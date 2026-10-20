package tg_bot_editor;

import megalodonte.base.theme.ThemeBorder;
import megalodonte.base.theme.ThemeColors;
import megalodonte.theme.DefaultTheme;

/** Default Megalodonte palette with visible field and card boundaries. */
public final class EditorTheme extends DefaultTheme {
    private final boolean dark;

    public EditorTheme(boolean dark) { this.dark = dark; }

    @Override public ThemeColors colors() {
        if (dark) return new ThemeColors(
                "#171d26", "#242c38", "#568dff", "#7188a6",
                "#edf4fd", "#adbbcc", "#526174", "#92a2b6",
                "#314968", "#89b8ff", "#303b4a",
                "#168a72", "#aa751f", "#c94b58");
        var base = super.colors();
        return new ThemeColors(base.background(), base.surface(), base.primary(), base.secondary(),
                base.textPrimary(), base.textSecondary(), "#b7c4d2", base.placeholder(),
                base.selection(), base.focusRing(), base.hover(), base.success(), base.warning(), base.danger());
    }

    @Override public ThemeBorder border() { return new ThemeBorder(1, 4, 6, 8); }
}
