package tg_bot_editor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Project-local .env values, excluded from Git; process variables take precedence. */
public final class ProjectEnvironment {
    private static volatile Map<String, String> values = Map.of();
    private static volatile Path directory;

    private ProjectEnvironment() { }

    public static String get(String name) {
        String process = System.getenv(name);
        return process != null ? process : values.get(name);
    }

    public static Path directory() { return directory; }

    public static void activate(Path projectDirectory) throws IOException {
        directory = projectDirectory.toAbsolutePath().normalize();
        values = parse(read(directory));
    }

    public static void useDraft(Path projectDirectory, String content) {
        directory = projectDirectory.toAbsolutePath().normalize();
        values = parse(content);
    }

    public static String read(Path projectDirectory) throws IOException {
        Path file = projectDirectory.resolve(".env");
        return Files.isRegularFile(file) ? Files.readString(file) : "";
    }

    public static Map<String, String> parse(String content) {
        var parsed = new LinkedHashMap<String, String>();
        int line = 0;
        for (String raw : content.split("\\R", -1)) {
            line++;
            String trimmed = raw.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            int equals = raw.indexOf('=');
            if (equals < 1) throw new IllegalArgumentException("Invalid .env line " + line + ": expected NAME=value");
            String key = raw.substring(0, equals).trim();
            if (!key.matches("[A-Za-z_][A-Za-z0-9_]*"))
                throw new IllegalArgumentException("Invalid variable name on .env line " + line);
            if (parsed.putIfAbsent(key, raw.substring(equals + 1).trim()) != null)
                throw new IllegalArgumentException("Duplicate variable " + key);
        }
        return Map.copyOf(parsed);
    }

    public static void write(Path projectDirectory, String content) throws IOException {
        parse(content);
        Files.createDirectories(projectDirectory);
        Path file = projectDirectory.resolve(".env");
        Path temporary = Files.createTempFile(projectDirectory, ".env-", ".tmp");
        try {
            Files.writeString(temporary, content);
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
        if (directory != null && directory.equals(projectDirectory.toAbsolutePath().normalize())) values = parse(content);
    }
}
