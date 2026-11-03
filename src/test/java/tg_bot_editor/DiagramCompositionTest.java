package tg_bot_editor;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class DiagramCompositionTest {
    @Test void usesDynamicButtonListAndHtml() {
        var buttons = List.of(BotButton.link("Loja", "https://example.org"), BotButton.callback("Abrir", "ads:open"));
        var reply = DiagramComposition.compose(Map.of("html", true, "items", List.of(
                Map.of("type", "TEXT", "value", "{{input.title}}"),
                Map.of("type", "BUTTONS", "value", "{{input.buttons}}"))),
                Map.of("title", "<b>Oferta</b>", "buttons", buttons), null);
        assertTrue(reply.html()); assertEquals("<b>Oferta</b>", reply.text()); assertEquals(buttons, reply.buttons());
        assertThrows(IllegalArgumentException.class, () -> DiagramComposition.compose(Map.of("items", List.of(
                Map.of("type", "BUTTONS", "value", "{{input.buttons}}"))), Map.of("buttons", List.of("bad")), null));
    }
    @Test void groupsButtonsIntoTelegramRowsAndKeepsLegacyButtonsSeparate() throws Exception {
        var parts = List.of(
                Map.of("type", "TEXT", "value", "Menu"),
                Map.of("type", "BUTTON", "value", "Um", "target", "a", "buttonKind", "CALLBACK", "row", "1"),
                Map.of("type", "BUTTON", "value", "Dois", "target", "https://example.org", "buttonKind", "URL", "row", "1"),
                Map.of("type", "BUTTON", "value", "Três", "target", "b", "buttonKind", "CALLBACK"));
        var reply = DiagramComposition.compose(Map.of("items", parts), null, null);
        var rows = new com.fasterxml.jackson.databind.ObjectMapper().readTree(BotRunner.replyMarkup(reply)).path("inline_keyboard");
        assertEquals(2, rows.size()); assertEquals(2, rows.get(0).size()); assertEquals(1, rows.get(1).size());
        assertEquals("a", rows.get(0).get(0).path("callback_data").asText());
        assertEquals("https://example.org", rows.get(0).get(1).path("url").asText());
        var legacy = BotReply.text("legacy").withButtons(List.of(BotButton.callback("A", "a"), BotButton.callback("B", "b")));
        assertEquals(2, new com.fasterxml.jackson.databind.ObjectMapper().readTree(BotRunner.replyMarkup(legacy)).path("inline_keyboard").size());
        assertThrows(IllegalArgumentException.class, () -> DiagramComposition.compose(Map.of("items", List.of(
                Map.of("type", "BUTTON", "value", "X", "target", "x", "buttonKind", "CALLBACK", "row", "0"))), null, null));
    }
    @Test void buildsPhotoTextLinksAndCallbackButtonsFromData() {
        var settings = Map.<String, Object>of("items", List.of(
                Map.of("type", "IMAGE", "value", "{{input.image}}"),
                Map.of("type", "TEXT", "value", "Olá {{input.name}}"),
                Map.of("type", "LINK", "value", "{{input.url}}"),
                Map.of("type", "BUTTON", "value", "Abrir", "target", "ads:edit:{{input.id}}", "buttonKind", "CALLBACK")));
        var reply = DiagramComposition.compose(settings,
                Map.of("image", "https://example.org/image", "name", "Ana", "url", "https://example.org", "id", "7"), null);
        assertEquals("https://example.org/image", reply.photoRef());
        assertEquals("Olá Ana\nhttps://example.org", reply.text());
        assertEquals("ads:edit:7", reply.buttons().getFirst().callbackData());
    }
    @Test void longPhotoCaptionRetainsLinkWithinTelegramLimit() {
        var settings = Map.<String, Object>of("items", List.of(Map.of("type", "IMAGE", "value", "image"),
                Map.of("type", "TEXT", "value", "título\n" + "😀".repeat(1100)),
                Map.of("type", "LINK", "value", "https://example.org")));
        String caption = DiagramComposition.compose(settings, null, null).text();
        assertTrue(caption.endsWith("\nhttps://example.org"));
        assertTrue(caption.codePointCount(0, caption.length()) <= 1024);
    }
    @Test void textCompositionOmitsEmptyPartsAndRejectsMissingImage() {
        var parts = List.of(Map.of("type", "TEXT", "value", "{{input.title}}"),
                Map.of("type", "TEXT", "value", "{{input.summary}}"), Map.of("type", "LINK", "value", "{{input.url}}"));
        var reply = DiagramComposition.compose(Map.of("items", parts), Map.of("title", "Título", "summary", "", "url", "https://example.org"), null);
        assertNull(reply.photoRef()); assertEquals("Título\nhttps://example.org", reply.text());
        assertThrows(IllegalArgumentException.class, () -> DiagramComposition.compose(
                Map.of("items", List.of(Map.of("type", "IMAGE", "value", ""))), Map.of(), null));
    }
}
