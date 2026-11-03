package tg_bot_editor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class DiagramJsonErrorTest {
    @Test void routesJsonFailureWithoutChangingNormalBranch(@TempDir Path directory) throws Exception {
        ProjectEnvironment.useDraft(directory, "");
        var project = new DiagramProject(); var diagram = new DiagramProject.Diagram(); diagram.id = "flow"; diagram.name = "Erro";
        diagram.nodes.add(node("start", "CALLBACK", Map.of("event", "COMMAND", "pattern", "/go")));
        diagram.nodes.add(node("db", "JSON_DB", Map.of("operation", "LIST", "dataPath", "", "path", ".env")));
        diagram.nodes.add(node("success", "RESPONSE", Map.of("text", "ok")));
        diagram.nodes.add(node("cleanup", "JAVA", Map.of("action", "return null;")));
        diagram.nodes.add(node("failed", "RESPONSE", Map.of("text", "{{output}}")));
        diagram.edges.add(edge("start", "db", "ALWAYS")); diagram.edges.add(edge("db", "success", "ALWAYS"));
        var failureEdge = edge("db", "cleanup", "ON_ERROR"); diagram.edges.add(failureEdge);
        diagram.edges.add(edge("cleanup", "failed", "ALWAYS")); project.diagrams.add(diagram);
        BotAction cleanup = new BotAction() {
            public Object run(BotContext context) { return null; }
            public Object run(BotContext context, Object input, int index) {
                assertInstanceOf(IllegalArgumentException.class, ((Map<?, ?>) input).get("error"));
                return "falha tratada";
            }
        };
        assertEquals("falha tratada", DiagramExecutor.run(project, diagram, new BotContext(1, 1, "/go", "x"),
                Map.of(ActionCompiler.diagramActionKey("flow", "cleanup"), cleanup)));
        diagram.nodes.get(1).settings = Map.of("operation", "LIST", "dataPath", "", "path", "data.json");
        assertEquals("ok", DiagramExecutor.run(project, diagram, new BotContext(1, 1, "/go", "x"), Map.of()));
        diagram.nodes.get(1).settings = Map.of("operation", "LIST", "dataPath", "", "path", ".env");
        diagram.edges.remove(failureEdge);
        assertThrows(IllegalArgumentException.class, () -> DiagramExecutor.run(project, diagram, new BotContext(1, 1, "/go", "x"), Map.of()));
    }
    private static DiagramProject.Node node(String id, String type, Map<String, Object> settings) {
        var node = new DiagramProject.Node(); node.id = id; node.name = id; node.type = type; node.settings = settings; return node;
    }
    private static DiagramProject.Edge edge(String from, String to, String kind) {
        var edge = new DiagramProject.Edge(); edge.id = from + to; edge.from = from; edge.to = to; edge.kind = kind; return edge;
    }
}
