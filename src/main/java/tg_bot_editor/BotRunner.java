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
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** One long-polling process and one token for every enabled flow. */
public final class BotRunner {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String token;
    private final FlowProject project;
    private final ActionBindings bindings;
    private final YouTubeSubscriptions youtube;
    private final long adminId;
    private final Map<String, BotAction> actions = new HashMap<>();
    private final Map<String, Instant> lastStartupDelivery = new HashMap<>();
    private ScheduledExecutorService schedules;
    private long offset;

    BotRunner(String token, Path projectDirectory, FlowProject project) throws Exception {
        this.token = token;
        this.project = project;
        FlowValidator.validate(project);
        actions.putAll(ActionCompiler.compileProject(projectDirectory, project));
        bindings = new ActionBindings(projectDirectory);
        youtube = new YouTubeSubscriptions(projectDirectory);
        String configuredAdmin = ProjectEnvironment.get("TG_BOT_ADMIN_ID");
        adminId = configuredAdmin == null || configuredAdmin.isBlank() ? 0 : Long.parseLong(configuredAdmin);
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
        try {
            runner.startSchedules();
            runner.poll();
        } finally {
            if (runner.schedules != null) runner.schedules.shutdownNow();
        }
    }

    private void startSchedules() {
        schedules = Executors.newSingleThreadScheduledExecutor();
        schedules.scheduleWithFixedDelay(this::checkYouTube, 1, 10, TimeUnit.MINUTES);
        for (var flow : project.flows) {
            if (!scheduled(flow.trigger)) continue;
            long chat = 0;
            if (flow.targetChatEnv != null && !flow.targetChatEnv.isBlank()) {
                String target = ProjectEnvironment.get(flow.targetChatEnv);
                if (target != null && !target.isBlank()) {
                    try { chat = Long.parseLong(target); }
                    catch (NumberFormatException error) { throw new IllegalArgumentException(flow.targetChatEnv + " deve ser numérico", error); }
                }
            }
            final long targetChat = chat;
            if ("INTERVAL".equalsIgnoreCase(flow.trigger)) {
                long minutes = Long.parseLong(flow.pattern);
                schedules.scheduleWithFixedDelay(() -> runScheduled(flow, targetChat), 0, minutes, TimeUnit.MINUTES);
            } else {
                if (flow.runOnStart) schedules.execute(() -> runScheduled(flow, targetChat, true));
                for (String value : flow.pattern.split(",")) scheduleDaily(schedules, flow, targetChat, LocalTime.parse(value.trim()));
            }
        }
    }

    private static boolean scheduled(String trigger) {
        return "INTERVAL".equalsIgnoreCase(trigger) || "DAILY".equalsIgnoreCase(trigger);
    }

    private void checkYouTube() {
        try {
            youtube.check(bindings.destinations(YouTubeSubscriptions.ACTION_ID),
                    (destination, message) -> send(destination.chatId(), destination.threadId(), message));
        } catch (Exception error) {
            System.err.println("Falha ao verificar vídeos do YouTube: " + safeMessage(error));
        }
    }

    static boolean shouldStopPolling(int status) {
        return status >= 400 && status < 500 && status != 429;
    }

    private void scheduleDaily(java.util.concurrent.ScheduledExecutorService timer,
                               FlowProject.Flow flow, long chat, LocalTime time) {
        long delay = delayUntilDailyNanos(ZonedDateTime.now(), time);
        timer.schedule(() -> {
            try {
                Instant startup = lastStartupDelivery.get(flow.id);
                if (!shouldSkipDailyAfterStartup(startup, Instant.now()))
                    runScheduled(flow, chat);
            }
            finally { if (!timer.isShutdown()) scheduleDaily(timer, flow, chat, time); }
        }, delay, TimeUnit.NANOSECONDS);
    }

    static long delayUntilDailyNanos(ZonedDateTime now, LocalTime time) {
        var next = now.toLocalDate().atTime(time).atZone(now.getZone());
        if (!next.isAfter(now)) next = next.plusDays(1);
        return Duration.between(now, next).toNanos();
    }

    static boolean shouldSkipDailyAfterStartup(Instant startup, Instant now) {
        return startup != null && !now.isBefore(startup)
                && Duration.between(startup, now).compareTo(Duration.ofMinutes(30)) < 0;
    }

    private void runScheduled(FlowProject.Flow flow, long chat) {
        runScheduled(flow, chat, false);
    }

    private void runScheduled(FlowProject.Flow flow, long chat, boolean startup) {
        try {
            var destinations = bindings.destinations(flow.id);
            if (destinations.isEmpty() && chat != 0)
                destinations = java.util.List.of(new ActionBindings.Destination(chat, null));
            boolean delivered = false;
            for (var destination : destinations) {
                try {
                    Object reply = actions.get(flow.id).run(new BotContext(destination.chatId(), 0, "", "scheduler",
                            null, destination.threadId()));
                    send(destination.chatId(), destination.threadId(), reply);
                    delivered |= !(reply instanceof java.util.Collection<?> batch && batch.isEmpty());
                } catch (Exception error) {
                    System.err.println("Ação agendada falhou em " + destination.chatId() + ": " + safeMessage(error));
                }
            }
            if (startup && delivered) lastStartupDelivery.put(flow.id, Instant.now());
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
                    try {
                        dispatch(update);
                    } catch (InterruptedException stopped) {
                        throw stopped;
                    } catch (Exception error) {
                        System.err.println("Falha ao processar atualização " + update.path("update_id").asLong()
                                + ": " + safeMessage(error));
                    } finally {
                        offset = Math.max(offset, update.path("update_id").asLong() + 1);
                    }
                }
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
            } catch (TelegramApiException error) {
                System.err.println("Falha na API do Telegram: " + safeMessage(error));
                if (shouldStopPolling(error.status)) return;
                Thread.sleep(3_000);
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
        Integer threadId = message.hasNonNull("message_thread_id") ? message.path("message_thread_id").asInt() : null;
        var context = new BotContext(chat, sender.path("id").asLong(), value,
                sender.path("username").asText(""), photoRef, threadId);
        if ("COMMAND".equals(kind) && handleActionCommand(context)) return;
        var match = FlowRouter.find(project, kind, value);
        if (match.isEmpty()) {
            answerCallback(callback);
            return;
        }
        Object reply;
        try {
            reply = actions.get(match.get().id).run(context);
        } catch (Exception error) {
            System.err.println("Ação falhou: " + match.get().title + ": " + safeMessage(error));
            answerCallback(callback);
            return;
        }
        send(chat, context.threadId(), reply);
        answerCallback(callback);
    }

    private boolean handleActionCommand(BotContext context) throws Exception {
        String[] parts = context.text().trim().split("\\s+", 2);
        String command = parts[0].split("@", 2)[0].toLowerCase(java.util.Locale.ROOT);
        if (command.equals("/whoami")) {
            send(context.chatId(), context.threadId(), "Seu ID: " + context.userId());
            return true;
        }
        if (!java.util.Set.of("/actions", "/bindings", "/bindaction", "/unbindaction", "/runaction",
                "/youtube_add", "/youtube_remove", "/youtube_list", "/youtube_check")
                .contains(command)) return false;
        String argument = parts.length == 2 ? parts[1].trim() : "";
        String response;
        if (adminId == 0) response = "Configure TG_BOT_ADMIN_ID no .env do projeto para administrar actions.";
        else if (context.userId() != adminId) response = "Apenas o administrador pode administrar actions.";
        else {
            try { response = actionCommand(command, argument, context); }
            catch (Exception error) {
                System.err.println("Falha no comando de action: " + safeMessage(error));
                response = "Falha ao executar action: " + safeMessage(error);
            }
        }
        send(context.chatId(), context.threadId(), response);
        return true;
    }

    String actionCommand(String command, String argument, BotContext context) throws Exception {
        var destination = new ActionBindings.Destination(context.chatId(), context.threadId());
        if (command.equals("/actions")) {
            StringBuilder list = new StringBuilder("Actions disponíveis:\n");
            for (var flow : project.flows)
                list.append(flow.id).append(" — ").append(flow.title).append(" (")
                        .append(flow.trigger).append(")\n");
            list.append(YouTubeSubscriptions.ACTION_ID).append(" — Novos vídeos do YouTube (automático)\n");
            return list.toString();
        }
        if (command.equals("/youtube_list")) return youtube.list();
        if (command.equals("/youtube_add")) {
            if (argument.isEmpty()) return "Use /youtube_add <@handle, username ou URL de canal>.";
            return youtube.add(argument);
        }
        if (command.equals("/youtube_remove")) {
            if (argument.isEmpty()) return "Use /youtube_remove <@handle, username ou URL de canal>.";
            return youtube.remove(argument);
        }
        if (command.equals("/youtube_check")) {
            int count = youtube.check(bindings.destinations(YouTubeSubscriptions.ACTION_ID),
                    (target, message) -> send(target.chatId(), target.threadId(), message));
            return count + " vídeo(s) novo(s) enviado(s).";
        }
        if (command.equals("/bindings")) {
            var ids = bindings.at(destination);
            return ids.isEmpty() ? "Nenhuma action vinculada aqui. Use /bindaction <id>."
                    : "Actions vinculadas aqui:\n" + String.join("\n", ids);
        }
        var flow = project.flows.stream().filter(item -> item.id.equals(argument)).findFirst();
        if (argument.isEmpty() || (flow.isEmpty() && !argument.equals(YouTubeSubscriptions.ACTION_ID)))
            return "Informe um ID válido. Use /actions para listar.";
        if (command.equals("/bindaction"))
            return bindings.add(argument, destination) ? "Action " + argument + " vinculada a este destino."
                    : "Action " + argument + " já está vinculada aqui.";
        if (command.equals("/unbindaction"))
            return bindings.remove(argument, destination) ? "Vínculo de " + argument + " removido."
                    : "Action " + argument + " não estava vinculada aqui.";
        if (argument.equals(YouTubeSubscriptions.ACTION_ID)) {
            int count = youtube.check(java.util.List.of(destination),
                    (target, message) -> send(target.chatId(), target.threadId(), message));
            return count + " vídeo(s) novo(s) enviado(s).";
        }
        Object reply = actions.get(argument).run(context);
        send(context.chatId(), context.threadId(), reply);
        return "Action " + argument + " executada.";
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
        send(chat, null, value);
    }

    private void send(long chat, Integer threadId, Object value) throws Exception {
        if (value == null) throw new IllegalStateException("A ação retornou null");
        if (value instanceof BotDelivery delivery) {
            send(delivery.chatId(), delivery.threadId(), delivery.reply());
            return;
        }
        if (value instanceof Iterable<?> batch) {
            for (Object item : batch) send(chat, threadId, item);
            return;
        }
        BotReply reply = value instanceof BotReply rich ? rich : BotReply.text(value.toString());
        String markup = replyMarkup(reply);
        String options = threadOption(threadId) + (reply.html() ? "&parse_mode=HTML" : "")
                + (markup.isEmpty() ? "" : "&reply_markup=" + URLEncoder.encode(markup, StandardCharsets.UTF_8));
        try {
            if (reply.photoRef() != null && !reply.photoRef().isBlank()) {
                Path local = reply.photoRef().startsWith("/") ? Path.of(reply.photoRef()) : null;
                JsonNode result;
                if (local != null && !Files.isRegularFile(local))
                    throw new IllegalStateException("Arquivo da foto não encontrado: " + local);
                if (local != null) {
                    result = sendLocalPhoto(chat, threadId, reply, local, markup);
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
        } catch (TelegramApiException error) {
            throw new java.io.IOException("Entrega para chat " + chat
                    + (threadId == null ? "" : ", tópico " + threadId) + " falhou: " + error.getMessage(), error);
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

    static String threadOption(Integer threadId) {
        if (threadId == null) return "";
        if (threadId <= 0) throw new IllegalArgumentException("ID do tópico deve ser positivo");
        return "&message_thread_id=" + threadId;
    }

    private JsonNode sendLocalPhoto(long chat, Integer threadId, BotReply reply, Path file, String markup) throws Exception {
        String boundary = "tg-bot-" + java.util.UUID.randomUUID();
        var output = new java.io.ByteArrayOutputStream();
        writePart(output, boundary, "chat_id", Long.toString(chat));
        if (threadId != null) {
            threadOption(threadId);
            writePart(output, boundary, "message_thread_id", Integer.toString(threadId));
        }
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
        return parseTelegramResponse(response.statusCode(), method, response.body(), token);
    }

    static JsonNode parseTelegramResponse(int status, String method, String body, String token) throws Exception {
        JsonNode result;
        try { result = JSON.readTree(body); }
        catch (Exception malformed) { result = null; }
        if (status != 200 || result == null || !result.path("ok").asBoolean()) {
            String description = result == null ? "resposta sem descrição"
                    : result.path("description").asText("resposta sem descrição");
            throw new TelegramApiException(status, method, description.replace(token, "[token redacted]"));
        }
        return result;
    }

    private static final class TelegramApiException extends java.io.IOException {
        final int status;
        TelegramApiException(int status, String method, String description) {
            super("Telegram " + method + " HTTP " + status + ": " + description);
            this.status = status;
        }
    }

    private String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null ? error.getClass().getSimpleName() : message.replace(token, "[token redacted]");
    }
}
