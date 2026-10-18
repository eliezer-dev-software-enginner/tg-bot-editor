package tg_bot_editor;

public record BotContext(long chatId, long userId, String text, String username) { }
