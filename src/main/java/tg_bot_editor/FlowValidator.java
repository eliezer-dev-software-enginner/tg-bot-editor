package tg_bot_editor;

import java.util.HashSet;
import java.util.Locale;

/** Fail before deployment when a flow is unreachable or incomplete. */
public final class FlowValidator {
    private FlowValidator() { }

    public static void validate(FlowProject project) {
        if (project == null || project.flows == null) throw new IllegalArgumentException("Projeto vazio");
        var identifiers = new HashSet<String>();
        var routes = new HashSet<String>();
        for (var flow : project.flows) {
            if (flow == null || flow.id == null || !identifiers.add(flow.id))
                throw new IllegalArgumentException("ID de fluxo ausente ou duplicado");
            if (flow.title == null || flow.title.isBlank() || flow.script == null || flow.script.isBlank())
                throw new IllegalArgumentException("Título e ação Java são obrigatórios: " + flow.id);
            if (flow.trigger == null || flow.pattern == null || flow.pattern.isBlank())
                throw new IllegalArgumentException("Gatilho incompleto: " + flow.title);
            String kind = flow.trigger.toUpperCase(Locale.ROOT);
            if (!java.util.Set.of("COMMAND", "TEXT", "CALLBACK", "PHOTO", "INTERVAL", "DAILY").contains(kind))
                throw new IllegalArgumentException("Gatilho desconhecido: " + flow.trigger);
            if (kind.equals("COMMAND") && !flow.pattern.startsWith("/"))
                throw new IllegalArgumentException("Comando deve começar com /: " + flow.title);
            if (kind.equals("INTERVAL") || kind.equals("DAILY")) {
                if (flow.targetChatEnv != null && !flow.targetChatEnv.isBlank()
                        && !flow.targetChatEnv.matches("[A-Z][A-Z0-9_]*"))
                    throw new IllegalArgumentException("Nome da variável de chat inválido: " + flow.title);
            }
            if (kind.equals("INTERVAL")) {
                try {
                    if (Long.parseLong(flow.pattern) < 1) throw new NumberFormatException();
                } catch (NumberFormatException error) {
                    throw new IllegalArgumentException("Intervalo em minutos inválido: " + flow.title);
                }
            } else if (kind.equals("DAILY")) {
                try {
                    for (String time : flow.pattern.split(",")) java.time.LocalTime.parse(time.trim());
                } catch (Exception error) {
                    throw new IllegalArgumentException("Horários inválidos (HH:mm): " + flow.title);
                }
            } else if (!routes.add(kind + ":" + flow.pattern.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Gatilho duplicado: " + flow.pattern);
            }
        }
    }
}
