package tg_bot_editor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FlowProjectTest {
    @TempDir Path directory;

    @Test void savesAndRoutesMultipleActionsWithoutCredentials() throws Exception {
        var project = new FlowProject();
        project.flows.add(new FlowProject.Flow("start", "Boas-vindas", "/start", 10, 20));
        project.flows.add(new FlowProject.Flow("help", "Ajuda", "/help", 30, 40));
        Path saved = directory.resolve("bot.json");
        FlowStore.write(saved, project);
        var restored = FlowStore.read(saved);
        assertEquals("help", FlowRouter.find(restored, "COMMAND", "/help me").orElseThrow().id);
        assertTrue(FlowRouter.find(restored, "COMMAND", "/unknown").isEmpty());
        assertEquals(30, restored.flows.get(1).x);
        assertFalse(java.nio.file.Files.readString(saved).contains("token"));
    }

    @Test void compilesJavaReturnAndRejectsMissingReturn() throws Exception {
        var context = new BotContext(7, 8, "/start", "eliezer");
        assertEquals("Olá eliezer", ActionCompiler.compile("return \"Olá \" + context.username();").run(context));
        assertThrows(IllegalArgumentException.class, () -> ActionCompiler.compile("System.out.println(\"oi\");"));
    }

    @Test void rejectsAmbiguousRoutes() {
        var project = new FlowProject();
        project.flows.add(new FlowProject.Flow("a", "A", "/start", 0, 0));
        project.flows.add(new FlowProject.Flow("b", "B", "/START", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> FlowValidator.validate(project));
    }

    @Test void g1FeedRecordsOnlySuccessfullyDeliveredArticles() throws Exception {
        var feed = new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
                [{"content":{"title":"Notícia","summary":"Resumo","url":"https://example.com/1",
                "image":{"sizes":{"L":{"url":"https://example.com/photo.png"}}}}}]
                """);
        Path state = directory.resolve("seen.txt");
        var pending = G1NewsAction.pending(feed, state);
        assertEquals(1, pending.size());
        assertFalse(java.nio.file.Files.exists(state));
        pending.getFirst().afterSend().run();
        assertTrue(G1NewsAction.pending(feed, state).isEmpty());
    }

    @Test void romCatalogProducesPhotoAndDownloadButton() throws Exception {
        Path cover = directory.resolve("cover.jpg");
        java.nio.file.Files.write(cover, new byte[]{1, 2, 3});
        java.nio.file.Files.writeString(directory.resolve("roms.json"), """
                [{"id":"r1","consoleId":"gba","nome":"Teste","descricao":"Jogo <novo>",
                "capaLocalPath":"cover.jpg","linkMonetizado":"https://example.com/download"}]
                """);
        java.nio.file.Files.writeString(directory.resolve("consoles.json"), """
                [{"id":"gba","nome":"GBA"}]
                """);
        var reply = RomPostAction.fromCatalog(directory);
        assertEquals(cover.toString(), reply.photoRef());
        assertTrue(reply.text().contains("&lt;novo&gt;"));
        assertEquals(2, reply.buttons().size());
    }

    @Test void bundledProjectCompilesEveryAction() throws Exception {
        try (var input = getClass().getResourceAsStream("/templates/arsenal-soup.json")) {
            var project = FlowStore.read(input);
            FlowValidator.validate(project);
            for (var flow : project.flows) assertNotNull(ActionCompiler.compile(flow.script));
        }
    }
}
