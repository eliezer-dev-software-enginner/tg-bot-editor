package tg_bot_editor;

import java.nio.file.Path;

/** A local document to send; cleanup runs after delivery or failure. */
public record BotDocument(Path path, String filename, String caption, Runnable cleanup) { }
