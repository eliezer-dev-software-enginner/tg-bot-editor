package tg_bot_editor;

/** One inline keyboard button. Exactly one of URL or callback data is set. */
public record BotButton(String text, String url, String callbackData, int row) {
    public BotButton(String text, String url, String callbackData) { this(text, url, callbackData, 0); }
    public static BotButton link(String text, String url) { return new BotButton(text, url, null); }
    public static BotButton callback(String text, String data) { return new BotButton(text, null, data); }
    public BotButton inRow(int value) {
        if (value < 1) throw new IllegalArgumentException("A linha do botão deve ser maior que zero.");
        return new BotButton(text, url, callbackData, value);
    }
}
