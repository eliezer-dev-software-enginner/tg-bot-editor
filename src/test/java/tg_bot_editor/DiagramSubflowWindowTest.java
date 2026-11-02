package tg_bot_editor;

import javafx.application.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DiagramSubflowWindowTest {
    @TempDir Path directory;

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
}
