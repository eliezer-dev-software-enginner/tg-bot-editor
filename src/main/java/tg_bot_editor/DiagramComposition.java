package tg_bot_editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Monta uma mensagem a partir de elementos configurados no diagrama. */
public final class DiagramComposition {
    private static final Pattern REFERENCE = Pattern.compile("\\{\\{\\s*(input|output)(?:\\.([A-Za-z0-9_.-]+))?\\s*}}");
    private DiagramComposition() { }

    public static BotReply compose(Map<String, Object> settings, Object input, Object output) {
        Object configured = settings.get("items");
        if (!(configured instanceof List<?> items)) throw new IllegalArgumentException("Composição sem itens.");
        var text = new ArrayList<String>(); var buttons = new ArrayList<BotButton>();
        String image = null;
        int lastLink = -1;
        for (Object value : items) {
            if (!(value instanceof Map<?, ?> item)) throw new IllegalArgumentException("Item de composição inválido.");
            String type = String.valueOf(item.get("type"));
            String content = render(string(item, "value"), input, output);
            switch (type) {
                case "TEXT", "LINK" -> {
                    if (!content.isBlank()) {
                        text.add(content);
                        if ("LINK".equals(type)) lastLink = text.size() - 1;
                    }
                }
                case "IMAGE" -> {
                    if (image != null) throw new IllegalArgumentException("A composição aceita apenas uma imagem.");
                    if (content.isBlank()) throw new IllegalArgumentException("Informe a imagem da composição.");
                    image = content;
                }
                case "BUTTON" -> {
                    String target = render(string(item, "target"), input, output);
                    if (content.isBlank() || target.isBlank()) throw new IllegalArgumentException("Informe o texto e destino do botão.");
                    String kind = string(item, "buttonKind");
                    BotButton button;
                    if ("CALLBACK".equals(kind)) button = BotButton.callback(content, target);
                    else if ("URL".equals(kind)) button = BotButton.link(content, target);
                    else throw new IllegalArgumentException("Tipo de botão inválido: " + kind);
                    String row = string(item, "row").trim();
                    if (!row.isEmpty()) {
                        try { button = button.inRow(Integer.parseInt(row)); }
                        catch (NumberFormatException error) { throw new IllegalArgumentException("A linha do botão deve ser um número inteiro positivo.", error); }
                    }
                    buttons.add(button);
                }
                case "BUTTONS" -> {
                    String reference = string(item, "value");
                    if (!reference.startsWith("{{") || !reference.endsWith("}}"))
                        throw new IllegalArgumentException("Informe a referência da lista de botões.");
                    Object resolved = DiagramExecutor.value(reference, input, output);
                    if (!(resolved instanceof Iterable<?> values))
                        throw new IllegalArgumentException("A composição precisa receber uma lista de botões.");
                    for (Object valueButton : values) {
                        if (!(valueButton instanceof BotButton button))
                            throw new IllegalArgumentException("A lista contém um botão inválido.");
                        buttons.add(button);
                    }
                }
                default -> throw new IllegalArgumentException("Tipo de composição desconhecido: " + type);
            }
        }
        String caption = String.join("\n", text);
        if (image != null && caption.codePointCount(0, caption.length()) > 1024) {
            if (lastLink == text.size() - 1 && lastLink > 0) {
                String link = text.get(lastLink);
                int available = 1024 - link.codePointCount(0, link.length()) - 1;
                if (available < 1) throw new IllegalArgumentException("O link excede o limite da legenda.");
                String prefix = String.join("\n", text.subList(0, lastLink));
                caption = truncate(prefix, available) + "\n" + link;
            } else caption = truncate(caption, 1024);
        }
        BotReply reply = (image == null ? BotReply.text(caption) : BotReply.photo(image, caption)).withButtons(buttons);
        return Boolean.TRUE.equals(settings.get("html")) ? reply.asHtml() : reply;
    }

    private static String truncate(String text, int limit) {
        if (text.codePointCount(0, text.length()) <= limit) return text;
        return text.substring(0, text.offsetByCodePoints(0, limit - 1)) + "…";
    }

    private static String string(Map<?, ?> map, String key) {
        Object value = map.get(key); return value == null ? "" : String.valueOf(value);
    }

    private static String render(String template, Object input, Object output) {
        var matcher = REFERENCE.matcher(template); var result = new StringBuffer();
        while (matcher.find()) {
            Object value = "input".equals(matcher.group(1)) ? input : output;
            if (matcher.group(2) != null) for (String part : matcher.group(2).split("\\.")) {
                if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("Caminho de composição inválido: " + matcher.group());
                value = map.get(part);
            }
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
        }
        matcher.appendTail(result); return result.toString();
    }
}
