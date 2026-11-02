package tg_bot_editor;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.StackPane;
import megalodonte.base.components.Component;
import megalodonte.base.state.State;
import megalodonte.base.theme.ThemeManager;
import megalodonte.components.Modal;
import megalodonte.theme.DefaultTheme;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DiagramLayoutTest {
    @TempDir Path directory;

    @Test void conditionActionsRemainReachableInsideSubflowDialog() throws Exception {
        assumeTrue(System.getenv("DISPLAY") != null);
        var started = new CountDownLatch(1);
        try { Platform.startup(started::countDown); } catch (IllegalStateException alreadyStarted) { started.countDown(); }
        assertTrue(started.await(10, TimeUnit.SECONDS));
        var check = new FutureTask<Void>(() -> {
            ThemeManager.setTheme(new DefaultTheme());
            java.nio.file.Files.createDirectories(directory.resolve("scripts"));
            FlowStore.write(directory.resolve("bot.json"), new FlowProject());
            var project = new DiagramProject(); var diagram = new DiagramProject.Diagram();
            diagram.id = "main"; diagram.name = "Subfluxo";
            var start = new DiagramProject.Node(); start.id = "start"; start.name = "Item"; start.type = "ITEM";
            var end = new DiagramProject.Node(); end.id = "end"; end.name = "Resposta"; end.type = "RESPONSE";
            end.settings = Map.of("text", "ok");
            diagram.nodes.add(start); diagram.nodes.add(end);
            var edge = new DiagramProject.Edge(); edge.id = "edge"; edge.from = "start"; edge.to = "end";
            diagram.edges.add(edge); project.diagrams.add(diagram); DiagramStore.write(directory, project);
            var editor = new DiagramViewModel(); editor.load(directory); editor.openDiagram("main"); editor.selectEdge("edge");
            editor.edgeKind.set("IF");
            var method = HomeScreen.class.getDeclaredMethod("diagramDialog", DiagramViewModel.class, boolean.class);
            method.setAccessible(true);
            var content = (Component) method.invoke(new HomeScreen(), editor, true);
            var root = new StackPane(new Modal(State.of(true), content).getJavaFxNode());
            new Scene(root, 1200, 700); root.resize(1200, 700); root.applyCss(); root.layout();
            var action = root.lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button -> "Aplicar condição".equals(button.getText())).findFirst().orElseThrow();
            var node = action.getParent();
            while (node != null && !(node instanceof ScrollPane)) node = node.getParent();
            assertInstanceOf(ScrollPane.class, node);
            var scroll = (ScrollPane) node; scroll.setVvalue(1); root.layout();
            var viewport = scroll.localToScene(scroll.getBoundsInLocal());
            var button = action.localToScene(action.getBoundsInLocal());
            assertTrue(viewport.getMaxY() <= 700, "Painel ultrapassou a janela: " + viewport);
            assertTrue(button.getMaxY() <= viewport.getMaxY() + 1, "Ação não alcançável ao rolar: " + button);
            return null;
        });
        Platform.runLater(check); check.get(30, TimeUnit.SECONDS);
    }
}
