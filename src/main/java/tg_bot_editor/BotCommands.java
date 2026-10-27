package tg_bot_editor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

/** Commands exposed by the runner and by the project's COMMAND flows. */
public final class BotCommands {
    public record Entry(String name, String description) { }

    private static final List<Entry> BUILT_INS = List.of(
            new Entry("/whoami", "Mostra seu ID de usuário e do chat"),
            new Entry("/actions", "Lista as actions disponíveis"),
            new Entry("/bindings", "Lista as actions vinculadas aqui"),
            new Entry("/bindaction", "Vincula uma action a este chat ou tópico"),
            new Entry("/unbindaction", "Remove uma action deste chat ou tópico"),
            new Entry("/runaction", "Executa uma action neste chat ou tópico"),
            new Entry("/youtube_add", "Inscreve um canal do YouTube"),
            new Entry("/youtube_remove", "Remove a inscrição de um canal"),
            new Entry("/youtube_list", "Lista os canais inscritos"),
            new Entry("/youtube_check", "Verifica novos vídeos agora"));

    private BotCommands() { }

    public static List<Entry> list(FlowProject project) {
        var commands = new LinkedHashMap<String, Entry>();
        for (Entry entry : BUILT_INS) commands.put(entry.name(), entry);
        for (var flow : project.flows) {
            if (flow == null || !"COMMAND".equalsIgnoreCase(flow.trigger) || flow.pattern == null) continue;
            String name = flow.pattern.strip();
            if (!name.matches("/[a-z0-9_]{1,32}")) continue;
            String description = flow.description == null || flow.description.isBlank() ? flow.title : flow.description;
            if (description == null || description.isBlank()) description = "Sem descrição";
            description = description.strip().replaceAll("\\R+", " ");
            if (description.length() > 256) description = description.substring(0, 256);
            commands.putIfAbsent(name, new Entry(name, description));
        }
        var result = new ArrayList<>(commands.values());
        result.sort(Comparator.comparing(Entry::name));
        return List.copyOf(result);
    }
}
