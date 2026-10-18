package tg_bot_editor;

/** Explicit response when a flow needs more than plain text. */
public record BotReply(String text, String photoRef, java.util.List<BotButton> buttons,
                       boolean html, Runnable afterSend) {
    public static BotReply text(String text) { return new BotReply(text, null, java.util.List.of(), false, null); }
    public static BotReply photo(String photoRef, String caption) {
        return new BotReply(caption, photoRef, java.util.List.of(), false, null);
    }
    public BotReply withButtons(java.util.List<BotButton> value) {
        return new BotReply(text, photoRef, java.util.List.copyOf(value), html, afterSend);
    }
    public BotReply asHtml() { return new BotReply(text, photoRef, buttons, true, afterSend); }
    public BotReply afterSend(Runnable callback) { return new BotReply(text, photoRef, buttons, html, callback); }
}
