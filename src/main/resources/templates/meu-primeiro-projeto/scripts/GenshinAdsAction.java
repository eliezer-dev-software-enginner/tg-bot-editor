import com.fasterxml.jackson.databind.ObjectMapper;
import tg_bot_editor.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Project-owned port of the genshin-ads creation, listing and publication flows. */
public final class GenshinAdsAction {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<Long, Draft> DRAFTS = new ConcurrentHashMap<>();
    private GenshinAdsAction() { }

    public static Object run(BotContext context) throws Exception {
        String text = context.text() == null ? "" : context.text().trim();
        long user = context.userId();
        if (text.startsWith("ads:")) return callback(context, text);
        if (text.equals("/ads")) return menu();
        if (text.equals("/novo")) {
            DRAFTS.put(user, new Draft());
            return "📸 Envie a foto do anúncio.";
        }
        if (text.equals("/save")) return saveDraft(user);
        if (text.equals("/listar")) return list(user);
        if (text.equals("/config")) return config(user);
        if (text.equals("/topic")) return topic(context);
        if (text.startsWith("/addchat ")) return changeChat(user, text.substring(9).trim(), true);
        if (text.startsWith("/removechat ")) return changeChat(user, text.substring(12).trim(), false);
        if (text.equals("/publicar")) return publishNext(user, context.chatId(), context.threadId());
        Draft draft = DRAFTS.get(user);
        if (draft == null) return "Use /ads para ver os comandos de anúncios.";
        if (context.photoRef() != null) {
            if (draft.step != Step.PHOTO) return "A foto não é esperada agora. Use /novo para recomeçar.";
            draft.photo = context.photoRef();
            draft.step = Step.TITLE;
            return "✅ Foto recebida. Envie o título do anúncio.";
        }
        if (draft.step == Step.PHOTO) return "Envie uma foto, não texto.";
        if (draft.step == Step.TITLE) {
            if (text.isBlank()) return "O título não pode ficar vazio.";
            draft.title = text; draft.step = Step.INTERVAL;
            return "Envie o intervalo de publicação em minutos (0 para pausar).";
        }
        if (draft.step == Step.INTERVAL) {
            try {
                long interval = Long.parseLong(text);
                if (interval < 0) throw new NumberFormatException();
                draft.interval = interval; draft.step = Step.BUTTONS;
                return "Envie botões como Título | https://link, um por mensagem. Depois, /save.";
            } catch (NumberFormatException error) { return "Intervalo inválido. Use um número inteiro de minutos."; }
        }
        if (!text.contains("|")) return "Use Título | https://link ou /save.";
        String[] pieces = text.split("\\|", 2);
        String url = pieces[1].trim();
        if (pieces[0].isBlank() || !(url.startsWith("https://") || url.startsWith("http://")))
            return "Botão inválido. Informe título e URL http(s).";
        draft.buttons.add(BotButton.link(pieces[0].trim(), url));
        return "✅ Botão adicionado. Envie outro ou /save.";
    }

    public static synchronized Object tick() throws Exception {
        Data data = read();
        long now = Instant.now().toEpochMilli();
        var deliveries = new ArrayList<BotDelivery>();
        for (Ad ad : data.ads) {
            if (ad.interval < 1 || now - ad.lastSent < ad.interval * 60_000L) continue;
            addDeliveries(deliveries, ad, destinations(data, ad.owner), true);
        }
        return deliveries;
    }

    private static Object menu() {
        return "📣 Anúncios: /novo, /listar, /publicar, /config, /topic, "
                + "/addchat <grupo> [tópico], /removechat <grupo> [tópico].";
    }

    private static synchronized Object saveDraft(long user) throws Exception {
        Draft draft = DRAFTS.get(user);
        if (draft == null || draft.step != Step.BUTTONS) return "Nada para salvar. Use /novo.";
        Data data = read();
        Ad ad = draft.editingId == null ? new Ad() : find(data, draft.editingId, user);
        if (ad == null) return "Anúncio original não encontrado.";
        if (ad.id == null) ad.id = UUID.randomUUID().toString();
        ad.owner = user; ad.title = draft.title; ad.photo = draft.photo;
        ad.interval = draft.interval; ad.buttons = List.copyOf(draft.buttons);
        data.ads.removeIf(existing -> existing.id.equals(ad.id));
        data.ads.add(ad); write(data); DRAFTS.remove(user);
        return "✅ Anúncio salvo. Use /listar para vê-lo.";
    }

    private static synchronized Object list(long user) throws Exception {
        var replies = new ArrayList<BotReply>();
        for (Ad ad : read().ads) {
            if (ad.owner != user) continue;
            replies.add(BotReply.photo(ad.photo, ad.title + "\nIntervalo: " + ad.interval + " min").asHtml()
                    .withButtons(List.of(BotButton.callback("Editar", "ads:edit:" + ad.id),
                            BotButton.callback("Excluir", "ads:delete:" + ad.id),
                            BotButton.callback("Publicar agora", "ads:publish:" + ad.id))));
        }
        return replies.isEmpty() ? "Você não tem anúncios. Use /novo." : replies;
    }

    private static synchronized Object config(long user) throws Exception {
        return "Destinos: " + destinations(read(), user)
                + "\nUse /topic dentro do tópico para obter os IDs; depois /addchat <grupo> <tópico>.";
    }

    private static Object topic(BotContext context) {
        if (context.threadId() == null)
            return "Este chat não informou um tópico. ID do chat: " + context.chatId();
        return "ID do grupo: " + context.chatId() + "\nID do tópico: " + context.threadId()
                + "\nCadastre com /addchat " + context.chatId() + " " + context.threadId();
    }

    private static synchronized Object changeChat(long user, String value, boolean add) throws Exception {
        String[] parts = value.split("\\s+");
        if (parts.length < 1 || parts.length > 2)
            return "Use /addchat <grupo> [tópico] ou /removechat <grupo> [tópico].";
        long chat; Integer thread = null;
        try {
            chat = Long.parseLong(parts[0]);
            if (parts.length == 2) {
                thread = Integer.parseInt(parts[1]);
                if (thread <= 0) throw new NumberFormatException();
            }
        } catch (NumberFormatException error) {
            return "IDs inválidos. Informe o ID numérico do grupo e, opcionalmente, o ID positivo do tópico.";
        }
        Data data = read();
        var targets = new ArrayList<>(destinations(data, user));
        Destination destination = new Destination(chat, thread);
        if (add && !targets.contains(destination)) targets.add(destination);
        if (!add) {
            if (thread == null) targets.removeIf(target -> target.chatId() == chat);
            else targets.remove(destination);
        }
        data.destinations.put(Long.toString(user), targets);
        write(data);
        return add ? "✅ Destino adicionado: " + destination : "✅ Destino removido: " + destination;
    }

    private static synchronized Object publishNext(long user, long origin, Integer originThread) throws Exception {
        Data data = read();
        List<Destination> targets = destinations(data, user);
        if (targets.isEmpty()) return "Cadastre um destino com /addchat <grupo> [tópico].";
        Ad selected = data.ads.stream().filter(ad -> ad.owner == user).findFirst().orElse(null);
        if (selected == null) return "Nenhum anúncio salvo.";
        var deliveries = new ArrayList<BotDelivery>();
        addDeliveries(deliveries, selected, targets, true);
        deliveries.add(new BotDelivery(origin, originThread, BotReply.text("📤 Publicação enviada aos destinos.")));
        return deliveries;
    }

    private static synchronized Object callback(BotContext context, String text) throws Exception {
        String[] parts = text.split(":", 3);
        if (parts.length < 3) return menu();
        String action = parts[1], id = parts[2];
        Data data = read();
        Ad ad = find(data, id, context.userId());
        if (ad == null) return "Anúncio não encontrado.";
        if (action.equals("edit")) {
            Draft draft = new Draft(); draft.editingId = id; draft.photo = ad.photo;
            draft.title = ad.title; draft.interval = ad.interval;
            draft.buttons.addAll(ad.buttons); draft.step = Step.TITLE;
            DRAFTS.put(context.userId(), draft);
            return "✏️ Envie o novo título; em seguida, o intervalo e os botões. Finalize com /save.";
        }
        if (action.equals("delete")) return BotReply.text("Excluir " + ad.title + "?")
                .withButtons(List.of(BotButton.callback("Confirmar", "ads:confirm:" + id)));
        if (action.equals("confirm")) {
            data.ads.removeIf(item -> item.id.equals(id)); write(data);
            return "🗑️ Anúncio excluído.";
        }
        if (action.equals("publish")) {
            List<Destination> targets = destinations(data, context.userId());
            if (targets.isEmpty()) return "Cadastre um destino com /addchat <grupo> [tópico].";
            var deliveries = new ArrayList<BotDelivery>();
            addDeliveries(deliveries, ad, targets, true);
            deliveries.add(new BotDelivery(context.chatId(), context.threadId(), BotReply.text("📤 Anúncio publicado.")));
            return deliveries;
        }
        return menu();
    }

    private static List<Destination> destinations(Data data, long user) {
        String owner = Long.toString(user);
        if (data.destinations.containsKey(owner)) return data.destinations.get(owner);
        return data.chats.getOrDefault(owner, List.of()).stream().map(chat -> new Destination(chat, null)).toList();
    }

    private static void addDeliveries(List<BotDelivery> output, Ad ad, List<Destination> targets, boolean acknowledge) {
        for (int i = 0; i < targets.size(); i++) {
            BotReply reply = BotReply.photo(ad.photo, ad.title).asHtml().withButtons(ad.buttons);
            if (acknowledge && i == targets.size() - 1) reply = reply.afterSend(() -> markSent(ad.id));
            Destination target = targets.get(i);
            output.add(new BotDelivery(target.chatId(), target.threadId(), reply));
        }
    }

    private static synchronized void markSent(String id) {
        try {
            Data data = read();
            for (Ad ad : data.ads) if (ad.id.equals(id)) ad.lastSent = Instant.now().toEpochMilli();
            write(data);
        } catch (Exception error) { throw new IllegalStateException("Não foi possível registrar a publicação", error); }
    }

    private static Ad find(Data data, String id, long owner) {
        return data.ads.stream().filter(ad -> ad.id.equals(id) && ad.owner == owner).findFirst().orElse(null);
    }

    private static Path dataFile() {
        Path directory = ProjectEnvironment.directory();
        if (directory == null) throw new IllegalStateException("Abra um projeto antes de executar anúncios.");
        String configured = ProjectEnvironment.get("TG_BOT_ADS_DATA_DIR");
        if (configured == null || configured.isBlank()) return directory.resolve("data/genshin-ads.json");
        Path dataDirectory = Path.of(configured).normalize();
        if (!dataDirectory.isAbsolute())
            throw new IllegalArgumentException("TG_BOT_ADS_DATA_DIR deve ser um caminho absoluto.");
        return dataDirectory.resolve("genshin-ads.json");
    }
    private static Data read() throws Exception {
        Path file = dataFile();
        if (Files.isRegularFile(file)) return JSON.readValue(file.toFile(), Data.class);
        Path directory = file.getParent();
        Path legacyAds = directory.resolve("ads.json");
        Path legacyConfigs = directory.resolve("configs.json");
        if (!Files.isRegularFile(legacyAds) && !Files.isRegularFile(legacyConfigs)) return new Data();
        Data imported = importLegacy(directory, legacyAds, legacyConfigs);
        write(imported);
        return imported;
    }
    private static Data importLegacy(Path directory, Path adsFile, Path configsFile) throws Exception {
        Data imported = new Data();
        if (Files.isRegularFile(adsFile)) {
            for (var item : JSON.readTree(adsFile.toFile())) {
                Ad ad = new Ad();
                ad.id = item.path("id").asText();
                ad.owner = item.path("ownerUserId").asLong();
                ad.title = item.path("title").asText();
                String image = item.path("image").asText("");
                ad.photo = image.isBlank() ? "" : directory.resolve("images").resolve(image).normalize().toString();
                ad.interval = item.path("interval").asLong();
                // Start a fresh interval after import instead of publishing every old ad at once.
                ad.lastSent = Instant.now().toEpochMilli();
                for (var button : item.path("buttons"))
                    ad.buttons.add(BotButton.link(button.path("title").asText(), button.path("url").asText()));
                imported.ads.add(ad);
            }
        }
        if (Files.isRegularFile(configsFile)) {
            for (var config : JSON.readTree(configsFile.toFile())) {
                var chats = new ArrayList<Long>();
                for (var chat : config.path("targetChatIds")) chats.add(chat.asLong());
                imported.chats.put(Long.toString(config.path("userId").asLong()), chats);
            }
        }
        return imported;
    }
    private static void write(Data data) throws Exception {
        Path file = dataFile(); Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), ".ads-", ".tmp");
        try {
            JSON.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), data);
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temp); }
    }
    private enum Step { PHOTO, TITLE, INTERVAL, BUTTONS }
    private static final class Draft {
        Step step = Step.PHOTO;
        String editingId, photo, title;
        long interval;
        List<BotButton> buttons = new ArrayList<>();
    }
    public static final class Data {
        public List<Ad> ads = new ArrayList<>();
        public Map<String, List<Long>> chats = new java.util.HashMap<>();
        public Map<String, List<Destination>> destinations = new java.util.HashMap<>();
    }
    public record Destination(long chatId, Integer threadId) {
        @Override public String toString() {
            return chatId + (threadId == null ? "" : " (tópico " + threadId + ")");
        }
    }
    public static final class Ad {
        public String id, title, photo;
        public long owner, interval, lastSent;
        public List<BotButton> buttons = new ArrayList<>();
    }
}
