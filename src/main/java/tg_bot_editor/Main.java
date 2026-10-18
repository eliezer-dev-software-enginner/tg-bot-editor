package tg_bot_editor;

import megalodonte.application.MegalodonteApp;
import megalodonte.base.theme.ThemeManager;

public final class Main {
    public static void main(String[] args) throws Exception {
        if (java.util.Arrays.asList(args).contains("--self-test")) {
            try (var template = Main.class.getResourceAsStream("/templates/arsenal-soup.json")) {
                var project = FlowStore.read(template);
                FlowValidator.validate(project);
                for (var flow : project.flows) ActionCompiler.compile(flow.script);
                System.out.println("SELF-TEST OK: " + project.flows.size() + " flows compiled");
            }
            return;
        }
        ThemeManager.setTheme(new EditorTheme());
        MegalodonteApp.appName("TG-bot Editor");
        MegalodonteApp.appIcon("/icon.png");
        MegalodonteApp.run(context -> context.useView(new HomeScreen(), 1240, 640));
    }
}
