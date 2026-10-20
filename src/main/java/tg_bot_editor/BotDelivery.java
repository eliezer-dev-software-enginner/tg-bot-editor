package tg_bot_editor;

/** A reply explicitly addressed to a channel other than the incoming chat. */
public record BotDelivery(long chatId, Integer threadId, BotReply reply) {
    public BotDelivery(long chatId, BotReply reply) { this(chatId, null, reply); }
}
