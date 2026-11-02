package tg_bot_editor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DiagramStoreTest {
    @TempDir Path directory;

    @Test void diagramDraftPersistsWithoutChangingLegacyBot() throws Exception {
        var legacy = new FlowProject();
        legacy.flows.add(new FlowProject.Flow("start", "Início", "/start", 10, 20));
        FlowStore.write(directory.resolve("bot.json"), legacy);
        String original = Files.readString(directory.resolve("bot.json"));

        var project = new DiagramProject();
        var main = diagram("main", "Anúncios");
        var subflow = diagram("items", "Cada anúncio");
        main.nodes.add(node("start", "CALLBACK"));
        main.nodes.add(node("list", "JSON_DB"));
        var splitter = node("split", "SPLITTER");
        splitter.subflowId = "items";
        splitter.settings = Map.of("array", "list.output.ads");
        main.nodes.add(splitter);
        main.edges.add(edge("first", "start", "list", "ALWAYS"));
        main.edges.add(edge("second", "list", "split", "ALWAYS"));
        subflow.nodes.add(node("item", "ITEM"));
        subflow.nodes.add(node("reply", "RESPONSE"));
        subflow.edges.add(edge("reply-edge", "item", "reply", "ALWAYS"));
        project.diagrams.add(main);
        project.diagrams.add(subflow);

        DiagramStore.write(directory, project);
        var restored = DiagramStore.read(directory);
        assertEquals("items", restored.diagrams.getFirst().nodes.get(2).subflowId);
        assertEquals("list.output.ads", restored.diagrams.getFirst().nodes.get(2).settings.get("array"));
        assertEquals(original, Files.readString(directory.resolve("bot.json")));
        assertEquals("start", FlowStore.read(directory.resolve("bot.json")).flows.getFirst().id);
    }

    @Test void newlyCreatedProjectContainsBothFormats() throws Exception {
        Path botFile = ProjectWorkspace.create(directory, "novo-bot");
        assertTrue(Files.isRegularFile(botFile));
        assertTrue(Files.isRegularFile(botFile.getParent().resolve(DiagramStore.FILE_NAME)));
        assertTrue(DiagramStore.read(botFile.getParent()).diagrams.isEmpty());
        assertTrue(Files.isRegularFile(botFile.getParent()
                .resolve("runtime/src/main/java/tg_bot_editor/DiagramExecutor.java")));
    }

    @Test void conditionsAreAttachedToEdgesAndElseIsUnique() throws Exception {
        var project = new DiagramProject();
        var diagram = diagram("main", "Condições");
        diagram.nodes.add(node("start", "CALLBACK"));
        diagram.nodes.add(node("yes", "RESPONSE"));
        diagram.nodes.add(node("no", "RESPONSE"));
        var conditional = edge("if", "start", "yes", "IF");
        var group = new DiagramProject.ConditionGroup();
        var comparison = new DiagramProject.Comparison();
        comparison.left = "start.input.text";
        comparison.operator = "EQ";
        comparison.right = "sim";
        group.comparisons.add(comparison);
        conditional.groups.add(group);
        diagram.edges.add(conditional);
        diagram.edges.add(edge("else", "start", "no", "ELSE"));
        project.diagrams.add(diagram);

        DiagramStore.write(directory, project);
        assertEquals("EQ", DiagramStore.read(directory).diagrams.getFirst().edges.getFirst()
                .groups.getFirst().comparisons.getFirst().operator);
        diagram.edges.add(edge("another-else", "start", "yes", "ELSE"));
        assertThrows(IllegalArgumentException.class, () -> DiagramStore.write(directory, project));
        assertEquals(2, DiagramStore.read(directory).diagrams.getFirst().edges.size());
    }

    @Test void enabledStartDiagramHandlesCommandAndReturnsConfiguredText() throws Exception {
        var project = new DiagramProject();
        var diagram = diagram("start", "start");
        diagram.enabled = true;
        var trigger = node("start-trigger", "CALLBACK");
        trigger.settings = Map.of("event", "COMMAND", "pattern", "/start");
        var response = node("start-response", "RESPONSE");
        response.settings = Map.of("text", "Boas-vindas existentes");
        diagram.nodes.add(trigger);
        diagram.nodes.add(response);
        diagram.edges.add(edge("link", trigger.id, response.id, "ALWAYS"));
        project.diagrams.add(diagram);
        DiagramStore.write(directory, project);

        var restored = DiagramStore.read(directory);
        assertEquals("Boas-vindas existentes", DiagramExecutor.run(
                DiagramExecutor.find(restored, "COMMAND", "/start@meu_bot argumento").orElseThrow()));
        assertTrue(DiagramExecutor.find(restored, "COMMAND", "/status").isEmpty());
        diagram.enabled = false;
        assertTrue(DiagramExecutor.find(project, "COMMAND", "/start").isEmpty());
    }

    private static DiagramProject.Diagram diagram(String id, String name) {
        var value = new DiagramProject.Diagram(); value.id = id; value.name = name; return value;
    }
    private static DiagramProject.Node node(String id, String type) {
        var value = new DiagramProject.Node(); value.id = id; value.name = id; value.type = type; return value;
    }
    private static DiagramProject.Edge edge(String id, String from, String to, String kind) {
        var value = new DiagramProject.Edge(); value.id = id; value.from = from; value.to = to; value.kind = kind;
        return value;
    }
}
