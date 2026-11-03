package tg_bot_editor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class JsonDbPathTest {
    @Test void setsAndRemovesNestedValuesWithoutChangingOtherData(@TempDir Path project) throws Exception {
        ProjectEnvironment.useDraft(project, "");
        Files.writeString(project.resolve("db.json"), "{\"ads\":{\"globalIntervals\":{\"8\":15},\"ads\":[{\"id\":\"a\"}]}}");
        var settings = new java.util.HashMap<String, Object>(Map.of("operation", "SET", "path", "db.json",
                "dataPath", "ads.globalIntervals.{{output.owner}}", "record", "{{output.value}}"));
        JsonDb.execute(settings, null, Map.of("owner", "7", "value", 30));
        settings.put("operation", "LIST");
        assertEquals(30, JsonDb.execute(settings, null, Map.of("owner", "7")));
        settings.put("operation", "REMOVE"); JsonDb.execute(settings, null, Map.of("owner", "7"));
        settings.put("operation", "LIST");
        assertNull(JsonDb.execute(settings, null, Map.of("owner", "7")));
        assertEquals(15, JsonDb.execute(settings, null, Map.of("owner", "8")));
        settings.put("dataPath", "ads.ads");
        assertEquals(java.util.List.of(Map.of("id", "a")), JsonDb.execute(settings, null, null));
    }
    @Test void readsPathsAndProjectsValuesAcrossLists(@TempDir Path project) throws Exception {
        ProjectEnvironment.useDraft(project, "");
        Files.writeString(project.resolve("db.json"), """
                {"user":{"name":"Maria","age":26},"products":[
                  {"name":"Phone","fixes_id":[1234,4567,2313,1111]},
                  {"name":"Tablet","fixes_id":[8,9]}]}
                """);
        var settings = new java.util.HashMap<String, Object>(Map.of("operation", "LIST", "path", "db.json", "dataPath", "user.name"));
        assertEquals("Maria", JsonDb.execute(settings, null, null));
        settings.put("dataPath", "products.fixes_id");
        assertEquals(java.util.List.of(1234, 4567, 2313, 1111, 8, 9), JsonDb.execute(settings, null, null));
        settings.put("dataPath", "products.name");
        assertEquals(java.util.List.of("Phone", "Tablet"), JsonDb.execute(settings, null, null));
        settings.put("dataPath", "user.unknown"); assertNull(JsonDb.execute(settings, null, null));
        settings.put("dataPath", ""); assertTrue(((Map<?, ?>) JsonDb.execute(settings, null, null)).containsKey("products"));
    }

    @Test void writesToNestedListAndPreservesOtherFields(@TempDir Path project) throws Exception {
        ProjectEnvironment.useDraft(project, "");
        Files.writeString(project.resolve("db.json"), "{\"user\":{\"name\":\"Maria\"},\"shop\":{\"products\":[{\"id\":1}]}}");
        var settings = new java.util.HashMap<String, Object>(Map.of("operation", "UPSERT", "path", "db.json",
                "dataPath", "shop.products", "record", "{{output}}", "identity", "id"));
        JsonDb.execute(settings, null, Map.of("id", 2, "name", "Phone"));
        settings.put("operation", "LIST");
        assertEquals(2, ((java.util.List<?>) JsonDb.execute(settings, null, null)).size());
        settings.put("dataPath", "user.name"); assertEquals("Maria", JsonDb.execute(settings, null, null));
        settings.put("dataPath", "shop.products"); settings.put("operation", "DELETE");
        JsonDb.execute(settings, null, Map.of("id", 1));
        settings.put("operation", "LIST"); assertEquals(java.util.List.of(Map.of("id", 2, "name", "Phone")), JsonDb.execute(settings, null, null));
        settings.put("dataPath", "groups.{{output.group}}"); settings.put("operation", "UPSERT");
        settings.put("record", "{{output.item}}");
        JsonDb.execute(settings, null, Map.of("group", "abc", "item", Map.of("id", 5)));
        settings.put("operation", "LIST");
        assertEquals(java.util.List.of(Map.of("id", 5)), JsonDb.execute(settings, null, Map.of("group", "abc")));
    }
    @Test void objectListReadsEntireSection(@TempDir Path project) throws Exception {
        ProjectEnvironment.useDraft(project, "");
        var saved = Map.of("ads", java.util.List.of(Map.of("id", "a")), "globalIntervals", Map.of("42", 15));
        BotDatabase.write("ads", saved);
        var settings = new java.util.HashMap<String, Object>(Map.of("operation", "LIST", "layout", "OBJECT", "section", "ads"));
        assertEquals(saved, JsonDb.execute(settings, null, null));
        settings.put("operation", "DELETE");
        assertThrows(IllegalArgumentException.class, () -> JsonDb.execute(settings, null, null));
    }
    @Test void objectArrayUpdatesKeepOtherPropertiesAndCanRetainInput(@TempDir Path project) throws Exception {
        ProjectEnvironment.useDraft(project, "");
        BotDatabase.write("ads", Map.of("ads", java.util.List.of(Map.of("id", "a")),
                "globalIntervals", Map.of("42", 15)));
        var settings = new java.util.HashMap<String, Object>(Map.of("operation", "UPSERT", "layout", "OBJECT_ARRAY",
                "section", "ads", "collection", "ads", "record", "{{output}}", "identity", "id"));
        JsonDb.execute(settings, null, Map.of("id", "b"));
        settings.put("operation", "LIST"); settings.put("includeInput", true);
        var result = (Map<?, ?>) JsonDb.execute(settings, null, Map.of("draft", "retido"));
        assertEquals(Map.of("draft", "retido"), result.get("input"));
        assertEquals(2, ((java.util.List<?>) result.get("data")).size());
        var stored = BotDatabase.read("ads", null, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}, java.util.LinkedHashMap::new);
        assertEquals(Map.of("42", 15), stored.get("globalIntervals"));
    }
    @Test void arrayCollectionImportsLegacyAndUpdatesByIdentity(@TempDir Path project) throws Exception {
        ProjectEnvironment.useDraft(project, "");
        Files.createDirectories(project.resolve("data"));
        Files.writeString(project.resolve("data/legacy.json"),
                "[{\"id\":\"a\",\"lastSeen\":{\"-100:7\":\"video\"}},{\"id\":\"b\"}]");
        var settings = new java.util.HashMap<String, Object>(Map.of("operation", "LIST", "layout", "ARRAY",
                "section", "channels", "legacy", "data/legacy.json", "record", "{{output}}", "identity", "id"));
        var initial = (java.util.List<?>) JsonDb.execute(settings, null, null);
        assertEquals(Map.of("-100:7", "video"), ((Map<?, ?>) initial.getFirst()).get("lastSeen"));
        settings.put("operation", "INSERT");
        assertEquals(false, ((Map<?, ?>) JsonDb.execute(settings, null, Map.of("id", "a", "lastSeen", Map.of()))).get("inserted"));
        assertEquals(true, ((Map<?, ?>) JsonDb.execute(settings, null, Map.of("id", "c", "title", "Canal"))).get("inserted"));
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
