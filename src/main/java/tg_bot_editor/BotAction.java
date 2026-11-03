package tg_bot_editor;

/** Return a String or BotReply; never use side effects as the sole result. */
@FunctionalInterface
public interface BotAction {
    Object run(BotContext context) throws Exception;

    /** Input is supplied by the previous diagram component, including each Splitter item. */
    default Object run(BotContext context, Object input) throws Exception { return run(context); }

    /** Index is zero-based within a Splitter, or -1 outside a subflow. */
    default Object run(BotContext context, Object input, int index) throws Exception { return run(context, input); }

    /** O item original continua acessível depois de etapas intermediárias do subfluxo. */
    default Object run(BotContext context, Object input, int index, Object item) throws Exception { return run(context, input, index); }
}
