package tg_bot_editor;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class DiagramConverterTest {
    @Test void convertsJsonBase64AndTrimInRunningDiagrams() throws Exception {
        assertEquals(Map.of("name", "Maria"), run("JSON_PARSE", "{\"name\":\"Maria\"}"));
        assertEquals("{\"name\":\"Maria\"}", run("JSON_STRINGIFY", Map.of("name", "Maria")));
        assertEquals("T2zDoQ==", run("BASE64_ENCODE", "Olá"));
        assertEquals("Olá", run("BASE64_DECODE", "T2zDoQ=="));
        assertEquals("texto", run("TEXT_TRIM", "  texto  "));
        assertThrows(Exception.class, () -> run("JSON_PARSE", "{"));
        assertThrows(IllegalArgumentException.class, () -> run("BASE64_DECODE", "!"));
        assertThrows(IllegalArgumentException.class, () -> run("OTHER", "texto"));
    }

    private Object run(String operation, Object input) throws Exception {
        var graph = new DiagramProject(); var diagram = new DiagramProject.Diagram();
        diagram.id = "test"; diagram.name = "test"; graph.diagrams.add(diagram);
        var start = node("start", "CALLBACK", Map.of());
        var actionNode = node("java", "JAVA", Map.of("action", "return null;"));
        var converter = node("converter", "CONVERTER", Map.of("operation", operation, "value", "{{output}}"));
        diagram.nodes.addAll(java.util.List.of(start, actionNode, converter));
        diagram.edges.add(edge("one", "start", "java")); diagram.edges.add(edge("two", "java", "converter"));
        BotAction action = context -> input;
        return DiagramExecutor.run(graph, diagram, new BotContext(1, 1, "/test", "user"),
                Map.of(ActionCompiler.diagramActionKey("test", "java"), action));
    }

    private static DiagramProject.Node node(String id, String type, Map<String, Object> settings) {
        var node = new DiagramProject.Node(); node.id = id; node.name = id; node.type = type; node.settings = settings; return node;
    }
    private static DiagramProject.Edge edge(String id, String from, String to) {
        var edge = new DiagramProject.Edge(); edge.id = id; edge.from = from; edge.to = to; return edge;
    }
}
