package tg_bot_editor;

/** Runtime capabilities available to project-owned management command flows. */
@FunctionalInterface
public interface BotCommandService {
    Object execute(BotContext context) throws Exception;
}
