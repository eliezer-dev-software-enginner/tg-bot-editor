package tg_bot_editor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class JavaSyntaxIdentifiersTest {
    @Test void discoversScriptsForTheCurrentProjectAndKeepsCommonIdentifiers(@TempDir Path root) throws Exception {
        Path first = Files.createDirectories(root.resolve("first/scripts/nested"));
        Files.writeString(first.resolve("ActionUm.java"), "class ActionUm {}");
        Files.writeString(first.resolve("bad-name.java"), "");
        Files.writeString(first.resolve("notes.txt"), "");
        Path second = Files.createDirectories(root.resolve("second/scripts"));
        Files.writeString(second.resolve("ActionDois.java"), "class ActionDois {}");

        var firstNames = JavaSyntaxIdentifiers.forProject(root.resolve("first"));
        assertTrue(firstNames.containsAll(java.util.List.of("ActionUm", "BotReply", "StringBuilder", "context")));
        assertFalse(firstNames.contains("bad-name"));
        assertFalse(firstNames.contains("ActionDois"));
        var secondNames = JavaSyntaxIdentifiers.forProject(root.resolve("second"));
        assertTrue(secondNames.contains("ActionDois"));
        assertFalse(secondNames.contains("ActionUm"));
        Files.writeString(second.resolve("NovaAction.java"), "class NovaAction {}");
        assertTrue(JavaSyntaxIdentifiers.forProject(root.resolve("second")).contains("NovaAction"));
    }
}
