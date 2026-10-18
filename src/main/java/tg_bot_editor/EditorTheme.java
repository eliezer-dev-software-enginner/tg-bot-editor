package tg_bot_editor;

import megalodonte.base.theme.ThemeBorder;
import megalodonte.base.theme.ThemeColors;
import megalodonte.theme.DefaultTheme;

/** Default Megalodonte palette with visible field and card boundaries. */
public final class EditorTheme extends DefaultTheme {
    @Override public ThemeColors colors() {
        var base = super.colors();
        return new ThemeColors(base.background(), base.surface(), base.primary(), base.secondary(),
                base.textPrimary(), base.textSecondary(), "#b7c4d2", base.placeholder(),
                base.selection(), base.focusRing(), base.hover(), base.success(), base.warning(), base.danger());
    }

    @Override public ThemeBorder border() { return new ThemeBorder(1, 4, 6, 8); }
}
