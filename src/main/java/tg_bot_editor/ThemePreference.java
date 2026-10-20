package tg_bot_editor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** The editor's appearance is local to this computer, not part of a bot project. */
public final class ThemePreference {
    private ThemePreference() { }

    private static Path file() { return RecentProjects.settingsDirectory().resolve("theme.txt"); }

    public static boolean isDark() {
        try { return Files.readString(file()).trim().equals("dark"); }
        catch (IOException ignored) { return false; }
    }

    public static void save(boolean dark) throws IOException {
        Path target = file();
        Files.createDirectories(target.getParent());
        Files.writeString(target, dark ? "dark\n" : "light\n");
    }
}
