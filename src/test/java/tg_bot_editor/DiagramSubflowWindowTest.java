package tg_bot_editor;

import javafx.application.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DiagramSubflowWindowTest {
    @TempDir Path directory;

    @Test void createsDraftAndActivatesConfiguredDiagram() throws Exception {
        assumeTrue(System.getenv("DISPLAY") != null, "Requer ambiente gráfico JavaFX");
        var started = new CountDownLatch(1);
        try { Platform.startup(started::countDown); } catch (IllegalStateException alreadyStarted) { started.countDown(); }
        assertTrue(started.await(10, TimeUnit.SECONDS));
        var check = new FutureTask<Void>(() -> {
            java.nio.file.Files.createDirectories(directory.resolve("scripts"));
            FlowStore.write(directory.resolve("bot.json"), new FlowProject());
            var editor = new DiagramViewModel(); editor.load(directory); editor.createDiagram();
            assertTrue(editor.open.get()); assertFalse(editor.enabled.get());
            editor.name.set("Meu comando"); editor.save();
            var draft = DiagramStore.read(directory).diagrams.getFirst();
            assertEquals("Meu comando", draft.name); assertFalse(draft.enabled);
            editor.enabled.set(true); editor.save();
            assertTrue(editor.status.get().contains("componente de início"));
            assertFalse(DiagramStore.read(directory).diagrams.getFirst().enabled);
            editor.addComponent("CALLBACK", 50, 100); editor.pattern.set("/novo-teste");
            editor.addComponent("RESPONSE", 300, 100); editor.response.set("Olá!");
            editor.enabled.set(false); editor.save();
            var restored = DiagramStore.read(directory).diagrams.getFirst();
            String start = restored.nodes.stream().filter(n -> "CALLBACK".equals(n.type)).findFirst().orElseThrow().id;
            String response = restored.nodes.stream().filter(n -> "RESPONSE".equals(n.type)).findFirst().orElseThrow().id;
            editor.connect(start, response); editor.enabled.set(true); editor.save();
            var project = DiagramStore.read(directory);
            var active = DiagramExecutor.find(project, "COMMAND", "/novo-teste").orElseThrow();
            assertEquals("Olá!", DiagramExecutor.run(active));
            editor.createDiagram(); editor.save();
            var both = DiagramStore.read(directory).diagrams;
            assertEquals(2, both.size()); assertNotEquals(both.getFirst().id, both.getLast().id);
            assertFalse(both.getLast().enabled);
            return null;
        });
        Platform.runLater(check); check.get(30, TimeUnit.SECONDS);
    }

    @Test void openingAndClosingSubflowPreservesParentAndPersistsEdits() throws Exception {
        assumeTrue(System.getenv("DISPLAY") != null, "Requer ambiente gráfico JavaFX");
        var started = new CountDownLatch(1);
        try { Platform.startup(started::countDown); }
        catch (IllegalStateException alreadyStarted) { started.countDown(); }
        assertTrue(started.await(10, TimeUnit.SECONDS));
        var check = new FutureTask<Void>(() -> {
            var project = new DiagramProject();
            var main = new DiagramProject.Diagram();
            main.id = "main"; main.name = "Principal";
            var splitter = new DiagramProject.Node();
            splitter.id = "split"; splitter.name = "Lista"; splitter.type = "SPLITTER";
            splitter.settings = Map.of("array", "{{output}}");
            var start = new DiagramProject.Node();
            start.id = "start"; start.name = "Comando"; start.type = "CALLBACK";
            start.settings = Map.of("event", "COMMAND", "pattern", "/test");
            main.nodes.add(start); main.nodes.add(splitter); project.diagrams.add(main);
            java.nio.file.Files.createDirectories(directory.resolve("scripts"));
            DiagramStore.write(directory, project);
            FlowStore.write(directory.resolve("bot.json"), new FlowProject());
            var editor = new DiagramViewModel();
            editor.load(directory); editor.openDiagram("main"); editor.selectNode("split");
            editor.openSubflow();
            assertTrue(editor.subflowOpen.get());
            assertEquals("Principal", editor.name.get());
            assertEquals("Lista", editor.componentName.get());
            var child = editor.subflowWindows.get().getFirst();
            child.name.set("Itens editados");
            child.close();
            assertFalse(editor.subflowOpen.get());
            assertEquals("Principal", editor.name.get());
            editor.openSubflow();
            child = editor.subflowWindows.get().getFirst();
            assertEquals("Itens editados", child.name.get());
            child.save();
            assertTrue(child.status.get().startsWith("Diagrama salvo"), child.status.get());
            assertEquals("Itens editados", DiagramStore.read(directory).diagrams.stream()
                    .filter(d -> !d.id.equals("main")).findFirst().orElseThrow().name);
            return null;
        });
        Platform.runLater(check);
        check.get(30, TimeUnit.SECONDS);
    }
    @Test void restPropertiesAndConnectionsPersist() throws Exception {
        assumeTrue(System.getenv("DISPLAY") != null, "Requer ambiente gráfico JavaFX");
        var started = new CountDownLatch(1);
        try { Platform.startup(started::countDown); }
        catch (IllegalStateException alreadyStarted) { started.countDown(); }
        assertTrue(started.await(10, TimeUnit.SECONDS));
        var check = new FutureTask<Void>(() -> {
            java.nio.file.Files.createDirectories(directory.resolve("scripts"));
            FlowStore.write(directory.resolve("bot.json"), new FlowProject());
            var project = new DiagramProject(); var diagram = new DiagramProject.Diagram();
            diagram.id = "main"; diagram.name = "Main"; diagram.enabled = true;
            var start = new DiagramProject.Node(); start.id = "start"; start.name = "Comando";
            start.type = "CALLBACK"; start.settings = Map.of("event", "COMMAND", "pattern", "/test");
            diagram.nodes.add(start); project.diagrams.add(diagram); DiagramStore.write(directory, project);
            var editor = new DiagramViewModel(); editor.load(directory); editor.openDiagram("main");
            editor.addComponent("REST_HTTP", 200, 100);
            editor.restUrl.set("https://example.com/api"); editor.restMethod.set("POST"); editor.restBody.set("{} ");
            editor.save();
            String restId = DiagramStore.read(directory).diagrams.getFirst().nodes.stream()
                    .filter(node -> node.type.equals("REST_HTTP")).findFirst().orElseThrow().id;
            editor.connect("start", restId); editor.save();
            assertTrue(editor.status.get().startsWith("Diagrama salvo"), editor.status.get());
            var restored = DiagramStore.read(directory).diagrams.getFirst();
            var rest = restored.nodes.stream().filter(node -> node.type.equals("REST_HTTP")).findFirst().orElseThrow();
            assertEquals("POST", rest.settings.get("method"));
            assertEquals("{} ", rest.settings.get("body"));
            assertEquals(rest.id, restored.edges.getFirst().to);
            editor.addComponent("JSON_DB", 420, 100);
            editor.jsonSection.set("records"); editor.jsonOperation.set("UPSERT");
            editor.jsonLayout.set("ARRAY");
            editor.jsonPath.set("${OUTRO_JSON}");
            editor.jsonKey.set("{{output.group}}"); editor.jsonRecord.set("{{output.item}}");
            editor.jsonIdentity.set("id"); editor.save();
            var json = DiagramStore.read(directory).diagrams.getFirst().nodes.stream()
                    .filter(node -> node.type.equals("JSON_DB")).findFirst().orElseThrow();
            assertEquals("records", json.settings.get("section"));
            assertEquals("ARRAY", json.settings.get("layout"));
            assertEquals("${OUTRO_JSON}", json.settings.get("path"));
            assertEquals("id", json.settings.get("identity"));
            return null;
        });
        Platform.runLater(check); check.get(30, TimeUnit.SECONDS);
    }

    @Test void configureConditionalConnectionAndAddAlternative() throws Exception {
        assumeTrue(System.getenv("DISPLAY") != null, "Requer ambiente gráfico JavaFX");
        var started = new CountDownLatch(1);
        try { Platform.startup(started::countDown); } catch (IllegalStateException alreadyStarted) { started.countDown(); }
        assertTrue(started.await(10, TimeUnit.SECONDS));
        var check = new FutureTask<Void>(() -> {
            java.nio.file.Files.createDirectories(directory.resolve("scripts"));
            FlowStore.write(directory.resolve("bot.json"), new FlowProject());
            var project = new DiagramProject(); var diagram = new DiagramProject.Diagram();
            diagram.id = "main"; diagram.name = "Condições"; diagram.enabled = true;
            var start = new DiagramProject.Node(); start.id = "start"; start.name = "Comando"; start.type = "CALLBACK";
            start.settings = Map.of("event", "COMMAND", "pattern", "/go");
            var yes = new DiagramProject.Node(); yes.id = "yes"; yes.name = "Sim"; yes.type = "RESPONSE"; yes.settings = Map.of("text", "sim");
            var no = new DiagramProject.Node(); no.id = "no"; no.name = "Não"; no.type = "RESPONSE"; no.settings = Map.of("text", "não");
            diagram.nodes.addAll(List.of(start, yes, no));
            var edge = new DiagramProject.Edge(); edge.id = "existing"; edge.from = "start"; edge.to = "yes";
            diagram.edges.add(edge); project.diagrams.add(diagram); DiagramStore.write(directory, project);
            var editor = new DiagramViewModel(); editor.load(directory); editor.openDiagram("main");
            editor.selectEdge("existing"); editor.edgeKind.set("IF"); editor.edgeLeft.set("{{output.image}}");
            editor.edgeOperator.set("NE"); editor.edgeRight.set("''"); editor.applyEdge();
            editor.connect("start", "no");
            editor.connect("yes", "no");
            assertTrue(editor.status.get().contains("válido"), editor.status.get());
            editor.connect("no", "start");
            assertTrue(editor.status.get().contains("válido"), editor.status.get());
            editor.connect("start", "yes");
            assertTrue(editor.status.get().contains("já está conectado"), editor.status.get());
            editor.save();
            assertTrue(editor.status.get().startsWith("Diagrama salvo"), editor.status.get());
            var edges = DiagramStore.read(directory).diagrams.getFirst().edges;
            assertEquals("IF", edges.getFirst().kind); assertEquals("{{output.image}}", edges.getFirst().groups.getFirst().comparisons.getFirst().left);
            assertEquals("ELSE", edges.getLast().kind);
            return null;
        });
        Platform.runLater(check); check.get(30, TimeUnit.SECONDS);
    }

    @Test void compositionItemsCanBeAddedRemovedAndPersisted() throws Exception {
        assumeTrue(System.getenv("DISPLAY") != null, "Requer ambiente gráfico JavaFX");
        var started = new CountDownLatch(1);
        try { Platform.startup(started::countDown); } catch (IllegalStateException alreadyStarted) { started.countDown(); }
        assertTrue(started.await(10, TimeUnit.SECONDS));
        var check = new FutureTask<Void>(() -> {
            java.nio.file.Files.createDirectories(directory.resolve("scripts"));
            FlowStore.write(directory.resolve("bot.json"), new FlowProject());
            var project = new DiagramProject(); var diagram = new DiagramProject.Diagram();
            diagram.id = "main"; diagram.name = "Composição"; diagram.enabled = true;
            var start = new DiagramProject.Node(); start.id = "start"; start.name = "Comando"; start.type = "CALLBACK";
            start.settings = Map.of("event", "COMMAND", "pattern", "/go");
            diagram.nodes.add(start); project.diagrams.add(diagram); DiagramStore.write(directory, project);
            var editor = new DiagramViewModel(); editor.load(directory); editor.openDiagram("main");
            editor.addComponent("COMPOSITION", 200, 100); editor.addCompositionItem(); editor.addCompositionItem();
            var first = editor.compositionItems.get().getFirst(); first.type.set("TEXT"); first.value.set("Olá {{input.name}}");
            var remove = editor.compositionItems.get().getLast(); editor.removeCompositionItem(remove); editor.save();
            assertTrue(editor.status.get().startsWith("Diagrama salvo"), editor.status.get());
            var restored = DiagramStore.read(directory).diagrams.getFirst();
            var composition = restored.nodes.stream().filter(node -> node.type.equals("COMPOSITION")).findFirst().orElseThrow();
            var items = (List<?>) composition.settings.get("items"); assertEquals(1, items.size());
            assertEquals("Olá {{input.name}}", ((Map<?, ?>) items.getFirst()).get("value"));
            return null;
        });
        Platform.runLater(check); check.get(30, TimeUnit.SECONDS);
    }

}
