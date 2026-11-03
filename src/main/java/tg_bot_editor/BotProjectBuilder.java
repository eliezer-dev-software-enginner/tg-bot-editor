package tg_bot_editor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Constrói e verifica o projeto sem iniciar o bot nem acessar o Telegram. */
final class BotProjectBuilder {
    private BotProjectBuilder() { }

    static Path build(Path directory) throws Exception {
        directory = directory.toAbsolutePath().normalize();
        boolean windows = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).startsWith("windows");
        Path wrapper = directory.resolve(windows ? "gradlew.bat" : "gradlew");
        if (!Files.isRegularFile(wrapper)) throw new IOException("O projeto não possui o Gradle Wrapper: " + wrapper.getFileName());
        Path log = directory.resolve("build/tg-bot-editor-build.log");
        Files.createDirectories(log.getParent());
        run(directory, log, false, gradleCommand(wrapper, windows));
        Path libs = directory.resolve("build/libs");
        Path expected = libs.resolve(directory.getFileName() + ".jar");
        Path jar;
        if (Files.isRegularFile(expected)) jar = expected;
        else {
            try (var files = Files.list(libs)) {
                var jars = files.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".jar"))
                        .filter(path -> !path.toString().endsWith("-plain.jar")).toList();
                if (jars.size() != 1) throw new IOException("Não foi possível identificar o JAR construído em " + libs);
                jar = jars.getFirst();
            }
        }
        String java = Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java").toString();
        run(directory, log, true, List.of(java, "-jar", jar.toString(), "--check", "bot.json"));
        createDistribution(directory, jar);
        return jar;
    }

    static List<String> gradleCommand(Path wrapper, boolean windows) {
        return windows ? List.of("cmd.exe", "/d", "/c", wrapper.toString(), "build")
                : List.of("bash", wrapper.toString(), "build");
    }

    static Path distributionPath(Path directory) { return directory.resolve("build/distributions/bot-portatil.zip"); }

    static void createDistribution(Path directory, Path jar) throws IOException {
        Path output = distributionPath(directory);
        Files.createDirectories(output.getParent());
        Path temporary = Files.createTempFile(output.getParent(), ".bot-portatil-", ".zip");
        try {
            try (var zip = new ZipOutputStream(Files.newOutputStream(temporary))) {
                addFile(zip, jar, "bot.jar");
                addFile(zip, directory.resolve("bot.json"), "bot.json");
                Path diagrams = directory.resolve("diagrams.json");
                if (Files.isRegularFile(diagrams, java.nio.file.LinkOption.NOFOLLOW_LINKS)) addFile(zip, diagrams, "diagrams.json");
                Path scripts = directory.resolve("scripts");
                if (Files.isDirectory(scripts, java.nio.file.LinkOption.NOFOLLOW_LINKS)) try (var paths = Files.walk(scripts)) {
                    for (Path file : paths.filter(p -> Files.isRegularFile(p, java.nio.file.LinkOption.NOFOLLOW_LINKS))
                            .filter(p -> p.getFileName().toString().endsWith(".java")).sorted().toList()) {
                        addFile(zip, file, "scripts/" + scripts.relativize(file).toString().replace('\\', '/'));
                    }
                }
                zip.putNextEntry(new ZipEntry("LEIA-ME.txt"));
                zip.write(("Requer JDK 21 ou superior. Configure um arquivo .env com TG_BOT_TOKEN e TG_BOT_ADMIN_ID.\n"
                        + "Valide: java -jar bot.jar --check bot.json\nInicie: java -jar bot.jar bot.json\n"
                        + "Este pacote não inclui .env, dados persistentes ou imagens de anúncios.\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            try { Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    private static void addFile(ZipOutputStream zip, Path path, String name) throws IOException {
        if (!Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) throw new IOException("Arquivo necessário ausente: " + path);
        zip.putNextEntry(new ZipEntry(name)); Files.copy(path, zip); zip.closeEntry();
    }

    private static void run(Path directory, Path log, boolean append, List<String> command) throws Exception {
        var builder = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true);
        builder.redirectOutput(append ? ProcessBuilder.Redirect.appendTo(log.toFile()) : ProcessBuilder.Redirect.to(log.toFile()));
        int exit = builder.start().waitFor();
        if (exit != 0) throw new IOException("Processo terminou com código " + exit + ". Consulte " + log);
    }
}
