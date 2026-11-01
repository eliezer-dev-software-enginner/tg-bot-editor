package tg_bot_editor;

import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Per-project destinations, kept outside bot.json so they can change while the runner is live. */
final class ActionBindings {
    record Destination(long chatId, Integer threadId) { }
    public static final class Entry {
        public String actionId;
        public long chatId;
        public Integer threadId;
        public Entry() { }
        Entry(String actionId, Destination destination) {
            this.actionId = actionId;
            this.chatId = destination.chatId();
            this.threadId = destination.threadId();
        }
        Destination destination() { return new Destination(chatId, threadId); }
    }

    private static final TypeReference<List<Entry>> TYPE = new TypeReference<>() { };
    private final List<Entry> entries = new ArrayList<>();

    ActionBindings(Path projectDirectory) throws IOException {
        Path legacy = projectDirectory.resolve("data/action-bindings.json");
        {
            for (Entry entry : BotDatabase.read("actionBindings", legacy, TYPE, ArrayList::new)) {
                if (entry.actionId == null || entry.actionId.isBlank() || entry.chatId == 0
                        || (entry.threadId != null && entry.threadId <= 0))
                    throw new IOException("Vínculo de action inválido no banco do bot");
                entries.add(entry);
            }
        }
    }

    synchronized List<Destination> destinations(String actionId) {
        return entries.stream().filter(entry -> entry.actionId.equals(actionId))
                .map(Entry::destination).toList();
    }

    synchronized List<String> at(Destination destination) {
        return entries.stream().filter(entry -> entry.destination().equals(destination))
                .map(entry -> entry.actionId).toList();
    }

    synchronized boolean add(String actionId, Destination destination) throws IOException {
        if (destinations(actionId).contains(destination)) return false;
        entries.add(new Entry(actionId, destination));
        save();
        return true;
    }

    synchronized boolean remove(String actionId, Destination destination) throws IOException {
        boolean changed = entries.removeIf(entry -> entry.actionId.equals(actionId)
                && entry.destination().equals(destination));
        if (changed) save();
        return changed;
    }

    private void save() throws IOException {
        BotDatabase.write("actionBindings", entries);
    }
}
