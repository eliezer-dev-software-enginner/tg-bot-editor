package tg_bot_editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Editable diagrams kept separately from the production bot.json format. */
public final class DiagramProject {
    public int version = 1;
    public List<Diagram> diagrams = new ArrayList<>();

    public static final class Diagram {
        public String id;
        public String name;
        public boolean enabled;
        public List<Node> nodes = new ArrayList<>();
        public List<Edge> edges = new ArrayList<>();
    }

    public static final class Node {
        public String id;
        public String name;
        /** TIMER, CALLBACK, CONVERTER, JAVA, GENERATOR, RESPONSE, JDBC, JSON_DB, SPLITTER. */
        public String type;
        public double x;
        public double y;
        public Map<String, Object> settings = Map.of();
        /** A splitter owns a diagram id rather than embedding a second graph. */
        public String subflowId;
    }

    public static final class Edge {
        public String id;
        public String from;
        public String to;
        /** ALWAYS, IF, ELSE. Conditions belong to the connection, not the node. */
        public String kind = "ALWAYS";
        public String name = "";
        public List<ConditionGroup> groups = new ArrayList<>();
    }

    public static final class ConditionGroup {
        /** ALL or ANY; nested groups allow visual parentheses. */
        public String operator = "ALL";
        public List<Comparison> comparisons = new ArrayList<>();
        public List<ConditionGroup> groups = new ArrayList<>();
    }

    public static final class Comparison {
        public String left;
        /** EQ, NE, LT, GT, LE, GE. */
        public String operator;
        public String right;
    }
}
