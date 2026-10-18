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
        Path projectFile = ProjectWorkspace.copyStarter(directory);
        var project = new FlowProject();
        var flow = new FlowProject.Flow("g1", "G1", "/g1", 0, 0);
        flow.script = "return G1NewsAction.pending(new com.fasterxml.jackson.databind.ObjectMapper().readTree("
                + new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(feed.toString())
                + "), java.nio.file.Path.of(\"" + state + "\"));";
        project.flows.add(flow);
        var action = ActionCompiler.compileProject(projectFile.getParent(), project).get("g1");
        @SuppressWarnings("unchecked") var pending = (java.util.List<BotReply>) action.run(new BotContext(1, 1, "", ""));
        assertEquals(1, pending.size());
        assertFalse(java.nio.file.Files.exists(state));
        pending.getFirst().afterSend().run();
        assertTrue(((java.util.List<?>) action.run(new BotContext(1, 1, "", ""))).isEmpty());
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
        Path projectFile = ProjectWorkspace.copyStarter(directory.resolve("starter"));
        var project = new FlowProject();
        var flow = new FlowProject.Flow("rom", "ROM", "/rom", 0, 0);
        flow.script = "return RomPostAction.fromCatalog(java.nio.file.Path.of(\"" + directory + "\"));";
        project.flows.add(flow);
        var reply = (BotReply) ActionCompiler.compileProject(projectFile.getParent(), project)
                .get("rom").run(new BotContext(1, 1, "", ""));
        assertEquals(cover.toString(), reply.photoRef());
        assertTrue(reply.text().contains("&lt;novo&gt;"));
        assertEquals(2, reply.buttons().size());
    }

    @Test void bundledProjectCompilesEveryAction() throws Exception {
        Path file = ProjectWorkspace.copyStarter(directory);
        var project = FlowStore.read(file);
        FlowValidator.validate(project);
        assertEquals(project.flows.size(), ActionCompiler.compileProject(file.getParent(), project).size());
    }

    @Test void firstLaunchInstallsAnEditableProjectOnlyOnce() throws Exception {
        Path file = ProjectWorkspace.ensureStarter(directory);
        assertEquals("meu-primeiro-projeto", file.getParent().getFileName().toString());
        assertEquals("meu-primeiro-projeto", FlowStore.read(file).name);
        assertEquals(15, FlowStore.read(file).flows.size());
        assertTrue(java.nio.file.Files.isRegularFile(file.getParent().resolve("scripts/GenshinAdsAction.java")));
        java.nio.file.Files.writeString(file.getParent().resolve("scripts/MyChange.java"), "class MyChange {}\n");
        assertEquals(file, ProjectWorkspace.ensureStarter(directory));
        assertTrue(java.nio.file.Files.isRegularFile(file.getParent().resolve("scripts/MyChange.java")));
    }

    @Test void oldStarterIsRenamedWithoutLosingLocalScriptsOrData() throws Exception {
        Path old = directory.resolve("arsenal-soup");
        java.nio.file.Files.createDirectories(old.resolve("scripts"));
        java.nio.file.Files.createDirectories(old.resolve("data"));
        java.nio.file.Files.writeString(old.resolve("scripts/MyChange.java"), "class MyChange {}\n");
        java.nio.file.Files.writeString(old.resolve("data/ads.json"), "{}\n");
        var project = new FlowProject();
        project.name = "Bot unificado";
        FlowStore.write(old.resolve("bot.json"), project);

        Path renamed = ProjectWorkspace.migrateStarter(directory);

        assertEquals("meu-primeiro-projeto", renamed.getParent().getFileName().toString());
        assertEquals("meu-primeiro-projeto", FlowStore.read(renamed).name);
        assertTrue(java.nio.file.Files.exists(renamed.getParent().resolve("scripts/MyChange.java")));
        assertTrue(java.nio.file.Files.exists(renamed.getParent().resolve("data/ads.json")));
        assertFalse(java.nio.file.Files.exists(old));
    }

    @Test void projectScriptsAndVariablesAreSeparateFromTheEditor() throws Exception {
        Path file = ProjectWorkspace.create(directory, "Meu bot");
        assertTrue(java.nio.file.Files.isRegularFile(file.getParent().resolve("pom.xml")));
        assertTrue(java.nio.file.Files.isRegularFile(file.getParent().resolve("README.md")));
        assertTrue(java.nio.file.Files.isRegularFile(file.getParent().resolve("runtime/src/main/java/tg_bot_editor/BotRunner.java")));
        assertTrue(java.nio.file.Files.readString(file.getParent().resolve(".gitignore")).contains("/target/"));
        BotRunner.main(new String[]{"--check", file.toString()});
        Path script = ProjectWorkspace.createScript(file.getParent(), "MeuNovoScript");
        assertTrue(java.nio.file.Files.readString(script).contains("class MeuNovoScript"));
        ProjectEnvironment.write(file.getParent(), "MEU_TOKEN=TESTE\nMEU_TOKEN2=TESTEABC\n");
        ProjectEnvironment.activate(file.getParent());
        assertEquals("TESTEABC", ProjectEnvironment.get("MEU_TOKEN2"));
        assertFalse(java.nio.file.Files.readString(file).contains("TESTEABC"));
        var recent = new RecentProjects(directory.resolve("settings/recent.properties"));
        recent.remember(file);
        assertEquals(file, recent.list().getFirst());
    }

    @Test void genshinAdsWizardPersistsAnAdInsideTheProject() throws Exception {
        Path file = ProjectWorkspace.copyStarter(directory);
        ProjectEnvironment.activate(file.getParent());
        var project = FlowStore.read(file);
        var actions = ActionCompiler.compileProject(file.getParent(), project);
        assertTrue(actions.get("ads-new").run(new BotContext(1, 42, "/novo", "tester")).toString().contains("foto"));
        assertTrue(actions.get("ads-photo").run(new BotContext(1, 42, "", "tester", "photo-file-id"))
                .toString().contains("título"));
        actions.get("ads-text").run(new BotContext(1, 42, "Anúncio teste", "tester"));
        actions.get("ads-text").run(new BotContext(1, 42, "5", "tester"));
        actions.get("ads-text").run(new BotContext(1, 42, "Site | https://example.com", "tester"));
        assertTrue(actions.get("ads-save").run(new BotContext(1, 42, "/save", "tester"))
                .toString().contains("salvo"));
        assertTrue(java.nio.file.Files.isRegularFile(file.getParent().resolve("data/genshin-ads.json")));
        assertEquals(1, ((java.util.List<?>) actions.get("ads-list")
                .run(new BotContext(1, 42, "/listar", "tester"))).size());
    }
}
