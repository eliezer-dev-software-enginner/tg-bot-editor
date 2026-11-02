package tg_bot_editor;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class DiagramConditionsTest {
    @Test void routesMatchingAndAlternativeBranchesUsingPreviousOutput() throws Exception {
        var project = new DiagramProject(); var diagram = new DiagramProject.Diagram();
        diagram.id = "condition"; diagram.name = "Condições"; diagram.enabled = true;
        diagram.nodes.add(node("start", "CALLBACK", Map.of("pattern", "/go", "event", "COMMAND")));
        diagram.nodes.add(node("data", "JAVA", Map.of("action", "return null;")));
        diagram.nodes.add(node("image", "RESPONSE", Map.of("text", "imagem")));
        diagram.nodes.add(node("text", "RESPONSE", Map.of("text", "texto")));
        diagram.edges.add(edge("start-data", "start", "data", "ALWAYS"));
        var conditional = edge("if", "data", "image", "IF");
        var group = new DiagramProject.ConditionGroup(); var comparison = new DiagramProject.Comparison();
        comparison.left = "{{output.image}}"; comparison.operator = "NE"; comparison.right = "\"\"";
        group.comparisons.add(comparison); conditional.groups.add(group); diagram.edges.add(conditional);
        diagram.edges.add(edge("else", "data", "text", "ELSE")); project.diagrams.add(diagram);
        DiagramValidator.validate(project);
        String key = ActionCompiler.diagramActionKey(diagram.id, "data");
        assertEquals("imagem", DiagramExecutor.run(project, diagram, null, Map.of(key, context -> Map.of("image", "https://exemplo.com/a.png"))));
        assertEquals("texto", DiagramExecutor.run(project, diagram, null, Map.of(key, context -> Map.of("image", ""))));
        assertEquals("texto", DiagramExecutor.run(project, diagram, null, Map.of(key, context -> Map.of())));
    }
    private static DiagramProject.Node node(String id, String type, Map<String, Object> settings) {
        var node = new DiagramProject.Node(); node.id = id; node.name = id; node.type = type; node.settings = settings; return node;
    }
    private static DiagramProject.Edge edge(String id, String from, String to, String kind) {
        var edge = new DiagramProject.Edge(); edge.id = id; edge.from = from; edge.to = to; edge.kind = kind; return edge;
    }
}
