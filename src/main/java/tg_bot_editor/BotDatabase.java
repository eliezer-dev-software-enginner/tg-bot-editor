package tg_bot_editor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Supplier;

/** One atomic JSON document for the bot's persistent state. Legacy files are imported lazily. */
public final class BotDatabase {
    private static final ObjectMapper JSON = new ObjectMapper();

    private BotDatabase() { }

    public static Path file() {
        Path project = ProjectEnvironment.directory();
        if (project == null) throw new IllegalStateException("Abra o projeto antes de acessar o banco do bot.");
        String configured = ProjectEnvironment.get("TG_BOT_DATABASE_FILE");
        Path path = configured == null || configured.isBlank()
                ? project.resolve("data/bot-database.json") : Path.of(configured);
        if (!path.isAbsolute()) path = project.resolve(path);
        path = path.toAbsolutePath().normalize();
        if (!path.getFileName().toString().endsWith(".json"))
            throw new IllegalArgumentException("TG_BOT_DATABASE_FILE deve apontar para um arquivo .json.");
        return path;
    }

    public static synchronized <T> T read(String section, Path legacy, TypeReference<T> type,
                                          Supplier<T> empty) throws IOException {
        ObjectNode root = root();
        JsonNode value = root.get(section);
        if (value != null) return JSON.convertValue(value, type);
        if (legacy != null && Files.isRegularFile(legacy)) {
            T imported = JSON.readValue(legacy.toFile(), type);
            write(section, imported);
            return imported;
        }
        return empty.get();
    }

    public static synchronized boolean hasSection(String section) throws IOException {
        return root().has(section);
    }

    public static synchronized void initialize() throws IOException {
        if (!Files.exists(file())) write("schemaVersion", 1);
        else root();
    }

    public static synchronized <T> void write(String section, T value) throws IOException {
        ObjectNode root = root();
        root.set(section, JSON.valueToTree(value));
        Path file = file();
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), ".bot-database-", ".tmp");
        try {
            JSON.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), root);
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }

    private static ObjectNode root() throws IOException {
        Path file = file();
        if (!Files.isRegularFile(file)) return JSON.createObjectNode();
        JsonNode parsed = JSON.readTree(file.toFile());
        if (!(parsed instanceof ObjectNode object))
            throw new IOException("Banco do bot inválido: esperado objeto JSON em " + file);
        return object;
    }
}
