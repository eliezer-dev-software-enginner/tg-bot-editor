package tg_bot_editor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Human-readable list of commands available to users of one bot project. */
public final class CommandExporter {
    private CommandExporter() { }

    public static Path export(FlowProject project, Path projectDirectory) throws IOException {
        Path output = projectDirectory.resolve("comandos.txt");
        Files.writeString(output, format(project), StandardCharsets.UTF_8);
        return output;
    }

    public static String format(FlowProject project) {
        var commands = BotCommands.list(project);
        StringBuilder output = new StringBuilder("Comandos de ")
                .append(project.name == null || project.name.isBlank() ? "Meu bot" : project.name)
                .append("\n\n");
        if (commands.isEmpty()) return output.append("Nenhum comando disponível.\n").toString();
        for (var command : commands) output.append(command.name()).append(" — ").append(command.description()).append('\n');
        return output.toString();
    }
}
