package tg_bot_editor;

import java.util.Locale;
import java.util.Optional;

/** Routes one incoming update to the first matching visual flow. */
public final class FlowRouter {
    private FlowRouter() { }

    public static Optional<FlowProject.Flow> find(FlowProject project, String kind, String value) {
        if (value == null) return Optional.empty();
        String normalized = value.trim();
        return project.flows.stream().filter(flow -> {
            if (flow == null || flow.trigger == null || flow.pattern == null) return false;
            if (!flow.trigger.equalsIgnoreCase(kind)) return false;
            return switch (flow.trigger.toUpperCase(Locale.ROOT)) {
                case "COMMAND" -> normalized.equals(flow.pattern)
                        || normalized.startsWith(flow.pattern + " ")
                        || normalized.startsWith(flow.pattern + "@");
                case "TEXT", "CALLBACK", "PHOTO" -> normalized.equalsIgnoreCase(flow.pattern)
                        || flow.pattern.equals("*") || (flow.pattern.endsWith("*")
                        && normalized.regionMatches(true, 0, flow.pattern, 0, flow.pattern.length() - 1));
                default -> false;
            };
        }).sorted((a, b) -> Boolean.compare(a.pattern.equals("*"), b.pattern.equals("*")))
                .findFirst();
    }
}
