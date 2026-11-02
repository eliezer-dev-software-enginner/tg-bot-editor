package tg_bot_editor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;

/** Identifiers exposed by the runtime and the currently opened project's scripts. */
final class JavaSyntaxIdentifiers {
    private static final List<String> COMMON = List.of(
            "context", "input", "index", "ProjectEnvironment", "ProjectActionBindings", "FlowStore",
            "BotContext", "BotReply", "BotButton", "BotDelivery", "BotDatabase", "BotCommandService",
            "String", "StringBuilder", "Long", "Integer", "Boolean", "Double", "Object", "List", "Map", "Set");

    private JavaSyntaxIdentifiers() { }

    static List<String> defaults() { return COMMON; }

    static List<String> forProject(Path directory) throws IOException {
        var identifiers = new TreeSet<>(COMMON);
        if (directory != null && Files.isDirectory(directory.resolve("scripts"))) {
            try (var paths = Files.walk(directory.resolve("scripts"))) {
                paths.filter(Files::isRegularFile).map(path -> path.getFileName().toString())
                        .filter(name -> name.endsWith(".java"))
                        .map(name -> name.substring(0, name.length() - 5))
                        .filter(JavaSyntaxIdentifiers::isIdentifier)
                        .forEach(identifiers::add);
            }
        }
        return List.copyOf(identifiers);
    }

    private static boolean isIdentifier(String name) {
        return !name.isEmpty() && Character.isJavaIdentifierStart(name.codePointAt(0))
                && name.codePoints().allMatch(Character::isJavaIdentifierPart);
    }
}
