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

    @Test void unconnectedPaletteNodePersistsWithoutChangingActiveResponse() throws Exception {
        var project = new DiagramProject();
        var diagram = diagram("start", "start");
        diagram.enabled = true;
        var trigger = node("trigger", "CALLBACK");
        trigger.settings = Map.of("event", "COMMAND", "pattern", "/start");
        var response = node("response", "RESPONSE");
        response.settings = Map.of("text", "Resposta existente");
        var pending = node("new-jsondb", "JSON_DB");
        pending.x = 235.5; pending.y = 182.0;
        diagram.nodes.add(trigger); diagram.nodes.add(response); diagram.nodes.add(pending);
        diagram.edges.add(edge("link", trigger.id, response.id, "ALWAYS"));
        project.diagrams.add(diagram);
        DiagramStore.write(directory, project);

        var restored = DiagramStore.read(directory);
        var saved = restored.diagrams.getFirst();
        assertTrue(saved.enabled);
        assertEquals(235.5, saved.nodes.get(2).x);
        assertEquals(182.0, saved.nodes.get(2).y);
        assertEquals("Resposta existente", DiagramExecutor.run(saved));
    }

    @Test void javaActionFlowsThroughResponseWithoutLosingNonTextReplies() throws Exception {
        Files.createDirectories(directory.resolve("scripts"));
        var project = new DiagramProject();
        var diagram = diagram("identity", "Identidade");
        diagram.enabled = true;
        var start = node("start", "CALLBACK");
        start.settings = Map.of("event", "COMMAND", "pattern", "/whoami");
        var javaNode = node("java", "JAVA");
        javaNode.settings = Map.of("action", "String input = \"um\"; int index = 2; return java.util.List.of(input, \"dois\");");
        var response = node("response", "RESPONSE");
        response.settings = Map.of("text", "{{output}}");
        diagram.nodes.add(start); diagram.nodes.add(javaNode); diagram.nodes.add(response);
        diagram.edges.add(edge("start-java", "start", "java", "ALWAYS"));
        diagram.edges.add(edge("java-response", "java", "response", "ALWAYS"));
        project.diagrams.add(diagram);
        DiagramStore.write(directory, project);
        var restored = DiagramStore.read(directory);
        var actions = ActionCompiler.compileProject(directory, new FlowProject(), restored);
        assertEquals(java.util.List.of("um", "dois"),
                DiagramExecutor.run(restored.diagrams.getFirst(), null, actions));
        restored.diagrams.getFirst().nodes.getLast().settings = Map.of("text", "Resultado: {{output}}");
        assertEquals("Resultado: [um, dois]", DiagramExecutor.run(restored.diagrams.getFirst(), null, actions));
    }

    @Test void splitterRunsSubflowForEachItemAndRejectsInvalidInput() throws Exception {
        Files.createDirectories(directory.resolve("scripts"));
        var project = new DiagramProject();
        var parent = diagram("main", "Principal"); parent.enabled = true;
        var start = node("start", "CALLBACK"); start.settings = Map.of("event", "COMMAND", "pattern", "/items");
        var javaNode = node("java", "JAVA"); javaNode.settings = Map.of("action", "return java.util.List.of(\"um\", \"dois\");");
        var splitter = node("split", "SPLITTER"); splitter.subflowId = "each"; splitter.settings = Map.of("array", "{{output}}");
        parent.nodes.addAll(java.util.List.of(start, javaNode, splitter));
        parent.edges.add(edge("a", "start", "java", "ALWAYS")); parent.edges.add(edge("b", "java", "split", "ALWAYS"));
        var child = diagram("each", "Cada item");
        var item = node("item", "ITEM");
        var formatter = node("formatter", "JAVA"); formatter.settings = Map.of("action", "return index + \": \" + input;");
        var reply = node("reply", "RESPONSE"); reply.settings = Map.of("text", "{{output}}");
        child.nodes.addAll(java.util.List.of(item, formatter, reply));
        child.edges.add(edge("c", "item", "formatter", "ALWAYS")); child.edges.add(edge("d", "formatter", "reply", "ALWAYS"));
        project.diagrams.add(parent); project.diagrams.add(child);
        DiagramStore.write(directory, project);
        var restored = DiagramStore.read(directory);
        var actions = ActionCompiler.compileProject(directory, new FlowProject(), restored);
        assertEquals(java.util.List.of("0: um", "1: dois"),
                DiagramExecutor.run(restored, restored.diagrams.getFirst(), null, actions));
        restored.diagrams.getFirst().nodes.getLast().settings = Map.of("array", "{{output}}", "aggregation", "concat");
        assertEquals("0: um1: dois", DiagramExecutor.run(restored, restored.diagrams.getFirst(), null, actions));
        var nestedActions = new java.util.HashMap<>(actions);
        nestedActions.put(ActionCompiler.diagramActionKey("main", "java"), context ->
                Map.of("result", Map.of("items", java.util.List.of("um", "dois"))));
        restored.diagrams.getFirst().nodes.getLast().settings = Map.of("array", "{{output.result.items}}");
        assertEquals(java.util.List.of("0: um", "1: dois"),
                DiagramExecutor.run(restored, restored.diagrams.getFirst(), null, nestedActions));
        restored.diagrams.getFirst().nodes.getLast().settings = Map.of("array", "{{output}}", "aggregation", "concat");
        var emptyActions = new java.util.HashMap<>(actions);
        emptyActions.put(ActionCompiler.diagramActionKey("main", "java"), context -> java.util.List.of());
        assertEquals("", DiagramExecutor.run(restored, restored.diagrams.getFirst(), null, emptyActions));
        actions = new java.util.HashMap<>(actions);
        actions.put(ActionCompiler.diagramActionKey("main", "java"), context -> "not a list");
        var finalActions = actions;
        assertThrows(IllegalArgumentException.class, () -> DiagramExecutor.run(restored, restored.diagrams.getFirst(), null, finalActions));
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
