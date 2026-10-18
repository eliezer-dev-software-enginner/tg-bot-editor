package tg_bot_editor;

/** Return a String or BotReply; never use side effects as the sole result. */
@FunctionalInterface
public interface BotAction {
    Object run(BotContext context) throws Exception;
}
