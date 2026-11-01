package tg_bot_editor;

import java.io.IOException;
import java.util.List;

/** Read-only access to live action bindings for project scripts. */
public final class ProjectActionBindings {
    public record Destination(long chatId, Integer threadId) { }

    private ProjectActionBindings() { }

    public static List<Destination> destinations(String actionId) throws IOException {
        if (ProjectEnvironment.directory() == null)
            throw new IllegalStateException("Abra o projeto antes de consultar vínculos de actions.");
        return new ActionBindings(ProjectEnvironment.directory()).destinations(actionId).stream()
                .map(value -> new Destination(value.chatId(), value.threadId())).toList();
    }

    public static List<String> at(long chatId, Integer threadId) throws IOException {
        return store().at(new ActionBindings.Destination(chatId, threadId));
    }

    public static boolean add(String actionId, long chatId, Integer threadId) throws IOException {
        return store().add(actionId, new ActionBindings.Destination(chatId, threadId));
    }

    public static boolean remove(String actionId, long chatId, Integer threadId) throws IOException {
        return store().remove(actionId, new ActionBindings.Destination(chatId, threadId));
    }

    private static ActionBindings store() throws IOException {
        if (ProjectEnvironment.directory() == null)
            throw new IllegalStateException("Abra o projeto antes de consultar vínculos de actions.");
        return new ActionBindings(ProjectEnvironment.directory());
    }
}
