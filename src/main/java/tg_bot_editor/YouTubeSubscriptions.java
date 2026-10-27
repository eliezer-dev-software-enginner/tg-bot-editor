package tg_bot_editor;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

/** Public YouTube feed subscriptions; each destination acknowledges successful deliveries. */
final class YouTubeSubscriptions {
    static final String ACTION_ID = "youtube-new-videos";
    private static final Pattern CHANNEL = Pattern.compile("UC[A-Za-z0-9_-]{22}");
    private static final Pattern EXTERNAL_ID = Pattern.compile("(?:\\\"externalId\\\"\\s*:\\s*\\\"|itemprop=\\\"channelId\\\"\\s+content=\\\")(?<id>UC[A-Za-z0-9_-]{22})");
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    interface Fetcher { String get(String url) throws Exception; }
    private final Fetcher fetcher;
    private final Path file;
    private final List<Channel> channels = new ArrayList<>();

    public static final class Channel {
        public String id;
        public String input;
        public String title;
        public Map<String, String> lastSeen = new HashMap<>();
        public Channel() { }
        Channel(String id, String input, String title) {
            this.id = id; this.input = input; this.title = title;
        }
    }
    record Video(String id, String title, String url) { }
    interface Sender { void send(ActionBindings.Destination destination, String text) throws Exception; }

    YouTubeSubscriptions(Path projectDirectory) throws IOException { this(projectDirectory, null); }

    YouTubeSubscriptions(Path projectDirectory, Fetcher fetcher) throws IOException {
        this.fetcher = fetcher == null ? this::fetchHttp : fetcher;
        file = projectDirectory.resolve("data/youtube-subscriptions.json");
        if (Files.isRegularFile(file)) {
            for (Channel channel : JSON.readValue(file.toFile(), Channel[].class)) {
                if (channel.id == null || !CHANNEL.matcher(channel.id).matches())
                    throw new IOException("Inscrição YouTube inválida em " + file);
                if (channel.lastSeen == null) channel.lastSeen = new HashMap<>();
                channels.add(channel);
            }
        }
    }

    synchronized String list() {
        if (channels.isEmpty()) return "Nenhum canal inscrito. Use /youtube_add <@handle ou URL>.";
        StringBuilder result = new StringBuilder("Canais inscritos:\n");
        for (Channel channel : channels)
            result.append(channel.title).append(" — ").append(channel.id).append("\n");
        return result.toString();
    }

    synchronized String add(String input) throws Exception {
        String id = resolve(input);
        if (channels.stream().anyMatch(channel -> channel.id.equals(id))) return "Canal já inscrito: " + id;
        List<Video> feed = readFeed(fetcher.get(feedUrl(id)));
        String title = input.trim();
        Channel channel = new Channel(id, title, title);
        channels.add(channel);
        save();
        return "Canal inscrito: " + title + " (" + id + "). "
                + (feed.isEmpty() ? "Aguardando novos vídeos." : "Os vídeos atuais serão usados como ponto inicial.");
    }

    synchronized String remove(String input) throws Exception {
        String value = input.trim();
        String id = channels.stream().filter(channel -> channel.id.equals(value) || channel.input.equalsIgnoreCase(value))
                .map(channel -> channel.id).findFirst().orElse(null);
        if (id == null) id = resolve(value);
        String target = id;
        if (!channels.removeIf(channel -> channel.id.equals(target))) return "Canal não estava inscrito: " + id;
        save();
        return "Inscrição removida: " + id;
    }

    synchronized int check(List<ActionBindings.Destination> destinations, Sender sender) throws Exception {
        if (destinations.isEmpty()) return 0;
        int sent = 0;
        for (Channel channel : channels) {
            List<Video> feed = readFeed(fetcher.get(feedUrl(channel.id)));
            if (feed.isEmpty()) continue;
            for (ActionBindings.Destination destination : destinations) {
                String key = destination.chatId() + ":" + (destination.threadId() == null ? 0 : destination.threadId());
                String last = channel.lastSeen.get(key);
                if (last == null) {
                    channel.lastSeen.put(key, feed.getFirst().id());
                    save();
                    continue;
                }
                int index = 0;
                while (index < feed.size() && !feed.get(index).id().equals(last)) index++;
                for (int i = Math.min(index, 5) - 1; i >= 0; i--) {
                    Video video = feed.get(i);
                    sender.send(destination, "📺 Novo vídeo de " + channel.title + ": " + video.title() + "\n" + video.url());
                    channel.lastSeen.put(key, video.id());
                    save();
                    sent++;
                }
            }
        }
        return sent;
    }

    static String feedUrl(String id) { return "https://www.youtube.com/feeds/videos.xml?channel_id=" + id; }

    static String channelIdFromPage(String html) throws IOException {
        var match = EXTERNAL_ID.matcher(html);
        if (match.find()) return match.group("id");
        throw new IOException("Não foi possível identificar o canal na página pública. Use a URL /channel/UC... ou configure YOUTUBE_API_KEY.");
    }

    private String resolve(String raw) throws Exception {
        String input = raw == null ? "" : raw.trim();
        if (CHANNEL.matcher(input).matches()) return input;
        String path;
        if (input.startsWith("@")) path = "/" + input;
        else if (input.startsWith("https://") || input.startsWith("http://")) {
            URI uri = URI.create(input);
            if (uri.getHost() == null || !java.util.Set.of("youtube.com", "www.youtube.com", "m.youtube.com")
                    .contains(uri.getHost().toLowerCase(java.util.Locale.ROOT)))
                throw new IllegalArgumentException("Use uma URL do youtube.com.");
            path = uri.getPath();
        } else if (input.matches("[A-Za-z0-9._-]{2,100}")) path = "/@" + input;
        else throw new IllegalArgumentException("Use @handle, username ou URL de canal do YouTube.");
        var direct = Pattern.compile("^/channel/(UC[A-Za-z0-9_-]{22})(?:/.*)?$").matcher(path);
        if (direct.matches()) return direct.group(1);
        if (!path.matches("/(?:@[A-Za-z0-9._-]+|user/[A-Za-z0-9._-]+|c/[A-Za-z0-9._-]+)(?:/.*)?"))
            throw new IllegalArgumentException("A URL precisa apontar para um canal do YouTube.");
        String apiKey = ProjectEnvironment.get("YOUTUBE_API_KEY");
        if (apiKey != null && !apiKey.isBlank() && (path.startsWith("/@") || path.startsWith("/user/"))) {
            String handle = path.substring(path.lastIndexOf('/') + 1);
            String criterion = path.startsWith("/@") ? "forHandle" : "forUsername";
            String url = "https://www.googleapis.com/youtube/v3/channels?part=id&" + criterion + "="
                    + URLEncoder.encode(handle, StandardCharsets.UTF_8) + "&key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
            try {
                var tree = JSON.readTree(fetcher.get(url));
                String id = tree.path("items").path(0).path("id").asText("");
                if (CHANNEL.matcher(id).matches()) return id;
            } catch (Exception error) {
                throw new IOException("Falha ao consultar canal pela YouTube Data API.", error);
            }
        }
        return channelIdFromPage(fetcher.get("https://www.youtube.com" + path));
    }

    private String fetchHttp(String url) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("User-Agent", "Mozilla/5.0 TG-bot-Editor/1.0").GET().build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) throw new IOException("YouTube respondeu HTTP " + response.statusCode());
        return response.body();
    }

    static List<Video> readFeed(String xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        var document = factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        var entries = document.getElementsByTagNameNS("http://www.w3.org/2005/Atom", "entry");
        var videos = new ArrayList<Video>();
        for (int i = 0; i < entries.getLength(); i++) {
            var entry = (Element) entries.item(i);
            String id = text(entry, "http://www.youtube.com/xml/schemas/2015", "videoId");
            if (!id.matches("[A-Za-z0-9_-]{11}")) continue;
            videos.add(new Video(id, text(entry, "http://www.w3.org/2005/Atom", "title"),
                    "https://www.youtube.com/watch?v=" + id));
        }
        return videos;
    }

    private static String text(Element parent, String namespace, String name) {
        var nodes = parent.getElementsByTagNameNS(namespace, name);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().trim();
    }

    private void save() throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "youtube-subscriptions-", ".tmp");
        try {
            JSON.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), channels);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
}
