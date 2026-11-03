package tg_bot_editor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class VisualCommandExportTest {
    @Test void exportsEnabledVisualCommandsAndKeepsLegacyCommands(@TempDir Path directory) throws Exception {
        var project = new FlowProject(); project.name = "Bot";
        var legacy = new FlowProject.Flow(); legacy.id = "old"; legacy.trigger = "COMMAND";
        legacy.pattern = "/old"; legacy.title = "Antigo"; project.flows.add(legacy);
        var diagrams = new DiagramProject();
        diagrams.diagrams.add(command("Novo comando", "/new", true));
        diagrams.diagrams.add(command("Novo nome", "/old", true));
        diagrams.diagrams.add(command("Rascunho", "/draft", false));
        DiagramStore.write(directory, diagrams);
        var entries = BotCommands.list(project, diagrams);
        assertEquals(2, entries.size());
        assertTrue(entries.contains(new BotCommands.Entry("/old", "Novo nome")));
        assertTrue(entries.contains(new BotCommands.Entry("/new", "Novo comando")));
        String result = Files.readString(CommandExporter.export(project, directory));
        assertTrue(result.contains("/new — Novo comando"));
        assertTrue(result.contains("/old — Novo nome"));
        assertFalse(result.contains("/draft"));
    }

    private static DiagramProject.Diagram command(String title, String pattern, boolean enabled) {
        var diagram = new DiagramProject.Diagram(); diagram.id = pattern.substring(1); diagram.name = title; diagram.enabled = enabled;
        var node = new DiagramProject.Node(); node.id = "start"; node.name = "Comando"; node.type = "CALLBACK";
        node.settings = Map.of("event", "COMMAND", "pattern", pattern); diagram.nodes.add(node);
        return diagram;
    }
}
