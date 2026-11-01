package tg_bot_editor;

import java.nio.file.Path;

/** Downloads a Telegram file to a temporary path owned by the caller. */
@FunctionalInterface
public interface BotFileService {
    Path download(String fileId) throws Exception;
}
