package tg_bot_editor;

/** One inline keyboard button. Exactly one of URL or callback data is set. */
public record BotButton(String text, String url, String callbackData) {
    public static BotButton link(String text, String url) { return new BotButton(text, url, null); }
    public static BotButton callback(String text, String data) { return new BotButton(text, null, data); }
}
