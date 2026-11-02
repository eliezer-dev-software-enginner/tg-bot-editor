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
        return project.diagrams.stream().filter(diagram -> diagram.enabled && matchRank(diagram, kind, value) >= 0)
                .max(java.util.Comparator.comparingInt(diagram -> matchRank(diagram, kind, value)));
    }

    static int matchRank(DiagramProject.Diagram diagram, String kind, String value) {
        return diagram.nodes.stream().filter(node -> "CALLBACK".equals(node.type))
                .filter(node -> kind.equalsIgnoreCase(string(node, "event")))
                .mapToInt(node -> patternRank(kind, string(node, "pattern"), value)).max().orElse(-1);
    }

    static int patternRank(String kind, String pattern, String value) {
        if (value == null || pattern == null || pattern.isBlank()) return -1;
        String normalized = value.trim();
        if ("COMMAND".equalsIgnoreCase(kind))
            return normalized.equals(pattern) || normalized.startsWith(pattern + " ")
                    || normalized.startsWith(pattern + "@") ? Integer.MAX_VALUE : -1;
        if (pattern.endsWith("*"))
            return normalized.regionMatches(true, 0, pattern, 0, pattern.length() - 1) ? pattern.length() - 1 : -1;
        return normalized.equalsIgnoreCase(pattern) ? Integer.MAX_VALUE : -1;
    }

    public static Object run(DiagramProject.Diagram diagram, BotContext context,
                             java.util.Map<String, BotAction> actions) throws Exception {
        var project = new DiagramProject();
        project.diagrams.add(diagram);
        return run(project, diagram, context, actions);
    }

    public static Object run(DiagramProject project, DiagramProject.Diagram diagram, BotContext context,
                             java.util.Map<String, BotAction> actions) throws Exception {
        return execute(project, diagram, context, actions, null, -1, false, new java.util.HashSet<>());
    }

    private static Object execute(DiagramProject project, DiagramProject.Diagram diagram, BotContext context,
                                  java.util.Map<String, BotAction> actions, Object input, int index, boolean subflow,
                                  java.util.Set<String> active) throws Exception {
        if (!active.add(diagram.id)) throw new IllegalArgumentException("Ciclo de subfluxos: " + diagram.name);
        try {
        DiagramProject.Node current = diagram.nodes.stream()
                .filter(node -> subflow ? "ITEM".equals(node.type) : ("CALLBACK".equals(node.type) || "TIMER".equals(node.type))).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Diagrama sem início " + (subflow ? "Item" : "Callback ou Timer")));
        Object output = input;
        for (int steps = 0; steps <= diagram.nodes.size(); steps++) {
            if ("RESPONSE".equals(current.type)) {
                String response = string(current, "text");
                if ("{{output}}".equals(response)) return output;
                return response.replace("{{output}}", output == null ? "" : String.valueOf(output));
            }
            if ("JAVA".equals(current.type)) {
                String key = string(current, "action").isBlank() ? string(current, "flowId")
                        : ActionCompiler.diagramActionKey(diagram.id, current.id);
                BotAction action = actions.get(key);
                if (action == null) throw new IllegalArgumentException("Ação ausente: " + key);
                output = action.run(context, output, index);
                String javaId = current.id;
                if (diagram.edges.stream().noneMatch(edge -> javaId.equals(edge.from))) return output;
            }
            if ("SPLITTER".equals(current.type)) {
                String path = string(current, "array");
                if (!path.isBlank() && !"{{output}}".equals(path))
                    throw new IllegalArgumentException("Caminho do Splitter ainda não suportado: " + path);
                if (!(output instanceof Iterable<?> items))
                    throw new IllegalArgumentException("O Splitter precisa receber uma lista: " + current.name);
                String subflowId = current.subflowId;
                var nested = project.diagrams.stream().filter(item -> item.id.equals(subflowId)).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Subfluxo do Splitter ausente: " + currentName(diagram, subflowId)));
                var results = new java.util.ArrayList<Object>();
                int itemIndex = 0;
                for (Object item : items) results.add(execute(project, nested, context, actions, item, itemIndex++, true, active));
                if ("concat".equals(string(current, "aggregation"))) {
                    var combined = new StringBuilder();
                    for (Object result : results) {
                        if (!(result instanceof String)) throw new IllegalArgumentException("Concatenação exige respostas de texto");
                        combined.append(result);
                    }
                    output = combined.toString();
                } else if ("flatten".equals(string(current, "aggregation"))) {
                    var flattened = new java.util.ArrayList<Object>();
                    for (Object result : results) {
                        if (result instanceof Iterable<?> batch) batch.forEach(flattened::add);
                        else flattened.add(result);
                    }
                    output = flattened;
                } else output = results;
                String splitterId = current.id;
                if (diagram.edges.stream().noneMatch(edge -> splitterId.equals(edge.from))) return output;
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
        } finally { active.remove(diagram.id); }
    }

    private static String currentName(DiagramProject.Diagram diagram, String subflowId) {
        return diagram.name + " / " + subflowId;
    }

    private static String string(DiagramProject.Node node, String key) {
        Object value = node.settings.get(key);
        return value instanceof String text ? text : "";
    }
}
