package tg_bot_editor;

public record BotContext(long chatId, long userId, String text, String username, String photoRef) {
    public BotContext(long chatId, long userId, String text, String username) {
        this(chatId, userId, text, username, null);
    }
}
