package tg_bot_editor;

public record BotContext(long chatId, long userId, String text, String username,
                         String photoRef, Integer threadId) {
    public BotContext(long chatId, long userId, String text, String username) {
        this(chatId, userId, text, username, null, null);
    }
    public BotContext(long chatId, long userId, String text, String username, String photoRef) {
        this(chatId, userId, text, username, photoRef, null);
    }
}
