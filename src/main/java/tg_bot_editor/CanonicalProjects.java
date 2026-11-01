package tg_bot_editor;

import java.nio.file.Files;
import java.nio.file.Path;

/** Resolves the old generated copy of the production bot to its Git checkout. */
public final class CanonicalProjects {
    private CanonicalProjects() { }

    public static Path botFile() {
        String configured = System.getProperty("tg.bot.editor.canonicalBot", "");
        if (!configured.isBlank()) return Path.of(configured).toAbsolutePath().normalize();
        return Path.of(System.getProperty("user.home"), "Desktop", "dev", "dev", "megalodonte-world",
                "applications", "meu-primeiro-projeto", "bot.json").toAbsolutePath().normalize();
    }

    public static Path resolve(Path requested) {
        Path path = requested.toAbsolutePath().normalize();
        Path oldCopy = ProjectWorkspace.defaultProjectsDirectory()
                .resolve(ProjectWorkspace.STARTER_NAME).resolve("bot.json").toAbsolutePath().normalize();
        Path canonical = botFile();
        return path.equals(oldCopy) && Files.isRegularFile(canonical) ? canonical : path;
    }
}
