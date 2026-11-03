package tg_bot_editor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

/** Commands exposed by the runner and by the project's COMMAND flows. */
public final class BotCommands {
    public record Entry(String name, String description) { }

    private BotCommands() { }

    /** Removes Telegram's @bot suffix from the command token, preserving arguments. */
    public static String normalize(String text) {
        if (text == null || !text.startsWith("/")) return text;
        return text.replaceFirst("^(/[A-Za-z0-9_]+)@[A-Za-z0-9_]+(?=\\s|$)", "$1");
    }

    public static List<Entry> list(FlowProject project) {
        return list(project, null);
    }

    /** Diagramas ativos têm prioridade, assim como no roteamento de comandos. */
    public static List<Entry> list(FlowProject project, DiagramProject diagrams) {
        var commands = new LinkedHashMap<String, Entry>();
        if (diagrams != null && diagrams.diagrams != null) for (var diagram : diagrams.diagrams) {
            if (diagram == null || !diagram.enabled || diagram.nodes == null) continue;
            for (var node : diagram.nodes) {
                if (node == null || !"CALLBACK".equals(node.type) || node.settings == null
                        || !"COMMAND".equalsIgnoreCase(String.valueOf(node.settings.get("event")))) continue;
                String name = String.valueOf(node.settings.getOrDefault("pattern", "")).strip();
                if (!name.matches("/[a-z0-9_]{1,32}")) continue;
                String description = diagram.name == null || diagram.name.isBlank() ? name.substring(1) : diagram.name;
                description = description.strip().replaceAll("\\R+", " ");
                if (description.length() > 256) description = description.substring(0, 256);
                commands.putIfAbsent(name, new Entry(name, description));
            }
        }
        for (var flow : project.flows) {
            if (flow == null || !"COMMAND".equalsIgnoreCase(flow.trigger) || flow.pattern == null) continue;
            String name = flow.pattern.strip();
            if (!name.matches("/[a-z0-9_]{1,32}")) continue;
            String description = flow.description == null || flow.description.isBlank() ? flow.title : flow.description;
            if (description == null || description.isBlank()) description = "Sem descrição";
            description = description.strip().replaceAll("\\R+", " ");
            if (description.length() > 256) description = description.substring(0, 256);
            commands.putIfAbsent(name, new Entry(name, description));
        }
        var result = new ArrayList<>(commands.values());
        result.sort(Comparator.comparing(Entry::name));
        return List.copyOf(result);
    }
}
