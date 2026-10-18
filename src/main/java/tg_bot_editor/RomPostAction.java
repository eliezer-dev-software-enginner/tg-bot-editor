package tg_bot_editor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Scheduled ROM publication ported from post-roms-bot's JSON catalog. */
public final class RomPostAction {
    private static final ObjectMapper JSON = new ObjectMapper();

    private RomPostAction() { }

    public static BotReply run() throws Exception {
        String configured = System.getenv("TG_BOT_ROMS_DATA_DIR");
        if (configured == null || configured.isBlank())
            throw new IllegalStateException("Defina TG_BOT_ROMS_DATA_DIR para o catálogo post-roms-bot/jsons");
        return fromCatalog(Path.of(configured));
    }

    static BotReply fromCatalog(Path dataDirectory) throws Exception {
        Path directory = dataDirectory.toAbsolutePath().normalize();
        JsonNode roms = JSON.readTree(directory.resolve("roms.json").toFile());
        JsonNode consoles = JSON.readTree(directory.resolve("consoles.json").toFile());
        List<JsonNode> available = new ArrayList<>();
        for (JsonNode rom : roms) {
            String local = rom.path("capaLocalPath").asText("");
            Path localPath = local.isBlank() ? null : resolve(directory, local);
            // Telegram file IDs belong to the bot that uploaded them. The
            // unified arsenal-soup identity can reuse local covers only.
            if (localPath != null && Files.isRegularFile(localPath)) available.add(rom);
        }
        if (available.isEmpty()) throw new IllegalStateException("Nenhuma ROM com capa disponível");
        JsonNode rom = available.get(ThreadLocalRandom.current().nextInt(available.size()));
        String local = rom.path("capaLocalPath").asText("");
        Path localPath = local.isBlank() ? null : resolve(directory, local);
        String photo = localPath.toAbsolutePath().toString();
        String title = rom.path("nome").asText("");
        String description = rom.path("descricao").asText("");
        String caption = "<b>" + escape(title) + "</b>\n\n" + escape(description);
        if (caption.length() > 1024) throw new IllegalStateException("Legenda da ROM excede o limite de foto");
        var buttons = new ArrayList<BotButton>();
        String download = rom.path("linkMonetizado").asText("");
        if (!download.isBlank()) buttons.add(BotButton.link("Baixar agora GRÁTIS 🎮", download));
        String consoleName = "";
        for (JsonNode console : consoles) {
            if (console.path("id").asText().equals(rom.path("consoleId").asText())) {
                consoleName = console.path("nome").asText("").toLowerCase();
                break;
            }
        }
        if (consoleName.contains("snes") || consoleName.contains("super nintendo"))
            buttons.add(BotButton.link("Emulador recomendado", "https://play.google.com/store/apps/details?id=com.explusalpha.Snes9xPlus"));
        else if (consoleName.contains("gba"))
            buttons.add(BotButton.link("Emulador recomendado", "https://play.google.com/store/apps/details?id=com.johnemulators.johngbalite"));
        return BotReply.photo(photo, caption).withButtons(buttons).asHtml();
    }

    private static Path resolve(Path directory, String path) {
        Path candidate = Path.of(path);
        if (candidate.isAbsolute()) return candidate;
        Path alongsideData = directory.resolve(candidate).normalize();
        if (Files.isRegularFile(alongsideData)) return alongsideData;
        return directory.getParent().resolve(candidate).normalize();
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
