package tg_bot_editor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class JsonDbPathTest {
    @Test void arrayCollectionImportsLegacyAndUpdatesByIdentity(@TempDir Path project) throws Exception {
        ProjectEnvironment.useDraft(project, "");
        Files.createDirectories(project.resolve("data"));
        Files.writeString(project.resolve("data/legacy.json"),
                "[{\"id\":\"a\",\"lastSeen\":{\"-100:7\":\"video\"}},{\"id\":\"b\"}]");
        var settings = new java.util.HashMap<String, Object>(Map.of("operation", "LIST", "layout", "ARRAY",
                "section", "channels", "legacy", "data/legacy.json", "record", "{{output}}", "identity", "id"));
        var initial = (java.util.List<?>) JsonDb.execute(settings, null, null);
        assertEquals(Map.of("-100:7", "video"), ((Map<?, ?>) initial.getFirst()).get("lastSeen"));
        settings.put("operation", "UPSERT");
        JsonDb.execute(settings, null, Map.of("id", "c", "title", "Canal"));
        settings.put("operation", "DELETE");
        JsonDb.execute(settings, null, Map.of("id", "b"));
        settings.put("operation", "LIST");
        var result = (java.util.List<?>) JsonDb.execute(settings, null, null);
        assertEquals(2, result.size());
        assertEquals(Map.of("-100:7", "video"), ((Map<?, ?>) result.getFirst()).get("lastSeen"));
        assertEquals("c", ((Map<?, ?>) result.getLast()).get("id"));
    }

    @Test void resolvesEnvironmentAndExplicitPathsWithoutWritingToAnotherFile(@TempDir Path project) throws Exception {
        ProjectEnvironment.useDraft(project, "TG_BOT_DATABASE_FILE=data/configurado.json\nOUTRO_JSON=data/outro.json");
        var input = Map.of("group", "x", "record", Map.of("id", 7), "message", "ok");
        var settings = new java.util.HashMap<String, Object>(Map.of("operation", "UPSERT", "section", "items",
                "key", "{{output.group}}", "record", "{{output.record}}", "identity", "id",
                "result", "{{output.message}}", "path", "${TG_BOT_DATABASE_FILE}"));
        assertEquals("ok", JsonDb.execute(settings, null, input));
        assertTrue(Files.exists(project.resolve("data/configurado.json")));
        assertFalse(Files.exists(project.resolve("data/bot-database.json")));
        settings.put("path", "${OUTRO_JSON}");
        JsonDb.execute(settings, null, input);
        assertTrue(Files.exists(project.resolve("data/outro.json")));
        settings.put("path", "data/direto.json");
        JsonDb.execute(settings, null, input);
        assertTrue(Files.exists(project.resolve("data/direto.json")));
        settings.put("path", "${VARIAVEL_AUSENTE_JSONDB_TESTE}");
        assertThrows(IllegalArgumentException.class, () -> JsonDb.execute(settings, null, input));
        settings.put("path", ".env");
        assertThrows(IllegalArgumentException.class, () -> JsonDb.execute(settings, null, input));
    }
}
