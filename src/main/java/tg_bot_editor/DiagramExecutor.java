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
                if (response.startsWith("{{") && response.endsWith("}}")) return value(response, input, output);
                return response.replace("{{output}}", output == null ? "" : String.valueOf(output));
            }
            if ("JSON_DB".equals(current.type)) {
                String jsonId = current.id;
                try { output = JsonDb.execute(current.settings, input, output); }
                catch (Exception error) {
                    var failure = diagram.edges.stream().filter(edge -> jsonId.equals(edge.from) && "ON_ERROR".equals(edge.kind)).findFirst();
                    if (failure.isEmpty()) throw error;
                    var details = new java.util.LinkedHashMap<String, Object>();
                    details.put("previous", output); details.put("error", error); output = details;
                    String target = failure.get().to;
                    current = diagram.nodes.stream().filter(node -> target.equals(node.id)).findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("Componente de erro ausente"));
                    continue;
                }
                if (diagram.edges.stream().noneMatch(edge -> jsonId.equals(edge.from) && !"ON_ERROR".equals(edge.kind))) return output;
            }
            if ("COMPOSITION".equals(current.type)) {
                output = DiagramComposition.compose(current.settings, input, output);
                String compositionId = current.id;
                if (diagram.edges.stream().noneMatch(edge -> compositionId.equals(edge.from))) return output;
            }
            if ("REST_HTTP".equals(current.type)) {
                output = RestHttp.execute(current.settings, input, output);
                String restId = current.id;
                if (diagram.edges.stream().noneMatch(edge -> restId.equals(edge.from))) return output;
            }
            if ("JAVA".equals(current.type)) {
                String key = string(current, "action").isBlank() ? string(current, "flowId")
                        : ActionCompiler.diagramActionKey(diagram.id, current.id);
                BotAction action = actions.get(key);
                if (action == null) throw new IllegalArgumentException("Ação ausente: " + key);
                output = action.run(context, output, index, input);
                if (output instanceof FlowStop stop) return stop.value();
                String javaId = current.id;
                if (diagram.edges.stream().noneMatch(edge -> javaId.equals(edge.from))) return output;
            }
            if ("SPLITTER".equals(current.type)) {
                String path = string(current, "array");
                if (!path.isBlank() && !(path.startsWith("{{") && path.endsWith("}}")))
                    throw new IllegalArgumentException("Informe uma referência de lista no Splitter: " + path);
                Object list = path.isBlank() ? output : value(path, input, output);
                if (!(list instanceof Iterable<?> items))
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
                    .filter(edge -> from.equals(edge.from) && !"ON_ERROR".equals(edge.kind)).toList();
            DiagramProject.Edge selected = selectEdge(outgoing, input, output, diagram.name);
            String target = selected.to;
            current = diagram.nodes.stream().filter(node -> target.equals(node.id)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Componente de destino ausente"));
        }
        throw new IllegalArgumentException("Ciclo no diagrama: " + diagram.name);
        } finally { active.remove(diagram.id); }
    }

    private static DiagramProject.Edge selectEdge(List<DiagramProject.Edge> outgoing, Object input, Object output, String diagramName) {
        if (outgoing.size() == 1 && "ALWAYS".equals(outgoing.getFirst().kind)) return outgoing.getFirst();
        if (outgoing.isEmpty() || outgoing.stream().anyMatch(edge -> "ALWAYS".equals(edge.kind)))
            throw new IllegalArgumentException("Conexões incompatíveis em " + diagramName);
        for (var edge : outgoing) {
            if ("IF".equals(edge.kind) && edge.groups.stream().allMatch(group -> matches(group, input, output))) return edge;
        }
        return outgoing.stream().filter(edge -> "ELSE".equals(edge.kind)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Nenhuma condição atendida em " + diagramName));
    }

    private static boolean matches(DiagramProject.ConditionGroup group, Object input, Object output) {
        var conditions = new java.util.ArrayList<Boolean>();
        for (var comparison : group.comparisons) {
            Object left = value(comparison.left, input, output);
            Object right = value(comparison.right, input, output);
            int order = compare(left, right);
            conditions.add(switch (comparison.operator) {
                case "EQ" -> order == 0; case "NE" -> order != 0;
                case "LT" -> order < 0; case "GT" -> order > 0;
                case "LE" -> order <= 0; case "GE" -> order >= 0;
                default -> throw new IllegalArgumentException("Operador de condição inválido: " + comparison.operator);
            });
        }
        for (var nested : group.groups) conditions.add(matches(nested, input, output));
        return "ALL".equals(group.operator) ? conditions.stream().allMatch(Boolean::booleanValue)
                : conditions.stream().anyMatch(Boolean::booleanValue);
    }

    static Object value(String expression, Object input, Object output) {
        if (expression == null) return null;
        String raw = expression.trim();
        if (raw.equals("\"\"") || raw.equals("''")) return "";
        if (!(raw.startsWith("{{") && raw.endsWith("}}"))) return expression;
        String path = raw.substring(2, raw.length() - 2).trim();
        String[] parts = path.split("\\.");
        Object current = switch (parts[0]) {
            case "input" -> input; case "output" -> output;
            default -> throw new IllegalArgumentException("Referência desconhecida: " + raw);
        };
        for (int i = 1; i < parts.length; i++) {
            if (current == null) return null;
            if (!(current instanceof java.util.Map<?, ?> map))
                throw new IllegalArgumentException("Caminho não é um objeto: " + raw);
            current = map.get(parts[i]);
        }
        return current;
    }

    private static int compare(Object left, Object right) {
        if (left == null || right == null) {
            if (left == right || left == null && "".equals(right) || right == null && "".equals(left)) return 0;
            return left == null ? -1 : 1;
        }
        String a = String.valueOf(left), b = String.valueOf(right);
        try { return new java.math.BigDecimal(a).compareTo(new java.math.BigDecimal(b)); }
        catch (NumberFormatException ignored) { return a.compareTo(b); }
    }

    private static String currentName(DiagramProject.Diagram diagram, String subflowId) {
        return diagram.name + " / " + subflowId;
    }

    private static String string(DiagramProject.Node node, String key) {
        Object value = node.settings.get(key);
        return value instanceof String text ? text : "";
    }
}
