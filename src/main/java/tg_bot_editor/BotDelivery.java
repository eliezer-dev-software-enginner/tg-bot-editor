package tg_bot_editor;

/** A reply explicitly addressed to a channel other than the incoming chat. */
public record BotDelivery(long chatId, BotReply reply) { }
