package tg_bot_editor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class BotProjectBuilderTest {
    @Test void reportsBuildFailureAndKeepsLog(@TempDir Path project) throws Exception {
        Files.writeString(project.resolve("gradlew"), "#!/bin/sh\necho erro-de-teste\nexit 17\n");
        var error = assertThrows(java.io.IOException.class, () -> BotProjectBuilder.build(project));
        assertTrue(error.getMessage().contains("17"));
        assertTrue(Files.readString(project.resolve("build/tg-bot-editor-build.log")).contains("erro-de-teste"));
    }

    @Test void buildsAndChecksGeneratedJar(@TempDir Path project) throws Exception {
        Files.writeString(project.resolve("bot.json"), "{}");
        Files.writeString(project.resolve("diagrams.json"), "{}");
        Files.createDirectories(project.resolve("scripts"));
        Files.writeString(project.resolve("scripts/Example.java"), "class Example {}");
        Files.writeString(project.resolve(".env"), "TG_BOT_TOKEN=private");
        Files.createDirectories(project.resolve("data")); Files.writeString(project.resolve("data/bot-database.json"), "private");
        Path source = project.resolve("Check.java");
        Files.writeString(source, "public class Check { public static void main(String[] args) { if (args.length != 2 || !args[0].equals(\"--check\")) System.exit(2); System.out.println(\"checked\"); } }");
        assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", project.toString(), source.toString()));
        Path libs = project.resolve("build/libs"); Files.createDirectories(libs);
        Path jar = libs.resolve(project.getFileName() + ".jar");
        String jarTool = Path.of(System.getProperty("java.home"), "bin", "jar").toString();
        assertEquals(0, new ProcessBuilder(jarTool, "--create", "--file", jar.toString(), "--main-class", "Check", "-C", project.toString(), "Check.class").start().waitFor());
        Files.writeString(project.resolve("gradlew"), "#!/bin/sh\necho build-ok\n");
        assertEquals(jar, BotProjectBuilder.build(project));
        String log = Files.readString(project.resolve("build/tg-bot-editor-build.log"));
        assertTrue(log.contains("build-ok")); assertTrue(log.contains("checked"));
        try (var zip = new java.util.zip.ZipFile(BotProjectBuilder.distributionPath(project).toFile())) {
            assertNotNull(zip.getEntry("bot.jar")); assertNotNull(zip.getEntry("bot.json"));
            assertNotNull(zip.getEntry("diagrams.json")); assertNotNull(zip.getEntry("scripts/Example.java"));
            assertNotNull(zip.getEntry("LEIA-ME.txt"));
            assertNull(zip.getEntry(".env")); assertNull(zip.getEntry("data/bot-database.json"));
        }
    }

    @Test void selectsTheWrapperForEachPlatform() {
        assertEquals(java.util.List.of("bash", "gradlew", "build"), BotProjectBuilder.gradleCommand(Path.of("gradlew"), false));
        assertEquals(java.util.List.of("cmd.exe", "/d", "/c", "gradlew.bat", "build"), BotProjectBuilder.gradleCommand(Path.of("gradlew.bat"), true));
    }
}
