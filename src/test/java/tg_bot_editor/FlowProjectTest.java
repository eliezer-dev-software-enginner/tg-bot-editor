package tg_bot_editor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FlowProjectTest {
    @TempDir Path directory;

    @Test void youtubeNotificationsStartAtCurrentVideoAndRetryFailedDeliveries() throws Exception {
        String channel = "UC1234567890123456789012";
        var xml = new java.util.concurrent.atomic.AtomicReference<>(youtubeFeed("AAAAAAAAAAA"));
        var subscriptions = new YouTubeSubscriptions(directory, url -> xml.get());
        assertTrue(subscriptions.add("https://www.youtube.com/channel/" + channel).contains("inscrito"));
        var topic = new ActionBindings.Destination(-100123, 37);
        var delivered = new java.util.ArrayList<String>();
        assertEquals(0, subscriptions.check(java.util.List.of(topic), (target, text) -> delivered.add(text)));
        xml.set(youtubeFeed("BBBBBBBBBBB", "AAAAAAAAAAA"));
        assertThrows(java.io.IOException.class, () -> subscriptions.check(java.util.List.of(topic),
                (target, text) -> { throw new java.io.IOException("Telegram offline"); }));
        var reloaded = new YouTubeSubscriptions(directory, url -> xml.get());
        assertEquals(1, reloaded.check(java.util.List.of(topic), (target, text) -> delivered.add(text)));
        assertEquals(0, reloaded.check(java.util.List.of(topic), (target, text) -> delivered.add(text)));
        assertEquals(1, delivered.size());
        assertTrue(delivered.getFirst().contains("youtube.com/watch?v=BBBBBBBBBBB"));
        assertTrue(subscriptions.list().contains(channel));
        assertTrue(subscriptions.remove(channel).contains("removida"));
    }

    @Test void resolvesYoutubeHandleFromPublicChannelMetadata() throws Exception {
        String channel = "UC1234567890123456789012";
        ProjectEnvironment.activate(directory);
        var subscriptions = new YouTubeSubscriptions(directory, url -> url.contains("/@CanalTeste")
                ? "<script>{\"externalId\":\"" + channel + "\"}</script>" : youtubeFeed("AAAAAAAAAAA"));
        assertTrue(subscriptions.add("@CanalTeste").contains(channel));
        assertThrows(IllegalArgumentException.class, () -> subscriptions.add("https://example.com/@CanalTeste"));
    }

    private static String youtubeFeed(String... ids) {
        StringBuilder xml = new StringBuilder("<feed xmlns=\"http://www.w3.org/2005/Atom\" xmlns:yt=\"http://www.youtube.com/xml/schemas/2015\">");
        for (String id : ids) xml.append("<entry><yt:videoId>").append(id)
                .append("</yt:videoId><title>Vídeo ").append(id).append("</title></entry>");
        return xml.append("</feed>").toString();
    }

    @Test void listsAndBindsActionsToGroupsAndTopicsWithoutEditingJava() throws Exception {
        var project = new FlowProject();
        var first = new FlowProject.Flow("news", "Notícias", "60", 0, 0);
        first.trigger = "INTERVAL";
        var second = new FlowProject.Flow("summary", "Resumo", "09:00", 0, 0);
        second.trigger = "DAILY";
        project.flows.add(first);
        project.flows.add(second);
        java.nio.file.Files.createDirectories(directory.resolve("scripts"));
        ProjectEnvironment.activate(directory);
        var runner = new BotRunner("test-token", directory, project);
        var topic = new BotContext(-100123, 42, "", "tester", null, 37);
        var group = new BotContext(-100123, 42, "", "tester");
        assertTrue(runner.actionCommand("/actions", "", topic).contains("news — Notícias (INTERVAL)"));
        assertTrue(runner.actionCommand("/bindaction", "news", topic).contains("vinculada"));
        assertTrue(runner.actionCommand("/bindaction", "summary", topic).contains("vinculada"));
        assertTrue(runner.actionCommand("/bindaction", "news", topic).contains("já está"));
        assertTrue(runner.actionCommand("/bindaction", "news", group).contains("vinculada"));
        assertTrue(runner.actionCommand("/bindings", "", topic).contains("news\nsummary"));
        assertEquals(java.util.List.of(new ActionBindings.Destination(-100123, 37),
                new ActionBindings.Destination(-100123, null)),
                new ActionBindings(directory).destinations("news"));
        assertTrue(runner.actionCommand("/unbindaction", "news", topic).contains("removido"));
        assertEquals(java.util.List.of(new ActionBindings.Destination(-100123, null)),
                new ActionBindings(directory).destinations("news"));
        assertTrue(runner.actionCommand("/bindaction", "missing", topic).contains("ID válido"));
    }

    @Test void themePreferencePersistsOutsideBotProjects() throws Exception {
        String previous = System.getProperty("tg.bot.editor.settingsDir");
        System.setProperty("tg.bot.editor.settingsDir", directory.resolve("settings").toString());
        try {
            assertFalse(ThemePreference.isDark());
            ThemePreference.save(true);
            assertTrue(ThemePreference.isDark());
            ThemePreference.save(false);
            assertFalse(ThemePreference.isDark());
        } finally {
            if (previous == null) System.clearProperty("tg.bot.editor.settingsDir");
            else System.setProperty("tg.bot.editor.settingsDir", previous);
        }
    }

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

    @Test void routesExactAndLongerPrefixesBeforeGeneralWildcard() {
        var project = new FlowProject();
        project.flows.add(new FlowProject.Flow("all", "Todos", "*", 0, 0));
        project.flows.add(new FlowProject.Flow("ads", "Anúncios", "ads:*", 0, 0));
        project.flows.add(new FlowProject.Flow("edit", "Editar", "ads:edit:*", 0, 0));
        project.flows.add(new FlowProject.Flow("specific", "Específico", "ads:edit:42", 0, 0));
        project.flows.forEach(flow -> flow.trigger = "CALLBACK");

        assertEquals("specific", FlowRouter.find(project, "CALLBACK", "ads:edit:42").orElseThrow().id);
        assertEquals("edit", FlowRouter.find(project, "CALLBACK", "ads:edit:43").orElseThrow().id);
        assertEquals("ads", FlowRouter.find(project, "CALLBACK", "ads:delete:42").orElseThrow().id);
        assertEquals("all", FlowRouter.find(project, "CALLBACK", "other").orElseThrow().id);
    }

    @Test void dailyScheduleDoesNotFireEarlyInTheLastSecond() {
        var before = java.time.ZonedDateTime.parse("2026-10-19T08:59:59.750-03:00[America/Sao_Paulo]");
        assertEquals(250_000_000L, BotRunner.delayUntilDailyNanos(before, java.time.LocalTime.of(9, 0)));
        assertEquals(java.time.Duration.ofDays(1).toNanos(),
                BotRunner.delayUntilDailyNanos(before.plusNanos(250_000_000L), java.time.LocalTime.of(9, 0)));
    }

    @Test void startupPublicationKeepsDailyScheduleWithoutImmediateDuplicate() throws Exception {
        var project = new FlowProject();
        var news = new FlowProject.Flow("g1-news", "Notícias", "09:00,15:00,21:00", 0, 0);
        news.trigger = "DAILY";
        news.runOnStart = true;
        project.flows.add(news);
        Path saved = directory.resolve("bot.json");
        FlowStore.write(saved, project);
        assertTrue(FlowStore.read(saved).flows.get(0).runOnStart);
        var start = java.time.Instant.parse("2026-10-20T11:59:00Z");
        assertFalse(BotRunner.shouldSkipDailyAfterStartup(null, start));
        assertTrue(BotRunner.shouldSkipDailyAfterStartup(start, start.plusSeconds(29 * 60)));
        assertFalse(BotRunner.shouldSkipDailyAfterStartup(start, start.plusSeconds(30 * 60)));
    }

    @Test void permanentTelegramErrorsExposeReasonAndStopPolling() {
        var error = assertThrows(java.io.IOException.class, () -> BotRunner.parseTelegramResponse(
                400, "getUpdates", "{\"ok\":false,\"description\":\"Bad Request: invalid offset TOKEN123\"}",
                "TOKEN123"));
        assertTrue(error.getMessage().contains("getUpdates HTTP 400: Bad Request: invalid offset"));
        assertFalse(error.getMessage().contains("TOKEN123"));
        assertTrue(BotRunner.shouldStopPolling(400));
        assertTrue(BotRunner.shouldStopPolling(409));
        assertFalse(BotRunner.shouldStopPolling(429));
        assertFalse(BotRunner.shouldStopPolling(500));
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

    @Test void bundledProjectCompilesEveryAction() throws Exception {
        Path file = ProjectWorkspace.copyStarter(directory);
        var project = FlowStore.read(file);
        FlowValidator.validate(project);
        assertEquals(project.flows.size(), ActionCompiler.compileProject(file.getParent(), project).size());
    }

    @Test void exportsOnlyAvailableCommandsWithDescriptions() throws Exception {
        Path file = ProjectWorkspace.copyStarter(directory);
        var project = FlowStore.read(file);
        Path output = CommandExporter.export(project, file.getParent());
        String commands = java.nio.file.Files.readString(output);
        assertTrue(commands.contains("/start — Mostra uma mensagem de boas-vindas"));
        assertTrue(commands.contains("/addchat — Adiciona um grupo ou canal"));
        assertFalse(commands.contains("Publicar notícias do G1"));
        assertFalse(commands.contains("ads:*"));
        assertTrue(commands.contains("/topic — Mostra os IDs do grupo e do tópico atual"));
        assertEquals(10, commands.lines().filter(line -> line.startsWith("/")).count());
    }

    @Test void firstLaunchInstallsAnEditableProjectOnlyOnce() throws Exception {
        Path file = ProjectWorkspace.ensureStarter(directory);
        assertEquals("meu-primeiro-projeto", file.getParent().getFileName().toString());
        assertEquals("meu-primeiro-projeto", FlowStore.read(file).name);
        assertEquals(15, FlowStore.read(file).flows.size());
        assertTrue(java.nio.file.Files.isRegularFile(file.getParent().resolve("scripts/GenshinAdsAction.java")));
        assertTrue(java.nio.file.Files.readString(file.getParent().resolve(".env.example"))
                .contains("TG_BOT_ADS_DATA_DIR="));
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
        assertTrue(java.nio.file.Files.isRegularFile(file.getParent().resolve("gradlew")));
        assertTrue(java.nio.file.Files.isExecutable(file.getParent().resolve("gradlew")));
        assertTrue(java.nio.file.Files.isRegularFile(file.getParent().resolve("build.gradle.kts")));
        assertTrue(java.nio.file.Files.isRegularFile(file.getParent().resolve("gradle/wrapper/gradle-wrapper.jar")));
        assertFalse(java.nio.file.Files.exists(file.getParent().resolve("pom.xml")));
        assertTrue(java.nio.file.Files.isRegularFile(file.getParent().resolve("README.md")));
        assertTrue(java.nio.file.Files.isRegularFile(file.getParent().resolve("runtime/src/main/java/tg_bot_editor/BotRunner.java")));
        assertTrue(java.nio.file.Files.readString(file.getParent().resolve(".gitignore")).contains("/build/"));
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

    @Test void legacyProjectReceivesGradleWithoutReplacingItsFiles() throws Exception {
        Path projectDirectory = directory.resolve("legacy");
        java.nio.file.Files.createDirectories(projectDirectory);
        java.nio.file.Files.writeString(projectDirectory.resolve("pom.xml"), "custom Maven build");
        java.nio.file.Files.writeString(projectDirectory.resolve("scripts-note.txt"), "keep me");

        ProjectWorkspace.initializeLegacyScripts(projectDirectory, new FlowProject());

        assertTrue(java.nio.file.Files.isExecutable(projectDirectory.resolve("gradlew")));
        assertTrue(java.nio.file.Files.isRegularFile(projectDirectory.resolve("build.gradle.kts")));
        assertTrue(java.nio.file.Files.isRegularFile(projectDirectory.resolve("runtime/src/main/java/tg_bot_editor/BotRunner.java")));
        assertEquals("custom Maven build", java.nio.file.Files.readString(projectDirectory.resolve("pom.xml")));
        assertEquals("keep me", java.nio.file.Files.readString(projectDirectory.resolve("scripts-note.txt")));
    }

    @Test void genshinAdsWizardPersistsAnAdInsideTheProject() throws Exception {
        Path file = ProjectWorkspace.copyStarter(directory);
        ProjectEnvironment.activate(file.getParent());
        var project = FlowStore.read(file);
        var actions = ActionCompiler.compileProject(file.getParent(), project);
        assertTrue(actions.get("ads-new").run(new BotContext(1, 42, "/novo", "tester")).toString().contains("foto"));
        assertTrue(actions.get("ads-photo").run(new BotContext(1, 42, "", "tester", "photo-file-id"))
                .toString().contains("título"));
        actions.get("ads-text").run(new BotContext(1, 42, "<b>Anúncio teste</b>", "tester"));
        actions.get("ads-text").run(new BotContext(1, 42, "5", "tester"));
        actions.get("ads-text").run(new BotContext(1, 42, "Site | https://example.com", "tester"));
        assertTrue(actions.get("ads-save").run(new BotContext(1, 42, "/save", "tester"))
                .toString().contains("salvo"));
        assertTrue(java.nio.file.Files.isRegularFile(file.getParent().resolve("data/genshin-ads.json")));
        @SuppressWarnings("unchecked") var listed = (java.util.List<BotReply>) actions.get("ads-list")
                .run(new BotContext(1, 42, "/listar", "tester"));
        assertEquals(1, listed.size());
        assertTrue(listed.getFirst().html());
        assertTrue(listed.getFirst().text().contains("<b>Anúncio teste</b>"));
        String chooseTopic = listed.getFirst().buttons().get(3).callbackData();
        assertEquals("Publicar em tópico", listed.getFirst().buttons().get(3).text());
        assertTrue(actions.get("ads-topic").run(new BotContext(-100123, 42, "/topic", "tester", null, 37))
                .toString().contains("/addchat -100123 37"));
        assertTrue(actions.get("ads-topic").run(new BotContext(-100123, 42, "/topic", "tester"))
                .toString().contains("/addchat -100123"));
        actions.get("ads-addchat").run(new BotContext(1, 42, "/addchat -100123 37 Atualizações", "tester"));
        actions.get("ads-addchat").run(new BotContext(1, 42, "/addchat -100999 52 Outro tópico", "tester"));
        var topicMenu = (BotReply) actions.get("ads-callback")
                .run(new BotContext(1, 42, chooseTopic, "tester"));
        assertEquals(2, topicMenu.buttons().size());
        assertTrue(topicMenu.buttons().getFirst().text().contains("Atualizações"));
        String selectedTopic = topicMenu.buttons().getFirst().callbackData();
        assertTrue(selectedTopic.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 64);
        @SuppressWarnings("unchecked") var targeted = (java.util.List<BotDelivery>) actions.get("ads-callback")
                .run(new BotContext(1, 42, selectedTopic, "tester"));
        assertEquals(2, targeted.size());
        assertEquals(-100123, targeted.getFirst().chatId());
        assertEquals(37, targeted.getFirst().threadId());
        assertEquals(1, targeted.getLast().chatId());
        @SuppressWarnings("unchecked") var published = (java.util.List<BotDelivery>) actions.get("ads-publish")
                .run(new BotContext(1, 42, "/publicar", "tester"));
        assertEquals(-100123, published.getFirst().chatId());
        assertEquals(37, published.getFirst().threadId());
        assertTrue(published.getFirst().reply().html());
        assertTrue(published.getFirst().reply().text().contains("<b>Anúncio teste</b>"));
        assertTrue(actions.get("ads-config").run(new BotContext(1, 42, "/config", "tester"))
                .toString().contains("-100123 (tópico 37) — Atualizações"));
        assertEquals("&message_thread_id=37", BotRunner.threadOption(37));
        assertEquals("", BotRunner.threadOption(null));
        assertThrows(IllegalArgumentException.class, () -> BotRunner.threadOption(0));
    }

    @Test void previouslyRegisteredTopicsWithoutNamesRemainAvailable() throws Exception {
        Path file = ProjectWorkspace.copyStarter(directory);
        ProjectEnvironment.activate(file.getParent());
        Path state = file.getParent().resolve("data/genshin-ads.json");
        java.nio.file.Files.createDirectories(state.getParent());
        java.nio.file.Files.writeString(state, """
                {"ads":[],"chats":{},"destinations":{"42":[{"chatId":-100123,"threadId":37}]}}
                """);
        var actions = ActionCompiler.compileProject(file.getParent(), FlowStore.read(file));
        assertTrue(actions.get("ads-config").run(new BotContext(1, 42, "/config", "tester"))
                .toString().contains("-100123 (tópico 37)"));
    }

    @Test void importsLegacyAdsFromConfiguredDirectoryWithoutChangingOriginalFiles() throws Exception {
        Path projectFile = ProjectWorkspace.copyStarter(directory.resolve("project"));
        Path adsDirectory = directory.resolve("legacy-ads");
        java.nio.file.Files.createDirectories(adsDirectory.resolve("images"));
        java.nio.file.Files.write(adsDirectory.resolve("images/cover.jpg"), new byte[]{1, 2, 3});
        String ads = """
                [{"id":"ad-1","ownerUserId":42,"title":"Anúncio antigo","image":"cover.jpg",
                  "buttons":[{"title":"Abrir","url":"https://example.com"}],"interval":60}]
                """;
        String configs = "[{\"userId\":42,\"targetChatIds\":[-100123]}]";
        java.nio.file.Files.writeString(adsDirectory.resolve("ads.json"), ads);
        java.nio.file.Files.writeString(adsDirectory.resolve("configs.json"), configs);
        ProjectEnvironment.useDraft(projectFile.getParent(), "TG_BOT_ADS_DATA_DIR=" + adsDirectory);
        var project = FlowStore.read(projectFile);
        var actions = ActionCompiler.compileProject(projectFile.getParent(), project);
        assertEquals(1, ((java.util.List<?>) actions.get("ads-list")
                .run(new BotContext(1, 42, "/listar", "tester"))).size());
        String imported = java.nio.file.Files.readString(adsDirectory.resolve("genshin-ads.json"));
        assertTrue(imported.contains(adsDirectory.resolve("images/cover.jpg").toString()));
        assertTrue(imported.contains("-100123"));
        @SuppressWarnings("unchecked") var legacyDelivery = (java.util.List<BotDelivery>) actions.get("ads-publish")
                .run(new BotContext(1, 42, "/publicar", "tester"));
        assertEquals(-100123, legacyDelivery.getFirst().chatId());
        assertNull(legacyDelivery.getFirst().threadId());
        assertTrue(((java.util.List<?>) actions.get("ads-tick")
                .run(new BotContext(0, 0, "", "scheduler"))).isEmpty());
        assertEquals(ads, java.nio.file.Files.readString(adsDirectory.resolve("ads.json")));
        assertEquals(configs, java.nio.file.Files.readString(adsDirectory.resolve("configs.json")));
    }
}
