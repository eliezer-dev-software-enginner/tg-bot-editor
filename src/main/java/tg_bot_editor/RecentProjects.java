package tg_bot_editor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Per-user project paths, kept outside portable bot projects. */
public final class RecentProjects {
    private final Path file;

    public RecentProjects() { this(settingsDirectory().resolve("recent-projects.properties")); }
    public RecentProjects(Path file) { this.file = file.toAbsolutePath().normalize(); }

    public List<Path> list() throws IOException {
        if (!Files.isRegularFile(file)) return List.of();
        var props = new Properties();
        try (var reader = Files.newBufferedReader(file)) { props.load(reader); }
        var paths = new ArrayList<Path>();
        for (int i = 0; i < 10; i++) {
            String value = props.getProperty("project." + i, "");
            if (!value.isBlank()) paths.add(Path.of(value).toAbsolutePath().normalize());
        }
        return List.copyOf(paths);
    }

    public void remember(Path projectFile) throws IOException {
        Path project = projectFile.toAbsolutePath().normalize();
        if (!Files.isRegularFile(project)) throw new IOException("Project file does not exist: " + project);
        var paths = new ArrayList<Path>();
        paths.add(project);
        for (Path previous : list()) if (!previous.equals(project) && Files.isRegularFile(previous)) paths.add(previous);
        var props = new Properties();
        for (int i = 0; i < Math.min(10, paths.size()); i++) props.setProperty("project." + i, paths.get(i).toString());
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), ".recent-", ".tmp");
        try {
            try (var writer = Files.newBufferedWriter(temporary)) { props.store(writer, "TG-bot Editor projects"); }
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }

    public static Path settingsDirectory() {
        String override = System.getProperty("tg.bot.editor.settingsDir", "");
        if (!override.isBlank()) return Path.of(override);
        String xdg = System.getenv("XDG_CONFIG_HOME");
        if (xdg != null && !xdg.isBlank() && Path.of(xdg).isAbsolute()) return Path.of(xdg, "tg-bot-editor");
        return Path.of(System.getProperty("user.home"), ".config", "tg-bot-editor");
    }
}
