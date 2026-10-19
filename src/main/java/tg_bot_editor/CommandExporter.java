package tg_bot_editor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;

/** Human-readable list of commands available to users of one bot project. */
public final class CommandExporter {
    private CommandExporter() { }

    public static Path export(FlowProject project, Path projectDirectory) throws IOException {
        Path output = projectDirectory.resolve("comandos.txt");
        Files.writeString(output, format(project), StandardCharsets.UTF_8);
        return output;
    }

    public static String format(FlowProject project) {
        var commands = project.flows.stream()
                .filter(flow -> flow != null && "COMMAND".equalsIgnoreCase(flow.trigger)
                        && flow.pattern != null && !flow.pattern.isBlank())
                .sorted(Comparator.comparing(flow -> flow.pattern.toLowerCase(Locale.ROOT)))
                .toList();
        StringBuilder output = new StringBuilder("Comandos de ")
                .append(project.name == null || project.name.isBlank() ? "Meu bot" : project.name)
                .append("\n\n");
        if (commands.isEmpty()) return output.append("Nenhum comando disponível.\n").toString();
        for (var flow : commands) {
            String description = flow.description == null || flow.description.isBlank()
                    ? flow.title : flow.description;
            output.append(flow.pattern).append(" — ")
                    .append(description == null || description.isBlank() ? "Sem descrição" : description.strip().replaceAll("\\R+", " "))
                    .append('\n');
        }
        return output.toString();
    }
}
