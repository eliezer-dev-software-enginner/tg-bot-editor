package tg_bot_editor;

import java.util.ArrayList;
import java.util.List;

/** Saved, visual definition of one Telegram bot. No credentials belong here. */
public final class FlowProject {
    public int version = 1;
    public String name = "Meu bot";
    public List<Flow> flows = new ArrayList<>();

    public static final class Flow {
        public String id;
        public String title;
        public String trigger = "COMMAND";
        public String pattern = "/start";
        public String script = "return \"Olá!\";";
        public String targetChatEnv = "TG_BOT_TARGET_CHAT";
        public double x = 80;
        public double y = 80;

        public Flow() { }

        public Flow(String id, String title, String pattern, double x, double y) {
            this.id = id;
            this.title = title;
            this.pattern = pattern;
            this.x = x;
            this.y = y;
        }
    }
}
