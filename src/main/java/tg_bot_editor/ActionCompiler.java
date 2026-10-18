package tg_bot_editor;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Compiles trusted, locally authored Java action bodies with a required return value. */
public final class ActionCompiler {
    private ActionCompiler() { }

    /** Compile all project-owned source files and action bodies in one class loader. */
    public static Map<String, BotAction> compileProject(Path projectDirectory, FlowProject project) throws Exception {
        if (projectDirectory == null) throw new IllegalArgumentException("Abra um projeto para compilar scripts.");
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("É necessário um JDK para compilar scripts Java");
        Path scripts = projectDirectory.resolve("scripts");
        if (!Files.isDirectory(scripts)) throw new IllegalArgumentException("Pasta scripts ausente: " + scripts);
        Path directory = Files.createTempDirectory("tg-bot-project-");
        var sources = new ArrayList<Path>();
        try (var files = Files.walk(scripts)) {
            files.filter(file -> Files.isRegularFile(file) && file.toString().endsWith(".java"))
                    .forEach(sources::add);
        }
        var classes = new HashMap<String, String>();
        for (var flow : project.flows) {
            String name = "Action_" + java.util.UUID.randomUUID().toString().replace("-", "");
            Path source = directory.resolve(name + ".java");
            Files.writeString(source, "import tg_bot_editor.*; public final class " + name
                    + " implements BotAction { public Object run(BotContext context) throws Exception {\n"
                    + flow.script + "\n}}", StandardCharsets.UTF_8);
            sources.add(source);
            classes.put(flow.id, name);
        }
        if (sources.isEmpty()) return Map.of();
        var errors = new DiagnosticCollector<JavaFileObject>();
        try (var files = compiler.getStandardFileManager(errors, null, StandardCharsets.UTF_8)) {
            var units = files.getJavaFileObjectsFromPaths(sources);
            boolean success = compiler.getTask(null, files, errors,
                    List.of("-classpath", System.getProperty("java.class.path"), "-d", directory.toString()),
                    null, units).call();
            if (!success) throw new IllegalArgumentException(errors.getDiagnostics().toString());
        }
        try (var files = Files.walk(directory)) {
            files.filter(Files::isRegularFile).forEach(file -> file.toFile().deleteOnExit());
        }
        directory.toFile().deleteOnExit();
        var loader = new URLClassLoader(new java.net.URL[]{directory.toUri().toURL()}, BotAction.class.getClassLoader());
        var actions = new HashMap<String, BotAction>();
        for (var entry : classes.entrySet()) {
            actions.put(entry.getKey(), (BotAction) loader.loadClass(entry.getValue()).getConstructor().newInstance());
        }
        return Map.copyOf(actions);
    }

    public static BotAction compile(String body) throws Exception {
        if (body == null || body.isBlank()) throw new IllegalArgumentException("A ação Java está vazia");
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("É necessário executar com um JDK para compilar ações Java");
        String name = "Action_" + java.util.UUID.randomUUID().toString().replace("-", "");
        Path directory = Files.createTempDirectory("tg-bot-action-");
        Path source = directory.resolve(name + ".java");
        Files.writeString(source, "import tg_bot_editor.*; public final class " + name
                + " implements BotAction { public Object run(BotContext context) throws Exception {\n"
                + body + "\n}}", StandardCharsets.UTF_8);
        var errors = new DiagnosticCollector<JavaFileObject>();
        try (var files = compiler.getStandardFileManager(errors, null, StandardCharsets.UTF_8)) {
            var units = files.getJavaFileObjects(source.toFile());
            boolean success = compiler.getTask(null, files, errors,
                    List.of("-classpath", System.getProperty("java.class.path"), "-d", directory.toString()),
                    null, units).call();
            if (!success) throw new IllegalArgumentException(errors.getDiagnostics().toString());
        }
        // Nested/lambda classes may be loaded only when the action runs, so keep
        // its output available for the life of this process.
        try (var paths = Files.list(directory)) {
            paths.forEach(path -> path.toFile().deleteOnExit());
        }
        directory.toFile().deleteOnExit();
        var loader = new URLClassLoader(new java.net.URL[]{directory.toUri().toURL()}, BotAction.class.getClassLoader());
        return (BotAction) loader.loadClass(name).getConstructor().newInstance();
    }
}
