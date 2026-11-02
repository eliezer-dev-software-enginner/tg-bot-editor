package tg_bot_editor;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Executes the first supported vertical slice: a command trigger and text response. */
public final class DiagramExecutor {
    private DiagramExecutor() { }

    public static Object run(DiagramProject.Diagram diagram) {
        try { return run(diagram, null, java.util.Map.of()); }
        catch (Exception error) { throw new IllegalArgumentException(error); }
    }

    public static Optional<DiagramProject.Diagram> find(DiagramProject project, String kind, String value) {
        if (project == null || value == null) return Optional.empty();
        String normalized = value.trim();
        return project.diagrams.stream().filter(diagram -> diagram.enabled && diagram.nodes.stream().anyMatch(node -> {
            if (!"CALLBACK".equals(node.type)) return false;
            String event = string(node, "event");
            String pattern = string(node, "pattern");
            if (!kind.equalsIgnoreCase(event) || pattern.isBlank()) return false;
            if ("COMMAND".equalsIgnoreCase(event))
                return normalized.equals(pattern) || normalized.startsWith(pattern + " ")
                        || normalized.startsWith(pattern + "@");
            return normalized.toLowerCase(Locale.ROOT).equals(pattern.toLowerCase(Locale.ROOT));
        })).findFirst();
    }

    public static Object run(DiagramProject.Diagram diagram, BotContext context,
                             java.util.Map<String, BotAction> actions) throws Exception {
        DiagramProject.Node current = diagram.nodes.stream()
                .filter(node -> "CALLBACK".equals(node.type)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Diagrama sem início Callback"));
        for (int steps = 0; steps <= diagram.nodes.size(); steps++) {
            if ("RESPONSE".equals(current.type)) return string(current, "text");
            if ("JAVA".equals(current.type)) {
                String flowId = string(current, "flowId");
                BotAction action = actions.get(flowId);
                if (action == null) throw new IllegalArgumentException("Ação ausente: " + flowId);
                return action.run(context);
            }
            String from = current.id;
            List<DiagramProject.Edge> outgoing = diagram.edges.stream()
                    .filter(edge -> from.equals(edge.from)).toList();
            if (outgoing.size() != 1 || !"ALWAYS".equals(outgoing.getFirst().kind))
                throw new IllegalArgumentException("O diagrama ainda usa conexões não executáveis: " + diagram.name);
            String target = outgoing.getFirst().to;
            current = diagram.nodes.stream().filter(node -> target.equals(node.id)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Componente de destino ausente"));
        }
        throw new IllegalArgumentException("Ciclo no diagrama: " + diagram.name);
    }

    private static String string(DiagramProject.Node node, String key) {
        Object value = node.settings.get(key);
        return value instanceof String text ? text : "";
    }
}
