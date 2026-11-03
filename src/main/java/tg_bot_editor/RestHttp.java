package tg_bot_editor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Requisição HTTP configurada por um nó de diagrama. */
public final class RestHttp {
    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD");
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private RestHttp() { }

    public static Map<String, Object> execute(Map<String, Object> settings) throws Exception {
        return execute(settings, null, null);
    }

    public static Map<String, Object> execute(Map<String, Object> settings, Object input, Object output) throws Exception {
        String method = String.valueOf(settings.getOrDefault("method", "GET")).trim().toUpperCase(Locale.ROOT);
        if (!METHODS.contains(method)) throw new IllegalArgumentException("Método HTTP não suportado: " + method);
        String url = String.valueOf(settings.getOrDefault("url", "")).trim();
        String variable = String.valueOf(settings.getOrDefault("urlEnv", "")).trim();
        if (!variable.isEmpty()) {
            if (!variable.matches("[A-Za-z_][A-Za-z0-9_]*"))
                throw new IllegalArgumentException("Nome inválido para variável de ambiente da URL.");
            String configured = ProjectEnvironment.get(variable);
            if (configured != null && !configured.isBlank()) url = configured.trim();
        }
        url = render(url, input, output);
        URI uri;
        try { uri = URI.create(url); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("URL HTTP inválida", e); }
        if (uri.getHost() == null || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())))
            throw new IllegalArgumentException("Informe uma URL HTTP ou HTTPS válida.");
        String body = render(String.valueOf(settings.getOrDefault("body", "")), input, output);
        if (Set.of("GET", "HEAD").contains(method) && !body.isBlank())
            throw new IllegalArgumentException(method + " não aceita corpo neste componente.");
        var publisher = body.isEmpty() ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
        var builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).method(method, publisher);
        String headers = String.valueOf(settings.getOrDefault("headers", ""));
        String tokenEnv = String.valueOf(settings.getOrDefault("bearerTokenEnv", "")).trim();
        for (String line : headers.split("\\R")) {
            if (line.isBlank()) continue;
            int separator = line.indexOf(':');
            if (separator <= 0) throw new IllegalArgumentException("Cabeçalho HTTP deve usar Nome: valor.");
            String name = line.substring(0, separator).trim();
            if (!tokenEnv.isEmpty() && name.equalsIgnoreCase("Authorization"))
                throw new IllegalArgumentException("Configure Authorization apenas uma vez.");
            builder.header(name, render(line.substring(separator + 1).trim(), input, output));
        }
        if (!tokenEnv.isEmpty()) {
            if (!tokenEnv.matches("[A-Za-z_][A-Za-z0-9_]*"))
                throw new IllegalArgumentException("Nome inválido para variável do token HTTP.");
            String token = ProjectEnvironment.get(tokenEnv);
            if (token == null || token.isBlank()) throw new IllegalArgumentException("Variável do token HTTP não definida: " + tokenEnv);
            builder.header("Authorization", "Bearer " + token.trim());
        }
        var request = builder.build();
        var response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return Map.of("status", response.statusCode(), "body", response.body(), "headers", response.headers().map());
    }

    private static String render(String text, Object input, Object output) {
        var matcher = java.util.regex.Pattern.compile("\\{\\{(input|output)(?:\\.[A-Za-z0-9_]+)*\\}\\}").matcher(text);
        var rendered = new StringBuffer();
        while (matcher.find()) {
            Object value = DiagramExecutor.value(matcher.group(), input, output);
            if (value == null) throw new IllegalArgumentException("Referência HTTP não encontrada: " + matcher.group());
            matcher.appendReplacement(rendered, java.util.regex.Matcher.quoteReplacement(String.valueOf(value)));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }
}
