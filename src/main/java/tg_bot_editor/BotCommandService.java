package tg_bot_editor;

/** Executes a project action by ID without deciding command syntax or responses. */
@FunctionalInterface
public interface BotCommandService {
    Object runAction(String actionId, BotContext context) throws Exception;
}
