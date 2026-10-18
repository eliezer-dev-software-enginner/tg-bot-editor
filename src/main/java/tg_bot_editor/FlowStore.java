package tg_bot_editor;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Atomic JSON persistence for editable flow projects. */
public final class FlowStore {
    private static final ObjectMapper JSON = new ObjectMapper();

    private FlowStore() { }

    public static FlowProject read(Path path) throws IOException {
        try (var input = Files.newInputStream(path)) { return read(input); }
    }

    public static FlowProject read(InputStream input) throws IOException {
        FlowProject project = JSON.readValue(input, FlowProject.class);
        if (project.version != 1 || project.flows == null) throw new IOException("Formato de projeto inválido");
        return project;
    }

    public static void write(Path path, FlowProject project) throws IOException {
        Path absolute = path.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        Path temporary = Files.createTempFile(absolute.getParent(), ".tg-bot-", ".tmp");
        try {
            JSON.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), project);
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
