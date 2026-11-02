package tg_bot_editor;

import megalodonte.base.components.Ref;
import megalodonte.base.state.State;
import megalodonte.components.DiagramCanvas;

import java.nio.file.Path;
import java.util.Map;
import java.util.List;

/** First diagram editor slice: /start event connected to a visual text response. */
final class DiagramViewModel {
    record Summary(String id, String name, String trigger, int steps, boolean enabled) { }
    static final class CompositionItem {
        final State<String> type = State.of("TEXT");
        final State<String> value = State.of("");
        final State<String> target = State.of("");
        final State<String> buttonKind = State.of("URL");
        final State<List<String>> buttonFields = State.of(List.of());
        CompositionItem(Map<?, ?> item) {
            type.set(String.valueOf(item.containsKey("type") ? item.get("type") : "TEXT"));
            value.set(String.valueOf(item.containsKey("value") ? item.get("value") : ""));
            target.set(String.valueOf(item.containsKey("target") ? item.get("target") : ""));
            buttonKind.set(String.valueOf(item.containsKey("buttonKind") ? item.get("buttonKind") : "URL"));
            type.subscribe(value -> buttonFields.set("BUTTON".equals(value) ? List.of("BUTTON") : List.of()));
        }
        Map<String, Object> values() { return Map.of("type", type.get(), "value", value.get(),
                "target", target.get(), "buttonKind", buttonKind.get()); }
    }
    final State<Boolean> open = State.of(false);
    final State<Boolean> expandedActionOpen = State.of(false);
    final State<Boolean> subflowOpen = State.of(false);
    final State<List<DiagramViewModel>> subflowWindows = State.of(List.of());
    final State<List<Summary>> summaries = State.of(List.of());
    final State<String> name = State.of("start");
    final State<List<String>> available = State.of(List.of());
    final State<String> chosen = State.of("start");
    final State<String> componentName = State.of("");
    final State<String> pattern = State.of("/start");
    final State<String> event = State.of("COMMAND");
    final State<List<String>> eventTypes = State.of(List.of("COMMAND", "CALLBACK", "TEXT", "PHOTO", "DOCUMENT"));
    final State<String> timerInterval = State.of("10");
    final State<String> response = State.of("");
    final State<List<CompositionItem>> compositionItems = State.of(List.of());
    final State<List<String>> compositionTypes = State.of(List.of("TEXT", "IMAGE", "LINK", "BUTTON"));
    final State<List<String>> compositionButtonKinds = State.of(List.of("URL", "CALLBACK"));
    final State<String> restUrl = State.of("");
    final State<String> restUrlEnv = State.of("");
    final State<String> restMethod = State.of("GET");
    final State<String> restBody = State.of("");
    final State<String> jsonSection = State.of("");
    final State<String> jsonOperation = State.of("UPSERT");
    final State<List<String>> jsonOperations = State.of(List.of("LIST", "UPSERT", "DELETE"));
    final State<String> jsonLegacy = State.of("");
    final State<String> jsonKey = State.of("{{output.key}}");
    final State<String> jsonRecord = State.of("{{output.record}}");
    final State<String> jsonIdentity = State.of("");
    final State<String> jsonResult = State.of("{{output}}");
    final State<List<String>> restMethods = State.of(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD"));
    final State<String> splitterArray = State.of("{{output}}");
    final State<String> splitterAggregation = State.of("list");
    final State<List<String>> splitterAggregations = State.of(List.of("list", "concat", "flatten"));
    final State<String> edgeKind = State.of("ALWAYS");
    final State<List<String>> edgeKinds = State.of(List.of("ALWAYS", "IF", "ELSE"));
    final State<String> edgeName = State.of("");
    final State<String> edgeLeft = State.of("{{output}}");
    final State<String> edgeOperator = State.of("NE");
    final State<List<String>> edgeOperators = State.of(List.of("EQ", "NE", "LT", "GT", "LE", "GE"));
    final State<String> edgeRight = State.of("''");
    final State<List<String>> edgeConditionForm = State.of(List.of());
    final State<String> selectedLabel = State.of("Selecione um componente para ver seu papel.");
    final State<List<String>> selectedTypes = State.of(List.of());
    final State<List<String>> actionHints = State.of(List.of());
    final State<String> javaScript = State.of("");
    final State<String> status = State.of("");
    final Ref<DiagramCanvas> canvas = new Ref<>(new DiagramCanvas());
    private DiagramProject project = new DiagramProject();
    private DiagramProject.Diagram diagram;
    private String selectedNodeId;
    private String selectedEdgeId;
    private Path directory;
    private FlowProject flows = new FlowProject();

    DiagramViewModel() {
        subflowOpen.subscribe(visible -> {
            if (!visible && !subflowWindows.get().isEmpty()) {
                var child = subflowWindows.get().getFirst();
                child.savePending();
                subflowWindows.set(List.of());
                updateSummaries();
                refresh();
            }
        });
        edgeKind.subscribe(kind -> edgeConditionForm.set("IF".equals(kind) ? List.of("IF") : List.of()));
        canvas.current().onSelect(this::selectNode).onEdgeSelect(this::selectEdge);
        canvas.current().onDrop(drop -> addComponent(drop.type(), drop.x(), drop.y()));
        canvas.current().onConnect(connection -> connect(connection.from(), connection.to()));
        canvas.current().onMove(move -> {
            if (diagram == null) return;
            diagram.nodes.stream().filter(node -> node.id.equals(move.id())).findFirst().ifPresent(node -> {
                node.x = move.x(); node.y = move.y(); refresh();
            });
        });
    }

    void selectNode(String id) {
        saveSelectedFields();
        selectedEdgeId = null;
        selectedNodeId = id;
        var node = diagram == null ? null : diagram.nodes.stream()
                .filter(item -> item.id.equals(id)).findFirst().orElse(null);
        if (node == null) {
            selectedTypes.set(List.of());
            componentName.set("");
            selectedLabel.set("Selecione um componente para editar suas propriedades.");
            return;
        }
        componentName.set(node.name);
        if ("CALLBACK".equals(node.type)) {
            pattern.set(String.valueOf(node.settings.getOrDefault("pattern", "")));
            event.set(String.valueOf(node.settings.getOrDefault("event", "COMMAND")));
        }
        if ("COMPOSITION".equals(node.type)) {
            Object items = node.settings.get("items");
            compositionItems.set(items instanceof List<?> list ? list.stream()
                    .filter(Map.class::isInstance).map(item -> new CompositionItem((Map<?, ?>) item)).toList() : List.of());
        }
        if ("REST_HTTP".equals(node.type)) {
            restUrl.set(String.valueOf(node.settings.getOrDefault("url", "")));
            restUrlEnv.set(String.valueOf(node.settings.getOrDefault("urlEnv", "")));
            restMethod.set(String.valueOf(node.settings.getOrDefault("method", "GET")));
            restBody.set(String.valueOf(node.settings.getOrDefault("body", "")));
        }
        if ("JSON_DB".equals(node.type)) {
            jsonSection.set(String.valueOf(node.settings.getOrDefault("section", "")));
            jsonOperation.set(String.valueOf(node.settings.getOrDefault("operation", "UPSERT")));
            jsonLegacy.set(String.valueOf(node.settings.getOrDefault("legacy", "")));
            jsonKey.set(String.valueOf(node.settings.getOrDefault("key", "{{output.key}}")));
            jsonRecord.set(String.valueOf(node.settings.getOrDefault("record", "{{output.record}}")));
            jsonIdentity.set(String.valueOf(node.settings.getOrDefault("identity", "")));
            jsonResult.set(String.valueOf(node.settings.getOrDefault("result", "{{output}}")));
        }
        updateActionHints(node);
        if ("TIMER".equals(node.type)) timerInterval.set(String.valueOf(node.settings.getOrDefault("intervalMinutes", "10")));
        if ("RESPONSE".equals(node.type)) response.set(String.valueOf(node.settings.getOrDefault("text", "")));
        if ("SPLITTER".equals(node.type)) {
            splitterArray.set(String.valueOf(node.settings.getOrDefault("array", "{{output}}")));
            splitterAggregation.set(String.valueOf(node.settings.getOrDefault("aggregation", "list")));
        }
        selectedLabel.set("CALLBACK".equals(node.type) ? "Início · comando recebido"
                : "TIMER".equals(node.type) ? "Início · agendamento"
                : "JAVA".equals(node.type) ? "Java · action"
                : "RESPONSE".equals(node.type) ? "Resposta síncrona"
                : "ITEM".equals(node.type) ? "Início · item do Splitter" : node.type);
        showAction(node);
        selectedTypes.set(List.of(node.type));
    }

    void selectEdge(String id) {
        saveSelectedFields();
        selectedNodeId = null;
        selectedEdgeId = id;
        var edge = diagram == null ? null : diagram.edges.stream().filter(item -> item.id.equals(id)).findFirst().orElse(null);
        if (edge == null) { selectedTypes.set(List.of()); return; }
        edgeKind.set(edge.kind); edgeName.set(edge.name == null ? "" : edge.name);
        edgeLeft.set("{{output}}"); edgeOperator.set("NE"); edgeRight.set("''");
        if (edge.groups.size() == 1 && edge.groups.getFirst().groups.isEmpty()
                && edge.groups.getFirst().comparisons.size() == 1) {
            var comparison = edge.groups.getFirst().comparisons.getFirst();
            edgeLeft.set(comparison.left); edgeOperator.set(comparison.operator); edgeRight.set(comparison.right);
        }
        selectedLabel.set("Conexão · " + edge.from + " → " + edge.to);
        selectedTypes.set(List.of("EDGE"));
    }

    void applyEdge() {
        if (diagram == null || selectedEdgeId == null) return;
        var edge = diagram.edges.stream().filter(item -> item.id.equals(selectedEdgeId)).findFirst().orElse(null);
        if (edge == null) return;
        if (edge.groups.size() > 1 || edge.groups.stream().anyMatch(group -> !group.groups.isEmpty() || group.comparisons.size() > 1)) {
            status.set("Esta condição contém grupos avançados; a edição visual deles ainda não está disponível."); return;
        }
        String kind = edgeKind.get();
        var siblings = diagram.edges.stream().filter(other -> other != edge && other.from.equals(edge.from)).toList();
        if ("ALWAYS".equals(kind) && !siblings.isEmpty()
                || !"ALWAYS".equals(kind) && siblings.stream().anyMatch(other -> "ALWAYS".equals(other.kind))) {
            status.set("Uma saída incondicional não pode coexistir com outros ramos."); return;
        }
        if ("ELSE".equals(kind) && diagram.edges.stream().anyMatch(other -> other != edge && other.from.equals(edge.from) && "ELSE".equals(other.kind))) {
            status.set("Este componente já possui um ramo alternativo."); return;
        }
        if ("IF".equals(kind) && (edgeLeft.get().isBlank() || edgeRight.get().isBlank())) {
            status.set("Informe os dois valores da condição; use '' para texto vazio."); return;
        }
        edge.kind = kind; edge.name = edgeName.get().trim(); edge.groups = new java.util.ArrayList<>();
        if ("IF".equals(kind)) {
            var group = new DiagramProject.ConditionGroup(); var comparison = new DiagramProject.Comparison();
            comparison.left = edgeLeft.get(); comparison.operator = edgeOperator.get(); comparison.right = edgeRight.get();
            group.comparisons.add(comparison); edge.groups.add(group);
        }
        refresh(); status.set("Conexão atualizada. Salve o diagrama.");
    }

    void deleteEdge() {
        if (diagram == null || selectedEdgeId == null) return;
        diagram.edges.removeIf(edge -> edge.id.equals(selectedEdgeId));
        selectedEdgeId = null; selectedTypes.set(List.of()); refresh();
        status.set("Conexão removida. Salve o diagrama.");
    }

    private void saveSelectedFields() {
        if (diagram == null || selectedNodeId == null) return;
        diagram.nodes.stream().filter(node -> node.id.equals(selectedNodeId)).findFirst().ifPresent(node -> {
            if (!componentName.get().isBlank()) node.name = componentName.get().trim();
            var values = new java.util.HashMap<>(node.settings);
            if ("CALLBACK".equals(node.type)) {
                values.put("pattern", pattern.get().trim());
                values.put("event", event.get());
            }
            if ("COMPOSITION".equals(node.type)) values.put("items", compositionItems.get().stream().map(CompositionItem::values).toList());
            if ("REST_HTTP".equals(node.type)) {
                values.put("url", restUrl.get().trim());
                values.put("urlEnv", restUrlEnv.get().trim());
                values.put("method", restMethod.get());
                values.put("body", restBody.get());
            }
            if ("JSON_DB".equals(node.type)) {
                values.put("section", jsonSection.get().trim()); values.put("operation", jsonOperation.get());
                values.put("legacy", jsonLegacy.get().trim()); values.put("key", jsonKey.get().trim());
                values.put("record", jsonRecord.get().trim()); values.put("identity", jsonIdentity.get().trim());
                values.put("result", jsonResult.get().trim());
            }
            if ("TIMER".equals(node.type)) values.put("intervalMinutes", timerInterval.get().trim());
            if ("RESPONSE".equals(node.type)) values.put("text", response.get());
            if ("SPLITTER".equals(node.type)) {
                values.put("array", splitterArray.get().trim());
                values.put("aggregation", splitterAggregation.get());
            }
            if ("JAVA".equals(node.type)) {
                values.put("action", javaScript.get());
                values.remove("flowId");
            }
            node.settings = values;
        });
    }

    void addComponent(String type, double x, double y) {
        if (diagram == null) return;
        String label = switch (type) {
            case "CALLBACK" -> "Callback"; case "TIMER" -> "Timer";
            case "CONVERTER" -> "Converter"; case "JAVA" -> "Java";
            case "GENERATOR" -> "Generator"; case "RESPONSE" -> "Resposta síncrona";
            case "COMPOSITION" -> "Composição"; case "REST_HTTP" -> "Rest HTTP"; case "JDBC" -> "JDBC"; case "JSON_DB" -> "JsonDB"; case "SPLITTER" -> "Splitter";
            default -> null;
        };
        if (label == null || !Double.isFinite(x) || !Double.isFinite(y)) return;
        if (List.of("CALLBACK", "TIMER").contains(type) && diagram.nodes.stream()
                .anyMatch(node -> List.of("CALLBACK", "TIMER", "ITEM").contains(node.type))) {
            status.set("O diagrama já possui um componente de início."); return;
        }
        saveSelectedFields();
        var node = new DiagramProject.Node();
        node.id = java.util.UUID.randomUUID().toString(); node.name = label; node.type = type;
        node.x = x; node.y = y;
        if ("CALLBACK".equals(type)) node.settings = Map.of("event", "COMMAND", "pattern", "");
        if ("COMPOSITION".equals(type)) node.settings = Map.of("items", List.of());
        if ("REST_HTTP".equals(type)) node.settings = Map.of("url", "", "method", "GET", "body", "");
        if ("JSON_DB".equals(type)) node.settings = Map.of("section", "", "operation", "UPSERT");
        if ("TIMER".equals(type)) node.settings = Map.of("intervalMinutes", "10");
        if ("RESPONSE".equals(type)) node.settings = Map.of("text", "");
        diagram.nodes.add(node);
        refresh(); canvas.current().select(node.id); selectNode(node.id);
        status.set("Componente adicionado sem conexão. A configuração e execução dos novos tipos ainda estão em desenvolvimento.");
    }

    void addCompositionItem() {
        var items = new java.util.ArrayList<>(compositionItems.get());
        items.add(new CompositionItem(Map.of())); compositionItems.set(List.copyOf(items));
    }

    void removeCompositionItem(CompositionItem item) {
        var items = new java.util.ArrayList<>(compositionItems.get());
        items.remove(item); compositionItems.set(List.copyOf(items));
    }

    private void updateActionHints(DiagramProject.Node node) {
        boolean inSubflow = diagram.nodes.stream().anyMatch(item -> "ITEM".equals(item.type));
        boolean afterRest = diagram.edges.stream().anyMatch(edge -> edge.to.equals(node.id)
                && diagram.nodes.stream().anyMatch(item -> item.id.equals(edge.from) && "REST_HTTP".equals(item.type)));
        boolean afterComposition = diagram.edges.stream().anyMatch(edge -> edge.to.equals(node.id)
                && diagram.nodes.stream().anyMatch(item -> item.id.equals(edge.from) && "COMPOSITION".equals(item.type)));
        actionHints.set(afterRest ? List.of("input contém a resposta do Rest HTTP.",
                "Map: status, body e headers.", "Exemplo: return ((java.util.Map<?, ?>) input).get(\"body\");")
                : afterComposition ? List.of("input contém a mensagem da composição anterior.")
                : inSubflow ? List.of("Na primeira action, input é o item atual.",
                        "Nas seguintes, input é o resultado anterior.",
                        "index é a posição do item (a partir de 0).", "Exemplo: return input;") : List.of());
    }

    void connect(String sourceId, String targetId) {
        if (diagram == null) return;
        var source = diagram.nodes.stream().filter(item -> item.id.equals(sourceId)).findFirst().orElse(null);
        var target = diagram.nodes.stream().filter(item -> item.id.equals(targetId)).findFirst().orElse(null);
        if (source == null || target == null || sourceId.equals(targetId) || "RESPONSE".equals(source.type)
                || List.of("CALLBACK", "TIMER", "ITEM").contains(target.type)) {
            status.set("Conecte a saída de um componente à entrada de outro componente válido."); return;
        }
        var outgoing = diagram.edges.stream().filter(edge -> edge.from.equals(sourceId)).toList();
        if (outgoing.stream().anyMatch(edge -> "ALWAYS".equals(edge.kind))) {
            status.set("Transforme a saída existente em IF antes de adicionar outro ramo."); return;
        }
        if (outgoing.stream().anyMatch(edge -> edge.to.equals(target.id))) {
            status.set("Este destino já está conectado."); return;
        }
        var reachable = new java.util.HashSet<String>();
        var pending = new java.util.ArrayDeque<String>();
        pending.add(target.id);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (!reachable.add(current)) continue;
            if (current.equals(sourceId)) { status.set("A conexão criaria um ciclo no diagrama."); return; }
            diagram.edges.stream().filter(existing -> existing.from.equals(current)).map(existing -> existing.to)
                    .forEach(pending::addLast);
        }
        saveSelectedFields();
        var edge = new DiagramProject.Edge(); edge.id = java.util.UUID.randomUUID().toString();
        edge.from = sourceId; edge.to = target.id;
        if (!outgoing.isEmpty()) {
            if (outgoing.stream().noneMatch(existing -> "ELSE".equals(existing.kind))) edge.kind = "ELSE";
            else {
                edge.kind = "IF";
                var group = new DiagramProject.ConditionGroup(); var comparison = new DiagramProject.Comparison();
                comparison.left = "{{output}}"; comparison.operator = "NE"; comparison.right = "''";
                group.comparisons.add(comparison); edge.groups.add(group);
            }
        }
        diagram.edges.add(edge); refresh(); status.set("Componentes conectados. Salve o diagrama.");
    }

    void load(Path projectDirectory) throws Exception {
        directory = projectDirectory;
        project = directory == null ? new DiagramProject() : DiagramStore.read(directory);
        flows = directory == null ? new FlowProject() : FlowStore.read(directory.resolve("bot.json"));
        updateSummaries();
        available.set(project.diagrams.stream().map(item -> item.id).toList());
        diagram = project.diagrams.stream().filter(item -> "start".equals(item.id)).findFirst().orElse(null);
        chosen.set(diagram == null ? null : diagram.id);
        showSelected();
    }

    void selectDiagram() {
        if (chosen.get() == null) return;
        diagram = project.diagrams.stream().filter(item -> item.id.equals(chosen.get())).findFirst().orElse(null);
        showSelected();
        canvas.current().resetView();
    }

    void openDiagram(String id) {
        chosen.set(id);
        selectDiagram();
        if (diagram != null) open.set(true);
    }

    void openSubflow() {
        if (diagram == null || selectedNodeId == null) return;
        saveSelectedFields();
        var splitter = diagram.nodes.stream().filter(node -> node.id.equals(selectedNodeId)
                && "SPLITTER".equals(node.type)).findFirst().orElse(null);
        if (splitter == null) return;
        if (splitter.subflowId == null) {
            var child = new DiagramProject.Diagram();
            child.id = "subflow-" + java.util.UUID.randomUUID();
            child.name = "Subfluxo de " + splitter.name;
            var start = new DiagramProject.Node();
            start.id = java.util.UUID.randomUUID().toString(); start.name = "Item atual"; start.type = "ITEM";
            start.x = 55; start.y = 130;
            child.nodes.add(start); project.diagrams.add(child); splitter.subflowId = child.id;
            available.set(project.diagrams.stream().map(item -> item.id).toList());
        }
        diagram.name = name.get().isBlank() ? diagram.name : name.get().trim();
        var child = new DiagramViewModel();
        child.project = project;
        child.directory = directory;
        child.flows = flows;
        child.available.set(available.get());
        child.chosen.set(splitter.subflowId);
        child.selectDiagram();
        if (child.diagram == null) {
            status.set("Subfluxo não encontrado: " + splitter.subflowId);
            return;
        }
        child.open.set(true);
        child.open.subscribe(visible -> {
            if (!visible) subflowOpen.set(false);
        });
        subflowWindows.set(List.of(child));
        subflowOpen.set(true);
        child.status.set("Salve o diagrama para guardar as alterações.");
    }

    private void savePending() {
        for (var child : subflowWindows.get()) child.savePending();
        saveSelectedFields();
        if (diagram != null && !name.get().isBlank()) diagram.name = name.get().trim();
    }

    void close() {
        savePending();
        open.set(false);
    }

    private void showSelected() {
        selectedNodeId = null;
        selectedEdgeId = null;
        selectedTypes.set(List.of());
        componentName.set("");
        javaScript.set("");
        actionHints.set(diagram != null && diagram.nodes.stream().anyMatch(node -> "ITEM".equals(node.type))
                ? List.of("Na primeira action, input é o item atual.",
                        "Nas seguintes, input é o resultado anterior.",
                        "index é a posição do item (a partir de 0).",
                        "Exemplo: return input;") : List.of());
        if (diagram != null) {
            name.set(diagram.name);
            selectedLabel.set("Selecione um componente para editar seu nome.");
            diagram.nodes.stream().filter(node -> "CALLBACK".equals(node.type)).findFirst()
                    .ifPresent(node -> pattern.set(String.valueOf(node.settings.getOrDefault("pattern", "/start"))));
            response.set("");
            diagram.nodes.stream().filter(node -> "RESPONSE".equals(node.type)).findFirst()
                    .ifPresent(node -> response.set(String.valueOf(node.settings.getOrDefault("text", ""))));
        }
        refresh();
    }

    void show() {
        if (directory == null) { status.set("Abra um projeto antes de editar diagramas."); return; }
        if (diagram == null) {
            diagram = new DiagramProject.Diagram();
            diagram.id = "start"; diagram.name = "start";
            var trigger = new DiagramProject.Node();
            trigger.id = "start-trigger"; trigger.name = "Comando /start"; trigger.type = "CALLBACK";
            trigger.x = 55; trigger.y = 130;
            trigger.settings = Map.of("event", "COMMAND", "pattern", "/start");
            var reply = new DiagramProject.Node();
            reply.id = "start-response"; reply.name = "Boas-vindas"; reply.type = "RESPONSE";
            reply.x = 390; reply.y = 130;
            reply.settings = Map.of("text", response.get());
            var edge = new DiagramProject.Edge();
            edge.id = "start-to-response"; edge.from = trigger.id; edge.to = reply.id;
            diagram.nodes.add(trigger); diagram.nodes.add(reply); diagram.edges.add(edge);
            project.diagrams.add(diagram);
        }
        available.set(project.diagrams.stream().map(item -> item.id).toList());
        updateSummaries();
        chosen.set(diagram.id);
        showSelected(); canvas.current().resetView(); open.set(true);
    }

    void save() {
        if (diagram == null || directory == null) return;
        try {
            savePending();
            diagram.name = name.get().isBlank() ? "start" : name.get().trim();
            saveSelectedFields();
            if (diagram.enabled && diagram.nodes.stream().filter(node -> "REST_HTTP".equals(node.type))
                    .anyMatch(node -> String.valueOf(node.settings.getOrDefault("url", "")).isBlank())) {
                status.set("Informe a URL do componente Rest HTTP antes de salvar."); return;
            }
            if (diagram.enabled && diagram.nodes.stream().filter(node -> "TIMER".equals(node.type))
                    .anyMatch(node -> !String.valueOf(node.settings.getOrDefault("intervalMinutes", "")).matches("[1-9][0-9]*"))) {
                status.set("Informe um intervalo inteiro maior que zero, em minutos."); return;
            }
            if (diagram.enabled && diagram.nodes.stream().filter(node -> "CALLBACK".equals(node.type))
                    .anyMatch(node -> String.valueOf(node.settings.getOrDefault("pattern", "")).isBlank())) {
                status.set("Informe o padrão do componente de início antes de salvar."); return;
            }
            if (diagram.enabled && diagram.edges.stream().anyMatch(edge -> diagram.nodes.stream()
                    .anyMatch(node -> node.id.equals(edge.to) && "RESPONSE".equals(node.type)
                            && String.valueOf(node.settings.getOrDefault("text", "")).isBlank()))) {
                status.set("Informe o texto de cada resposta conectada antes de salvar."); return;
            }
            if (diagram.enabled && diagram.edges.stream().anyMatch(edge -> diagram.nodes.stream()
                    .anyMatch(node -> node.id.equals(edge.to) && "JAVA".equals(node.type)
                            && String.valueOf(node.settings.getOrDefault("action", "")).isBlank()
                            && String.valueOf(node.settings.getOrDefault("flowId", "")).isBlank()))) {
                status.set("Informe a action de cada componente Java conectado antes de salvar."); return;
            }
            ActionCompiler.compileProject(directory, flows, project);
            DiagramStore.write(directory, project);
            updateSummaries();
            status.set("Diagrama salvo. Componentes adicionados sem conexão não são executados.");
            refresh();
        } catch (Exception error) { status.set("Não foi possível salvar o diagrama: " + error.getMessage()); }
    }

    void preview() {
        if (diagram == null) return;
        status.set(diagram.nodes.stream().noneMatch(node -> "JAVA".equals(node.type))
                ? "Prévia: " + response.get()
                : "A prévia de actions Java no diagrama ainda não está disponível.");
    }

    private void showAction(DiagramProject.Node node) {
        javaScript.set("");
        if (!"JAVA".equals(node.type)) return;
        if (node.settings.get("action") instanceof String body) {
            javaScript.set(body);
            return;
        }
        String flowId = String.valueOf(node.settings.getOrDefault("flowId", ""));
        var flow = flows.flows.stream().filter(item -> flowId.equals(item.id)).findFirst().orElse(null);
        if (flow != null) javaScript.set(flow.script);
        else status.set("Action legada não encontrada: " + flowId);
    }

    private void refresh() {
        if (diagram == null) { canvas.current().show(java.util.List.of(), java.util.List.of()); return; }
        canvas.current().show(diagram.nodes.stream().map(node -> new DiagramCanvas.Node(
                        node.id, node.name, switch (node.type) {
                            case "CALLBACK", "TIMER", "ITEM" -> "INÍCIO";
                            case "RESPONSE" -> "RESPOSTA";
                            default -> node.type;
                        },
                        node.x, node.y, !List.of("CALLBACK", "TIMER", "ITEM").contains(node.type),
                        !"RESPONSE".equals(node.type))).toList(),
                diagram.edges.stream().map(edge -> new DiagramCanvas.Edge(edge.id, edge.from, edge.to, edge.kind)).toList());
    }

    private void updateSummaries() {
        var subflows = project.diagrams.stream().flatMap(item -> item.nodes.stream())
                .filter(node -> node.subflowId != null).map(node -> node.subflowId).collect(java.util.stream.Collectors.toSet());
        summaries.set(project.diagrams.stream().filter(item -> !subflows.contains(item.id)).map(item -> {
            String trigger = item.nodes.stream().filter(node -> "CALLBACK".equals(node.type))
                    .findFirst().map(node -> String.valueOf(node.settings.getOrDefault("pattern", "")))
                    .orElseGet(() -> item.nodes.stream().filter(node -> "TIMER".equals(node.type))
                            .findFirst().map(node -> "A cada " + node.settings.getOrDefault("intervalMinutes", "?") + " min")
                            .orElse("Sem gatilho"));
            return new Summary(item.id, item.name, trigger, item.nodes.size(), item.enabled);
        }).toList());
    }
}
