package tg_bot_editor;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Compiles trusted, locally authored Java action bodies with a required return value. */
public final class ActionCompiler {
    private ActionCompiler() { }

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
