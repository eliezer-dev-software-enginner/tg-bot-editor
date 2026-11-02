package tg_bot_editor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** One long-polling process and one token for every enabled flow. */
public final class BotRunner {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String token;
    private final FlowProject project;
    private final DiagramProject diagrams;
    private final Path projectDirectory;
    private final Map<String, BotAction> actions = new HashMap<>();
    private final Map<String, Instant> lastStartupDelivery = new HashMap<>();
    private final Set<Long> privateCommandMenus = new HashSet<>();
    private ScheduledExecutorService schedules;
    private long offset;

    private BotRunner(String token, Path projectDirectory, FlowProject project) throws Exception {
        this.token = token;
        this.project = project;
        this.diagrams = DiagramStore.read(projectDirectory);
        this.projectDirectory = projectDirectory;
        FlowValidator.validate(project);
        actions.putAll(ActionCompiler.compileProject(projectDirectory, project, diagrams));
        offset = BotDatabase.read("telegramOffset", null, new TypeReference<Long>() { }, () -> 0L);
    }

    public static void main(String[] args) throws Exception {
        boolean check = args.length == 2 && "--check".equals(args[0]);
        if ((!check && args.length != 1) || (args.length == 2 && !check))
            throw new IllegalArgumentException("Uso: BotRunner [--check] <arquivo-do-projeto.json>");
        Path projectFile = Path.of(args[check ? 1 : 0]).toAbsolutePath().normalize();
        var project = FlowStore.read(projectFile);
        if (check) {
            FlowValidator.validate(project);
            var diagrams = DiagramStore.read(projectFile.getParent());
            int count = ActionCompiler.compileProject(projectFile.getParent(), project, diagrams).size();
            System.out.println("Projeto válido: " + count + " fluxos Java compilados.");
            return;
        }
        ProjectEnvironment.activate(projectFile.getParent());
        String token = ProjectEnvironment.get("TG_BOT_TOKEN");
        if (token == null || token.isBlank()) throw new IllegalStateException("Defina TG_BOT_TOKEN fora do projeto");
        BotDatabase.initialize();
        var runner = new BotRunner(token, projectFile.getParent(), project);
        try {
            runner.registerPrivateCommands();
            runner.startSchedules();
            runner.poll();
        } finally {
            if (runner.schedules != null) runner.schedules.shutdownNow();
        }
    }

    private void startSchedules() throws java.io.IOException {
        schedules = Executors.newSingleThreadScheduledExecutor();
        for (var flow : scheduledFlows(project, diagrams)) {
            long chat = 0;
            if (flow.targetChatEnv != null && !flow.targetChatEnv.isBlank()) {
                String target = ProjectEnvironment.get(flow.targetChatEnv);
                if ((target == null || target.isBlank()) && new ActionBindings(projectDirectory).destinations(flow.id).isEmpty())
                    throw new IllegalStateException("Defina " + flow.targetChatEnv + " para " + flow.title);
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

    static java.util.List<FlowProject.Flow> scheduledFlows(FlowProject project, DiagramProject diagrams) {
        var result = new java.util.LinkedHashMap<String, FlowProject.Flow>();
        for (var flow : project.flows) if (scheduled(flow.trigger)) result.put(flow.id, flow);
        for (var diagram : diagrams.diagrams) {
            var timer = diagram.nodes.stream().filter(node -> "TIMER".equals(node.type)).findFirst().orElse(null);
            if (timer == null) continue;
            if (!diagram.enabled) { result.remove(diagram.id); continue; }
            String interval = String.valueOf(timer.settings.getOrDefault("intervalMinutes", ""));
            long minutes;
            try { minutes = Long.parseLong(interval); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("Intervalo inválido em " + diagram.name, e); }
            if (minutes < 1) throw new IllegalArgumentException("Intervalo deve ser maior que zero em " + diagram.name);
            var flow = new FlowProject.Flow();
            flow.id = diagram.id; flow.title = diagram.name; flow.trigger = "INTERVAL"; flow.pattern = interval;
            flow.targetChatEnv = String.valueOf(timer.settings.getOrDefault("targetChatEnv", ""));
            result.put(flow.id, flow);
        }
        return java.util.List.copyOf(result.values());
    }

    private static boolean scheduled(String trigger) {
        return "INTERVAL".equalsIgnoreCase(trigger) || "DAILY".equalsIgnoreCase(trigger);
    }

    private void registerPrivateCommands() {
        var commands = JSON.createArrayNode();
        var available = BotCommands.list(project);
        if (available.size() > 100) throw new IllegalStateException("O Telegram aceita no máximo 100 comandos.");
        for (var command : available)
            commands.addObject().put("command", command.name().substring(1))
                    .put("description", command.description());
        try {
            call("setMyCommands", "commands=" + URLEncoder.encode(commands.toString(), StandardCharsets.UTF_8)
                    + "&scope=" + URLEncoder.encode("{\"type\":\"all_private_chats\"}", StandardCharsets.UTF_8), 20);
            call("setMyCommands", "commands=" + URLEncoder.encode(commands.toString(), StandardCharsets.UTF_8), 20);
            System.out.println("Menu de comandos atualizado: " + available.size() + " comandos.");
        } catch (Exception error) {
            System.err.println("Não foi possível atualizar o menu de comandos: " + safeMessage(error));
        }
        configureCommandMenu(null);
    }

    private void configureCommandMenu(Long chatId) {
        String data = "menu_button=" + URLEncoder.encode("{\"type\":\"commands\"}", StandardCharsets.UTF_8);
        if (chatId != null) data += "&chat_id=" + chatId;
        try {
            call("setChatMenuButton", data, 20);
            if (chatId != null) privateCommandMenus.add(chatId);
        } catch (Exception error) {
            System.err.println("Não foi possível ativar o botão de comandos"
                    + (chatId == null ? " padrão" : " no chat " + chatId) + ": " + safeMessage(error));
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
            var destinations = new ActionBindings(projectDirectory).destinations(flow.id);
            boolean delivered = false;
            if (destinations.isEmpty()) {
                Object reply = runProjectAction(flow.id, new BotContext(chat, 0, "", "scheduler"));
                send(chat, reply);
                delivered = !(reply instanceof java.util.Collection<?> batch && batch.isEmpty());
            } else {
                for (int i = 0; i < destinations.size(); i++) {
                    var destination = destinations.get(i);
                    Object reply = runProjectAction(flow.id, new BotContext(destination.chatId(), 0, "", "scheduler",
                            null, destination.threadId()));
                    send(destination.chatId(), destination.threadId(), reply);
                    delivered |= !(reply instanceof java.util.Collection<?> batch && batch.isEmpty());
                    // An action that returns explicit deliveries has already selected all targets.
                    if (reply instanceof BotDelivery || reply instanceof java.util.Collection<?> batch
                            && batch.stream().anyMatch(BotDelivery.class::isInstance)) break;
                }
            }
            if (startup && delivered)
                lastStartupDelivery.put(flow.id, Instant.now());
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
                        BotDatabase.write("telegramOffset", offset);
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
        if (chat > 0 && chat == sender.path("id").asLong() && !privateCommandMenus.contains(chat))
            configureCommandMenu(chat);
        String value = callback.isMissingNode() ? BotCommands.normalize(message.path("text").asText(""))
                : callback.path("data").asText("");
        JsonNode photos = message.path("photo");
        String photoRef = photos.isArray() && !photos.isEmpty()
                ? photos.get(photos.size() - 1).path("file_id").asText("") : null;
        JsonNode document = message.path("document");
        String kind = callback.isMissingNode() ? (!document.isMissingNode() ? "DOCUMENT" : photoRef != null ? "PHOTO"
                : value.startsWith("/") ? "COMMAND" : "TEXT") : "CALLBACK";
        Integer threadId = message.hasNonNull("message_thread_id") ? message.path("message_thread_id").asInt() : null;
        var diagram = DiagramExecutor.find(diagrams, kind, value);
        var match = diagram.isPresent() ? java.util.Optional.<FlowProject.Flow>empty()
                : FlowRouter.find(project, kind, kind.equals("DOCUMENT") ? "*" : value);
        if (diagram.isEmpty() && match.isEmpty()) {
            answerCallback(callback);
            return;
        }
        Path downloaded = null;
        try {
            if (kind.equals("DOCUMENT")) {
                try {
                    if (document.path("file_size").asLong() > 20_000_000L)
                        throw new IllegalArgumentException("Documento acima do limite de 20 MB para download pelo bot.");
                    downloaded = downloadFile(document.path("file_id").asText());
                }
                catch (Exception error) {
                    if (chat == sender.path("id").asLong())
                        send(chat, BotReply.text("Falha ao baixar o documento: " + safeMessage(error)));
                    return;
                }
            }
        var context = new BotContext(chat, sender.path("id").asLong(), value,
                    sender.path("username").asText(""), photoRef,
                    threadId,
                    downloaded, document.path("file_name").asText(""), this::downloadFile,
                    this::runProjectAction);
            try {
                send(chat, context.threadId(), diagram.isPresent()
                        ? DiagramExecutor.run(diagrams, diagram.get(), context, actions) : actions.get(match.get().id).run(context));
            } catch (Exception error) {
                System.err.println("Ação falhou: " + (diagram.isPresent() ? diagram.get().name : match.get().title)
                        + ": " + safeMessage(error));
                if (chat == context.userId())
                    send(chat, BotReply.text("Ação falhou: " + safeMessage(error)));
            }
        } finally {
            if (downloaded != null) Files.deleteIfExists(downloaded);
        }
        answerCallback(callback);
    }

    private Object runProjectAction(String actionId, BotContext context) throws Exception {
        var scheduledDiagram = diagrams.diagrams.stream().filter(diagram -> diagram.enabled
                && diagram.id.equals(actionId) && diagram.nodes.stream().anyMatch(node -> "TIMER".equals(node.type)))
                .findFirst();
        if (scheduledDiagram.isPresent()) return DiagramExecutor.run(diagrams, scheduledDiagram.get(), context, actions);
        BotAction action = actions.get(actionId);
        if (action == null) throw new IllegalArgumentException("Action não encontrada: " + actionId);
        return action.run(context);
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
        if (value instanceof BotDocument document) {
            try { sendDocument(chat, threadId, document); }
            finally { if (document.cleanup() != null) document.cleanup().run(); }
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
                    try {
                        result = call("sendPhoto", data, 40);
                    } catch (TelegramApiException photoError) {
                        // A public image URL can be valid for us yet inaccessible to Telegram.
                        // Preserve the news text and its delivery acknowledgement in that case.
                        sendText(chat, reply, options);
                        result = JSON.createObjectNode().put("ok", true);
                    }
                }
                if (!result.path("ok").asBoolean())
                    throw new IllegalStateException("Telegram recusou sendPhoto");
            } else if (reply.text() != null && !reply.text().isBlank()) {
                sendText(chat, reply, options);
            } else {
                throw new IllegalStateException("A ação retornou uma resposta vazia");
            }
        } catch (TelegramApiException error) {
            throw new java.io.IOException("Entrega para chat " + chat
                    + (threadId == null ? "" : ", tópico " + threadId) + " falhou: " + error.getMessage(), error);
        }
        if (reply.afterSend() != null) reply.afterSend().run();
    }

    private void sendText(long chat, BotReply reply, String options) throws Exception {
        if (reply.text() == null || reply.text().isBlank())
            throw new IllegalStateException("A foto não pôde ser enviada e não há texto alternativo");
        String data = "chat_id=" + chat + "&text=" + URLEncoder.encode(reply.text(), StandardCharsets.UTF_8) + options;
        if (!call("sendMessage", data, 20).path("ok").asBoolean())
            throw new IllegalStateException("Telegram recusou sendMessage");
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

    private void sendDocument(long chat, Integer threadId, BotDocument document) throws Exception {
        String boundary = "tg-bot-" + java.util.UUID.randomUUID();
        var prefix = new java.io.ByteArrayOutputStream();
        writePart(prefix, boundary, "chat_id", Long.toString(chat));
        if (threadId != null) writePart(prefix, boundary, "message_thread_id", Integer.toString(threadId));
        writePart(prefix, boundary, "caption", document.caption() == null ? "" : document.caption());
        String filename = document.filename().replaceAll("[^a-zA-Z0-9._-]", "_");
        prefix.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"document\"; filename=\""
                + filename + "\"\r\nContent-Type: application/zip\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        var body = HttpRequest.BodyPublishers.concat(HttpRequest.BodyPublishers.ofByteArray(prefix.toByteArray()),
                HttpRequest.BodyPublishers.ofFile(document.path()),
                HttpRequest.BodyPublishers.ofString("\r\n--" + boundary + "--\r\n"));
        request("sendDocument", body, "multipart/form-data; boundary=" + boundary, 120);
    }

    private Path downloadFile(String fileId) throws Exception {
        if (fileId == null || fileId.isBlank()) throw new IllegalArgumentException("Arquivo do Telegram sem file_id.");
        JsonNode result = call("getFile", "file_id=" + URLEncoder.encode(fileId, StandardCharsets.UTF_8), 20);
        String filePath = result.path("result").path("file_path").asText("");
        if (filePath.isBlank() || filePath.startsWith("/") || filePath.contains(".."))
            throw new IllegalStateException("Caminho de arquivo inválido retornado pelo Telegram.");
        Path temp = Files.createTempFile("tg-bot-download-", ".tmp");
        try {
            var request = HttpRequest.newBuilder(URI.create("https://api.telegram.org/file/bot" + token + "/" + filePath))
                    .timeout(Duration.ofSeconds(60)).GET().build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofFile(temp));
            if (response.statusCode() != 200 || Files.size(temp) > 20_000_000L)
                throw new IllegalStateException("Não foi possível baixar arquivo do Telegram (HTTP "
                        + response.statusCode() + ", limite 20 MB).");
            return temp;
        } catch (Exception error) {
            Files.deleteIfExists(temp);
            throw error;
        }
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
