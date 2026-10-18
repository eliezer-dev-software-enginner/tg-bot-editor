package tg_bot_editor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Portable bot project layout: bot.json, scripts/, and an ignored local .env. */
public final class ProjectWorkspace {
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
        Files.writeString(directory.resolve(".gitignore"), ".env\n/data/\n");
        return directory.resolve("bot.json");
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
        Path scripts = directory.resolve("scripts");
        Files.createDirectories(scripts);
        for (String name : new String[]{"G1NewsAction", "RomPostAction", "GenshinAdsAction"}) {
            if (project.flows.stream().noneMatch(flow -> flow.script != null && flow.script.contains(name + "."))) continue;
            Path target = scripts.resolve(name + ".java");
            if (Files.exists(target)) continue;
            try (InputStream source = ProjectWorkspace.class.getResourceAsStream("/templates/arsenal-soup/scripts/" + name + ".java")) {
                if (source == null) throw new IOException("Missing bundled script: " + name);
                Files.copy(source, target);
            }
        }
        Path ignore = directory.resolve(".gitignore");
        String rules = Files.exists(ignore) ? Files.readString(ignore) : "";
        if (!rules.lines().anyMatch(line -> line.trim().equals(".env"))) rules += "\n.env\n";
        if (!rules.lines().anyMatch(line -> line.trim().equals("/data/"))) rules += "/data/\n";
        Files.writeString(ignore, rules);
    }

    /** Add the missing ads flows to the earlier bundled starter without replacing user edits. */
    public static boolean mergeStarterAds(FlowProject project) throws IOException {
        if (!project.flows.stream().anyMatch(flow -> "g1-news".equals(flow.id))
                || !project.flows.stream().anyMatch(flow -> "daily-roms".equals(flow.id))) return false;
        boolean changed = false;
        try (InputStream input = ProjectWorkspace.class.getResourceAsStream("/templates/arsenal-soup/bot.json")) {
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
        Path projectFile = create(parent, "arsenal-soup");
        Path directory = projectFile.getParent();
        try (InputStream input = ProjectWorkspace.class.getResourceAsStream("/templates/arsenal-soup/bot.json")) {
            if (input == null) throw new IOException("Missing starter project");
            FlowStore.write(projectFile, FlowStore.read(input));
        }
        initializeLegacyScripts(directory, FlowStore.read(projectFile));
        return projectFile;
    }

    /** Install the bundled example once, leaving any existing project untouched. */
    public static Path ensureStarter(Path parent) throws IOException {
        Path projectFile = parent.resolve("arsenal-soup").resolve("bot.json");
        if (Files.isRegularFile(projectFile)) return projectFile;
        if (Files.exists(projectFile.getParent()))
            throw new IOException("A pasta arsenal-soup já existe sem bot.json: " + projectFile.getParent());
        return copyStarter(parent);
    }
}
