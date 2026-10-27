package tg_bot_editor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Portable bot project layout: bot.json, scripts/, and an ignored local .env. */
public final class ProjectWorkspace {
    public static final String STARTER_NAME = "meu-primeiro-projeto";
    private static final List<String> RUNTIME_SOURCES = List.of(
            "ActionCompiler", "BotAction", "BotButton", "BotContext", "BotDelivery", "BotReply",
            "BotRunner", "ActionBindings", "FlowProject", "FlowRouter", "FlowStore", "FlowValidator", "ProjectEnvironment");
    private ProjectWorkspace() { }

    public static Path defaultProjectsDirectory() {
        return Path.of(System.getProperty("user.home"), "Documents", "TG-bot Editor", "projects");
    }

    public static Path create(Path parent, String name) throws IOException {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isBlank() || trimmed.equals(".") || trimmed.equals("..")
                || trimmed.contains("/") || trimmed.contains("\\"))
            throw new IllegalArgumentException("Informe um nome de projeto válido, sem separadores de caminho.");
        Path directory = parent.resolve(trimmed).toAbsolutePath().normalize();
        if (Files.exists(directory)) throw new IOException("Já existe uma pasta com esse nome: " + directory);
        Files.createDirectories(directory.resolve("scripts"));
        var project = new FlowProject();
        project.name = trimmed;
        FlowStore.write(directory.resolve("bot.json"), project);
        Files.writeString(directory.resolve(".gitignore"), ".env\n/data/\n/build/\n/target/\n/.gradle/\n/.idea/\n");
        Files.writeString(directory.resolve(".env.example"), "# Copy to .env and set your Telegram bot token locally.\nTG_BOT_TOKEN=\nTG_BOT_ADMIN_ID=\n");
        ensureGradleBuild(directory);
        copyTemplate("/templates/project-build/README.md", directory.resolve("README.md"));
        ensureRuntimeSources(directory);
        return directory.resolve("bot.json");
    }

    private static void copyTemplate(String resource, Path destination) throws IOException {
        try (InputStream input = ProjectWorkspace.class.getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Arquivo de projeto ausente no editor: " + resource);
            Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void ensureGradleBuild(Path directory) throws IOException {
        for (String relative : List.of("build.gradle.kts", "settings.gradle.kts", "gradlew", "gradlew.bat",
                "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties")) {
            Path target = directory.resolve(relative);
            if (!Files.exists(target)) {
                Files.createDirectories(target.getParent());
                copyTemplate("/templates/project-build/" + relative, target);
            }
        }
        directory.resolve("gradlew").toFile().setExecutable(true);
    }

    private static void ensureRuntimeSources(Path directory) throws IOException {
        Path runtime = directory.resolve("runtime/src/main/java/tg_bot_editor");
        Files.createDirectories(runtime);
        for (String source : RUNTIME_SOURCES) {
            Path target = runtime.resolve(source + ".java");
            if (!Files.exists(target) || source.equals("BotRunner") || source.equals("ActionBindings"))
                copyTemplate("/templates/project-runtime/tg_bot_editor/" + source + ".java", target);
        }
    }

    public static Path createScript(Path projectDirectory, String className) throws IOException {
        String name = className == null ? "" : className.trim();
        if (!name.matches("[A-Za-z_$][A-Za-z0-9_$]*") || !Character.isUpperCase(name.charAt(0)))
            throw new IllegalArgumentException("Use um nome de classe Java, como MeuNovoScript.");
        Path scripts = projectDirectory.resolve("scripts");
        Files.createDirectories(scripts);
        Path file = scripts.resolve(name + ".java");
        Files.writeString(file, "import tg_bot_editor.*;\n\npublic final class " + name + " {\n"
                + "    private " + name + "() { }\n\n"
                + "    public static Object run() throws Exception {\n"
                + "        return \"Olá do " + name + "\";\n"
                + "    }\n}\n", java.nio.file.StandardOpenOption.CREATE_NEW);
        return file;
    }

    public static void initializeLegacyScripts(Path directory, FlowProject project) throws IOException {
        ensureGradleBuild(directory);
        ensureRuntimeSources(directory);
        Path scripts = directory.resolve("scripts");
        Files.createDirectories(scripts);
        for (String name : new String[]{"G1NewsAction", "GenshinAdsAction"}) {
            if (project.flows.stream().noneMatch(flow -> flow.script != null && flow.script.contains(name + "."))) continue;
            Path target = scripts.resolve(name + ".java");
            if (Files.exists(target)) continue;
            try (InputStream source = ProjectWorkspace.class.getResourceAsStream("/templates/meu-primeiro-projeto/scripts/" + name + ".java")) {
                if (source == null) throw new IOException("Missing bundled script: " + name);
                Files.copy(source, target);
            }
        }
        Path ignore = directory.resolve(".gitignore");
        String rules = Files.exists(ignore) ? Files.readString(ignore) : "";
        if (!rules.lines().anyMatch(line -> line.trim().equals(".env"))) rules += "\n.env\n";
        if (!rules.lines().anyMatch(line -> line.trim().equals("/data/"))) rules += "/data/\n";
        Files.writeString(ignore, rules);
        Path example = directory.resolve(".env.example");
        String settings = Files.exists(example) ? Files.readString(example)
                : "# Copy to .env and fill locally.\nTG_BOT_TOKEN=\n";
        if (settings.lines().noneMatch(line -> line.startsWith("TG_BOT_ADMIN_ID=")))
            settings += (settings.endsWith("\n") ? "" : "\n") + "TG_BOT_ADMIN_ID=\n";
        if (project.flows.stream().anyMatch(flow -> flow.script != null && flow.script.contains("GenshinAdsAction."))) {
            if (settings.lines().noneMatch(line -> line.startsWith("TG_BOT_ADS_DATA_DIR=")))
                settings += (settings.endsWith("\n") ? "" : "\n") + "TG_BOT_ADS_DATA_DIR=\n";
        }
        Files.writeString(example, settings);
    }

    /** Add the missing ads flows to the earlier bundled starter without replacing user edits. */
    public static boolean mergeStarterAds(FlowProject project) throws IOException {
        if (!project.flows.stream().anyMatch(flow -> "g1-news".equals(flow.id))) return false;
        boolean changed = false;
        try (InputStream input = ProjectWorkspace.class.getResourceAsStream("/templates/meu-primeiro-projeto/bot.json")) {
            if (input == null) throw new IOException("Missing starter project");
            for (var flow : FlowStore.read(input).flows) {
                if (!flow.id.startsWith("ads-")) continue;
                boolean exists = project.flows.stream().anyMatch(existing -> flow.id.equals(existing.id)
                        || (flow.trigger.equals(existing.trigger)
                        && flow.pattern.equalsIgnoreCase(existing.pattern == null ? "" : existing.pattern)));
                if (!exists) { project.flows.add(flow); changed = true; }
            }
        }
        return changed;
    }

    public static Path copyStarter(Path parent) throws IOException {
        Path projectFile = create(parent, STARTER_NAME);
        Path directory = projectFile.getParent();
        try (InputStream input = ProjectWorkspace.class.getResourceAsStream("/templates/meu-primeiro-projeto/bot.json")) {
            if (input == null) throw new IOException("Missing starter project");
            FlowStore.write(projectFile, FlowStore.read(input));
        }
        initializeLegacyScripts(directory, FlowStore.read(projectFile));
        copyTemplate("/templates/meu-primeiro-projeto/.env.example", directory.resolve(".env.example"));
        return projectFile;
    }

    /** Install the bundled example once, leaving any existing project untouched. */
    public static Path migrateStarter(Path parent) throws IOException {
        Path projectFile = parent.resolve(STARTER_NAME).resolve("bot.json");
        if (Files.isRegularFile(projectFile)) return projectFile;
        Path old = parent.resolve("arsenal-soup");
        if (!Files.isRegularFile(old.resolve("bot.json")) || Files.exists(projectFile.getParent())) return null;
        Files.move(old, projectFile.getParent());
        var project = FlowStore.read(projectFile);
        if ("Bot unificado".equals(project.name) || "arsenal-soup".equals(project.name)) {
            project.name = STARTER_NAME;
            FlowStore.write(projectFile, project);
        }
        return projectFile;
    }

    public static Path ensureStarter(Path parent) throws IOException {
        Path projectFile = migrateStarter(parent);
        if (projectFile != null) return projectFile;
        projectFile = parent.resolve(STARTER_NAME).resolve("bot.json");
        if (Files.exists(projectFile.getParent()))
            throw new IOException("A pasta " + STARTER_NAME + " já existe sem bot.json: " + projectFile.getParent());
        return copyStarter(parent);
    }
}
