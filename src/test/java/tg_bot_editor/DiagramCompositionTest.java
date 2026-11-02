package tg_bot_editor;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class DiagramCompositionTest {
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
