package tg_bot_editor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Structural checks for saved drafts; execution-specific checks come before publication. */
public final class DiagramValidator {
    private static final Set<String> TYPES = Set.of("TIMER", "CALLBACK", "ITEM", "CONVERTER", "JAVA",
            "GENERATOR", "RESPONSE", "JDBC", "JSON_DB", "SPLITTER", "REST_HTTP", "COMPOSITION");
    private static final Set<String> COMPARISONS = Set.of("EQ", "NE", "LT", "GT", "LE", "GE");

    private DiagramValidator() { }

    public static void validate(DiagramProject project) {
        if (project == null || project.version != 1 || project.diagrams == null)
            throw new IllegalArgumentException("Formato de diagramas inválido");
        Set<String> diagramIds = new HashSet<>();
        for (var diagram : project.diagrams) {
            if (diagram == null || blank(diagram.id) || !diagramIds.add(diagram.id)
                    || blank(diagram.name) || diagram.nodes == null || diagram.edges == null)
                throw new IllegalArgumentException("Diagrama incompleto ou com ID duplicado");
        }
        for (var diagram : project.diagrams) validateDiagram(diagram, diagramIds);
    }

    private static void validateDiagram(DiagramProject.Diagram diagram, Set<String> diagramIds) {
        Map<String, DiagramProject.Node> nodes = new HashMap<>();
        int starts = 0;
        for (var node : diagram.nodes) {
            if (node == null || blank(node.id) || nodes.putIfAbsent(node.id, node) != null
                    || blank(node.name) || !TYPES.contains(node.type) || node.settings == null
                    || !Double.isFinite(node.x) || !Double.isFinite(node.y))
                throw new IllegalArgumentException("Componente inválido em " + diagram.name);
            if (Set.of("TIMER", "CALLBACK", "ITEM").contains(node.type)) starts++;
            if (node.subflowId != null && (!node.type.equals("SPLITTER") || !diagramIds.contains(node.subflowId)
                    || node.subflowId.equals(diagram.id)))
                throw new IllegalArgumentException("Subfluxo inválido em " + node.name);
        }
        if (!nodes.isEmpty() && starts != 1)
            throw new IllegalArgumentException("Diagrama deve ter um único componente de início: " + diagram.name);
        Set<String> edgeIds = new HashSet<>();
        Map<String, Integer> elseCount = new HashMap<>();
        for (var edge : diagram.edges) {
            if (edge == null || blank(edge.id) || !edgeIds.add(edge.id)
                    || !nodes.containsKey(edge.from) || !nodes.containsKey(edge.to)
                    || edge.from.equals(edge.to) || !Set.of("ALWAYS", "IF", "ELSE").contains(edge.kind)
                    || edge.groups == null)
                throw new IllegalArgumentException("Conexão inválida em " + diagram.name);
            if (edge.kind.equals("ELSE")) {
                if (!edge.groups.isEmpty() || elseCount.merge(edge.from, 1, Integer::sum) > 1)
                    throw new IllegalArgumentException("Ramo alternativo inválido em " + diagram.name);
            } else if (edge.kind.equals("IF")) {
                if (edge.groups.isEmpty()) throw new IllegalArgumentException("Condição sem comparação");
                edge.groups.forEach(DiagramValidator::validateGroup);
            } else if (!edge.groups.isEmpty()) {
                throw new IllegalArgumentException("Conexão incondicional não recebe comparações");
            }
        }
    }

    private static void validateGroup(DiagramProject.ConditionGroup group) {
        if (group == null || !Set.of("ALL", "ANY").contains(group.operator)
                || group.comparisons == null || group.groups == null
                || (group.comparisons.isEmpty() && group.groups.isEmpty()))
            throw new IllegalArgumentException("Grupo de condições vazio ou inválido");
        for (var comparison : group.comparisons) {
            if (comparison == null || blank(comparison.left) || blank(comparison.right)
                    || !COMPARISONS.contains(comparison.operator))
                throw new IllegalArgumentException("Comparação inválida");
        }
        group.groups.forEach(DiagramValidator::validateGroup);
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
