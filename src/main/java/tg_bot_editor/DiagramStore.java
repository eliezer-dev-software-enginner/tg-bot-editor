package tg_bot_editor;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Atomic storage for diagram drafts; never rewrites the running bot.json. */
public final class DiagramStore {
    private static final ObjectMapper JSON = new ObjectMapper();
    public static final String FILE_NAME = "diagrams.json";

    private DiagramStore() { }

    public static DiagramProject read(Path projectDirectory) throws IOException {
        Path file = projectDirectory.resolve(FILE_NAME);
        if (!Files.exists(file)) return new DiagramProject();
        DiagramProject project = JSON.readValue(file.toFile(), DiagramProject.class);
        DiagramValidator.validate(project);
        return project;
    }

    public static void write(Path projectDirectory, DiagramProject project) throws IOException {
        DiagramValidator.validate(project);
        Files.createDirectories(projectDirectory);
        Path file = projectDirectory.resolve(FILE_NAME);
        Path temporary = Files.createTempFile(projectDirectory, ".diagrams-", ".tmp");
        try {
            JSON.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), project);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
