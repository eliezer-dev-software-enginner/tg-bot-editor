package tg_bot_editor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Port of arsenal-soup's scheduled G1 feed, with per-item delivery acknowledgement. */
public final class G1NewsAction {
    private static final String DEFAULT_FEED = "https://recomendacao.globo.com/v3/globocom/rec/g1-trendings?registerImpression=false&responseFormat=legacyPublishing&perPage=20";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private G1NewsAction() { }

    public static List<BotReply> run() throws Exception {
        Path state = stateFile();
        seedLegacyHistory(state);
        String configured = System.getenv("TG_BOT_G1_FEED_URL");
        String url = configured == null || configured.isBlank() ? DEFAULT_FEED : configured;
        var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).GET().build();
        var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) throw new IllegalStateException("Feed G1 HTTP " + response.statusCode());
        return pending(JSON.readTree(response.body()), state);
    }

    static List<BotReply> pending(JsonNode feed, Path state) throws Exception {
        var seen = Files.exists(state) ? new HashSet<>(Files.readAllLines(state)) : new HashSet<String>();
        var pending = new ArrayList<BotReply>();
        for (JsonNode item : feed) {
            JsonNode content = item.path("content");
            String title = content.path("title").asText("").trim();
            String link = content.path("url").asText("");
            if (title.isBlank() || link.isBlank() || seen.contains(link)) continue;
            String summary = content.path("summary").asText("").trim();
            String image = content.path("image").path("sizes").path("L").path("url").asText("");
            String caption = title + "\n" + link;
            if (!summary.isBlank()) {
                int available = 1000 - caption.codePointCount(0, caption.length()) - 2;
                if (available > 0) caption = title + "\n" + truncate(summary, available) + "\n" + link;
            }
            BotReply reply = image.isBlank() ? BotReply.text(caption) : BotReply.photo(image, caption);
            pending.add(reply.afterSend(() -> acknowledge(state, link)));
            seen.add(link);
        }
        return pending;
    }

    private static synchronized void acknowledge(Path state, String link) {
        try {
            Files.createDirectories(state.getParent());
            Files.writeString(state, link + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception error) {
            throw new IllegalStateException("Não foi possível registrar a notícia entregue", error);
        }
    }

    private static Path stateFile() {
        String xdg = System.getenv("XDG_DATA_HOME");
        Path base = xdg == null || xdg.isBlank()
                ? Path.of(System.getProperty("user.home"), ".local", "share") : Path.of(xdg);
        return base.resolve("tg-bot-editor").resolve("g1-seen.txt");
    }

    private static String truncate(String value, int codePoints) {
        return value.codePointCount(0, value.length()) <= codePoints
                ? value : value.substring(0, value.offsetByCodePoints(0, codePoints - 1)) + "…";
    }

    private static void seedLegacyHistory(Path state) throws Exception {
        if (Files.exists(state)) return;
        Path legacy = Path.of(System.getProperty("user.home"), "arsenal-soup", "noticias.json");
        if (!Files.exists(legacy)) return;
        var links = new ArrayList<String>();
        for (JsonNode item : JSON.readTree(legacy.toFile())) {
            String link = item.path("link").asText("");
            if (!link.isBlank()) links.add(link);
        }
        Files.createDirectories(state.getParent());
        Files.write(state, links, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }
}
