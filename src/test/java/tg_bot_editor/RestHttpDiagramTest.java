package tg_bot_editor;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RestHttpDiagramTest {
    @TempDir Path directory;

    @Test void requestUsesDynamicValuesHeadersAndBearerFromEnvironment() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api", exchange -> {
            String result = exchange.getRequestURI().getQuery() + "|" + exchange.getRequestHeaders().getFirst("X-Name")
                    + "|" + exchange.getRequestHeaders().getFirst("Authorization") + "|"
                    + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] bytes = result.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var stream = exchange.getResponseBody()) { stream.write(bytes); }
        });
        server.start();
        try {
            ProjectEnvironment.useDraft(directory, "API_TOKEN=example-token");
            var settings = Map.<String, Object>of("url", "http://127.0.0.1:" + server.getAddress().getPort() + "/api?id={{output.id}}",
                    "method", "POST", "body", "Olá {{input.name}}", "headers", "X-Name: {{input.name}}",
                    "bearerTokenEnv", "API_TOKEN");
            var result = RestHttp.execute(settings, Map.of("name", "Maria"), Map.of("id", 42));
            assertEquals("id=42|Maria|Bearer example-token|Olá Maria", result.get("body"));
            assertThrows(IllegalArgumentException.class, () -> RestHttp.execute(settings, Map.of(), Map.of("id", 42)));
            assertThrows(IllegalArgumentException.class, () -> RestHttp.execute(Map.of("url", settings.get("url"),
                    "bearerTokenEnv", "MISSING_TOKEN"), Map.of(), Map.of("id", 1)));
        } finally { server.stop(0); }
    }

    @Test void generatedProjectExecutesRestThenJavaAndPreservesResponse() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api", exchange -> {
            byte[] input = exchange.getRequestBody().readAllBytes();
            byte[] result = (exchange.getRequestMethod() + ":" + new String(input, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("X-Test", "yes");
            exchange.sendResponseHeaders(201, result.length);
            try (var output = exchange.getResponseBody()) { output.write(result); }
        });
        server.start();
        try {
            Path bot = ProjectWorkspace.create(directory, "rest-bot");
            Path project = bot.getParent();
            assertTrue(Files.readString(project.resolve("runtime/src/main/java/tg_bot_editor/RestHttp.java")).contains("class RestHttp"));
            try (var sources = Files.walk(project.resolve("runtime/src/main/java"))) {
                var javaFiles = sources.filter(path -> path.toString().endsWith(".java")).map(Path::toFile).toList();
                var compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
                try (var files = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
                    Path output = Files.createDirectories(project.resolve("runtime-classes"));
                    assertTrue(compiler.getTask(null, files, null,
                            java.util.List.of("-classpath", System.getProperty("java.class.path"), "-d", output.toString()),
                            null, files.getJavaFileObjectsFromFiles(javaFiles)).call());
                }
            }
            var graph = new DiagramProject();
            var diagram = new DiagramProject.Diagram(); diagram.id = "test"; diagram.name = "Test"; diagram.enabled = true;
            var start = node("start", "CALLBACK", Map.of("event", "COMMAND", "pattern", "/test"));
            var rest = node("rest", "REST_HTTP", Map.of("url", "http://127.0.0.1:" + server.getAddress().getPort() + "/api", "method", "POST", "body", "olá"));
            var action = node("java", "JAVA", Map.of("action", "var response = (java.util.Map<?, ?>) input; return response.get(\"status\") + \"/\" + response.get(\"body\");"));
            var reply = node("reply", "RESPONSE", Map.of("text", "{{output}}"));
            diagram.nodes.addAll(java.util.List.of(start, rest, action, reply));
            diagram.edges.addAll(java.util.List.of(edge("one", "start", "rest"), edge("two", "rest", "java"), edge("three", "java", "reply")));
            graph.diagrams.add(diagram); DiagramStore.write(project, graph);
            var restored = DiagramStore.read(project);
            var actions = ActionCompiler.compileProject(project, FlowStore.read(bot), restored);
            assertEquals("201/POST:olá", DiagramExecutor.run(restored, restored.diagrams.getFirst(), new BotContext(1, 1, "/test", "t"), actions));
            var result = RestHttp.execute(Map.of("url", rest.settings.get("url"), "method", "GET"));
            assertEquals(201, result.get("status"));
            assertEquals("GET:", result.get("body"));
            ProjectEnvironment.useDraft(project, "REST_ENDPOINT=" + rest.settings.get("url"));
            var byEnvironment = RestHttp.execute(Map.of("url", "https://invalid.example", "urlEnv", "REST_ENDPOINT"));
            assertEquals("GET:", byEnvironment.get("body"));
            assertThrows(IllegalArgumentException.class, () -> RestHttp.execute(Map.of("url", rest.settings.get("url"), "urlEnv", "BAD-NAME")));
            assertEquals(java.util.List.of("yes"), ((Map<?, ?>) result.get("headers")).get("x-test"));
            assertThrows(IllegalArgumentException.class, () -> RestHttp.execute(Map.of("url", "file:///etc/passwd")));
            assertThrows(IllegalArgumentException.class, () -> RestHttp.execute(Map.of("url", rest.settings.get("url"), "method", "TRACE")));
        } finally { server.stop(0); }
    }

    private static DiagramProject.Node node(String id, String type, Map<String, Object> settings) {
        var node = new DiagramProject.Node(); node.id = id; node.name = id; node.type = type; node.settings = settings; return node;
    }
    private static DiagramProject.Edge edge(String id, String from, String to) {
        var edge = new DiagramProject.Edge(); edge.id = id; edge.from = from; edge.to = to; return edge;
    }
}
