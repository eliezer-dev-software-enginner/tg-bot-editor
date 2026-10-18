package tg_bot_editor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** One long-polling process and one token for every enabled flow. */
public final class BotRunner {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String token;
    private final FlowProject project;
    private final Map<String, BotAction> actions = new HashMap<>();
    private long offset;

    private BotRunner(String token, Path projectDirectory, FlowProject project) throws Exception {
        this.token = token;
        this.project = project;
        FlowValidator.validate(project);
        actions.putAll(ActionCompiler.compileProject(projectDirectory, project));
    }

    public static void main(String[] args) throws Exception {
        boolean check = args.length == 2 && "--check".equals(args[0]);
        if ((!check && args.length != 1) || (args.length == 2 && !check))
            throw new IllegalArgumentException("Uso: BotRunner [--check] <arquivo-do-projeto.json>");
        Path projectFile = Path.of(args[check ? 1 : 0]).toAbsolutePath().normalize();
        var project = FlowStore.read(projectFile);
        if (check) {
            FlowValidator.validate(project);
            int count = ActionCompiler.compileProject(projectFile.getParent(), project).size();
            System.out.println("Projeto válido: " + count + " fluxos Java compilados.");
            return;
        }
        ProjectEnvironment.activate(projectFile.getParent());
        String token = ProjectEnvironment.get("TG_BOT_TOKEN");
        if (token == null || token.isBlank()) throw new IllegalStateException("Defina TG_BOT_TOKEN fora do projeto");
        var runner = new BotRunner(token, projectFile.getParent(), project);
        runner.startSchedules();
        runner.poll();
    }

    private void startSchedules() {
        if (project.flows.stream().noneMatch(flow -> scheduled(flow.trigger))) return;
        var timer = Executors.newSingleThreadScheduledExecutor();
        for (var flow : project.flows) {
            if (!scheduled(flow.trigger)) continue;
            long chat = 0;
            if (flow.targetChatEnv != null && !flow.targetChatEnv.isBlank()) {
                String target = ProjectEnvironment.get(flow.targetChatEnv);
                if (target == null || target.isBlank())
                    throw new IllegalStateException("Defina " + flow.targetChatEnv + " para " + flow.title);
                try { chat = Long.parseLong(target); }
                catch (NumberFormatException error) { throw new IllegalArgumentException(flow.targetChatEnv + " deve ser numérico", error); }
            }
            final long targetChat = chat;
            if ("INTERVAL".equalsIgnoreCase(flow.trigger)) {
                long minutes = Long.parseLong(flow.pattern);
                timer.scheduleWithFixedDelay(() -> runScheduled(flow, targetChat), 0, minutes, TimeUnit.MINUTES);
            } else {
                for (String value : flow.pattern.split(",")) scheduleDaily(timer, flow, targetChat, LocalTime.parse(value.trim()));
            }
        }
    }

    private static boolean scheduled(String trigger) {
        return "INTERVAL".equalsIgnoreCase(trigger) || "DAILY".equalsIgnoreCase(trigger);
    }

    private void scheduleDaily(java.util.concurrent.ScheduledExecutorService timer,
                               FlowProject.Flow flow, long chat, LocalTime time) {
        var now = ZonedDateTime.now();
        var next = now.toLocalDate().atTime(time).atZone(now.getZone());
        if (!next.isAfter(now)) next = next.plusDays(1);
        long delay = Math.max(0, Duration.between(now, next).toSeconds());
        timer.schedule(() -> {
            try { runScheduled(flow, chat); }
            finally { scheduleDaily(timer, flow, chat, time); }
        }, delay, TimeUnit.SECONDS);
    }

    private void runScheduled(FlowProject.Flow flow, long chat) {
        try {
            Object reply = actions.get(flow.id).run(new BotContext(chat, 0, "", "scheduler"));
            send(chat, reply);
        } catch (Exception error) {
            System.err.println("Ação agendada falhou: " + flow.title + ": " + safeMessage(error));
        }
    }

    private void poll() throws Exception {
        System.out.println("Projeto " + project.name + " iniciado com " + project.flows.size() + " fluxo(s).");
        while (!Thread.currentThread().isInterrupted()) {
            try {
                JsonNode response = call("getUpdates", "offset=" + offset + "&timeout=30", 40);
                if (!response.path("ok").asBoolean()) throw new IllegalStateException("Telegram recusou getUpdates");
                for (var update : response.path("result")) {
                    dispatch(update);
                    offset = Math.max(offset, update.path("update_id").asLong() + 1);
                }
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
            } catch (Exception error) {
                System.err.println("Falha ao receber atualizações: " + safeMessage(error));
                Thread.sleep(3_000);
            }
        }
    }

    private void dispatch(JsonNode update) throws Exception {
        JsonNode callback = update.path("callback_query");
        JsonNode message = callback.isMissingNode() ? update.path("message") : callback.path("message");
        if (message.isMissingNode()) return;
        long chat = message.path("chat").path("id").asLong();
        JsonNode sender = callback.isMissingNode() ? message.path("from") : callback.path("from");
        String value = callback.isMissingNode() ? message.path("text").asText("") : callback.path("data").asText("");
        JsonNode photos = message.path("photo");
        String photoRef = photos.isArray() && !photos.isEmpty()
                ? photos.get(photos.size() - 1).path("file_id").asText("") : null;
        String kind = callback.isMissingNode() ? (photoRef != null ? "PHOTO"
                : value.startsWith("/") ? "COMMAND" : "TEXT") : "CALLBACK";
        var match = FlowRouter.find(project, kind, value);
        if (match.isEmpty()) {
            answerCallback(callback);
            return;
        }
        var context = new BotContext(chat, sender.path("id").asLong(), value,
                sender.path("username").asText(""), photoRef);
        Object reply;
        try {
            reply = actions.get(match.get().id).run(context);
        } catch (Exception error) {
            System.err.println("Ação falhou: " + match.get().title + ": " + safeMessage(error));
            answerCallback(callback);
            return;
        }
        send(chat, reply);
        answerCallback(callback);
    }

    private void answerCallback(JsonNode callback) {
        if (callback.isMissingNode()) return;
        try {
            call("answerCallbackQuery", "callback_query_id=" +
                    URLEncoder.encode(callback.path("id").asText(), StandardCharsets.UTF_8), 10);
        } catch (Exception error) {
            System.err.println("Falha ao confirmar callback Telegram");
        }
    }

    private void send(long chat, Object value) throws Exception {
        if (value == null) throw new IllegalStateException("A ação retornou null");
        if (value instanceof BotDelivery delivery) {
            send(delivery.chatId(), delivery.reply());
            return;
        }
        if (value instanceof Iterable<?> batch) {
            for (Object item : batch) send(chat, item);
            return;
        }
        BotReply reply = value instanceof BotReply rich ? rich : BotReply.text(value.toString());
        String markup = replyMarkup(reply);
        String options = (reply.html() ? "&parse_mode=HTML" : "")
                + (markup.isEmpty() ? "" : "&reply_markup=" + URLEncoder.encode(markup, StandardCharsets.UTF_8));
        if (reply.photoRef() != null && !reply.photoRef().isBlank()) {
            Path local = reply.photoRef().startsWith("/") ? Path.of(reply.photoRef()) : null;
            JsonNode result;
            if (local != null && !Files.isRegularFile(local))
                throw new IllegalStateException("Arquivo da foto não encontrado: " + local);
            if (local != null) {
                result = sendLocalPhoto(chat, reply, local, markup);
            } else {
                String data = "chat_id=" + chat + "&photo=" + URLEncoder.encode(reply.photoRef(), StandardCharsets.UTF_8)
                        + "&caption=" + URLEncoder.encode(reply.text() == null ? "" : reply.text(), StandardCharsets.UTF_8)
                        + options;
                result = call("sendPhoto", data, 40);
            }
            if (!result.path("ok").asBoolean())
                throw new IllegalStateException("Telegram recusou sendPhoto");
        } else if (reply.text() != null && !reply.text().isBlank()) {
            String data = "chat_id=" + chat + "&text=" + URLEncoder.encode(reply.text(), StandardCharsets.UTF_8) + options;
            if (!call("sendMessage", data, 20).path("ok").asBoolean())
                throw new IllegalStateException("Telegram recusou sendMessage");
        } else {
            throw new IllegalStateException("A ação retornou uma resposta vazia");
        }
        if (reply.afterSend() != null) reply.afterSend().run();
    }

    private static String replyMarkup(BotReply reply) throws Exception {
        if (reply.buttons() == null || reply.buttons().isEmpty()) return "";
        var keyboard = JSON.createArrayNode();
        for (var button : reply.buttons()) {
            var row = keyboard.addArray();
            var item = row.addObject().put("text", button.text());
            if (button.url() != null) item.put("url", button.url());
            else if (button.callbackData() != null) item.put("callback_data", button.callbackData());
            else throw new IllegalArgumentException("Botão sem URL nem callback");
        }
        return JSON.createObjectNode().set("inline_keyboard", keyboard).toString();
    }

    private JsonNode sendLocalPhoto(long chat, BotReply reply, Path file, String markup) throws Exception {
        String boundary = "tg-bot-" + java.util.UUID.randomUUID();
        var output = new java.io.ByteArrayOutputStream();
        writePart(output, boundary, "chat_id", Long.toString(chat));
        writePart(output, boundary, "caption", reply.text() == null ? "" : reply.text());
        if (reply.html()) writePart(output, boundary, "parse_mode", "HTML");
        if (!markup.isEmpty()) writePart(output, boundary, "reply_markup", markup);
        output.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"photo\"; filename=\"photo.jpg\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        Files.copy(file, output);
        output.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return request("sendPhoto", java.net.http.HttpRequest.BodyPublishers.ofByteArray(output.toByteArray()),
                "multipart/form-data; boundary=" + boundary, 60);
    }

    private static void writePart(java.io.ByteArrayOutputStream output, String boundary, String name, String value)
            throws java.io.IOException {
        output.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name
                + "\"\r\n\r\n" + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode call(String method, String data, int timeoutSeconds) throws Exception {
        return request(method, HttpRequest.BodyPublishers.ofString(data),
                "application/x-www-form-urlencoded", timeoutSeconds);
    }

    private JsonNode request(String method, HttpRequest.BodyPublisher body, String contentType, int timeoutSeconds)
            throws Exception {
        var request = HttpRequest.newBuilder(URI.create("https://api.telegram.org/bot" + token + "/" + method))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", contentType)
                .POST(body).build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("Telegram HTTP " + response.statusCode());
        return JSON.readTree(response.body());
    }

    private String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null ? error.getClass().getSimpleName() : message.replace(token, "[token redacted]");
    }
}
