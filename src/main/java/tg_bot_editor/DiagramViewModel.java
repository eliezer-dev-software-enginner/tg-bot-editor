package tg_bot_editor;

import megalodonte.base.components.Ref;
import megalodonte.base.state.State;
import megalodonte.components.DiagramCanvas;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Map;
import java.util.List;

/** First diagram editor slice: /start event connected to a visual text response. */
final class DiagramViewModel {
    final State<Boolean> open = State.of(false);
    final State<String> name = State.of("start");
    final State<List<String>> available = State.of(List.of());
    final State<String> chosen = State.of("start");
    final State<String> componentName = State.of("");
    final State<String> pattern = State.of("/start");
    final State<String> response = State.of("");
    final State<String> selectedLabel = State.of("Selecione um componente para ver seu papel.");
    final State<String> action = State.of("");
    final State<String> javaScript = State.of("");
    final State<String> javaSource = State.of("");
    final State<String> status = State.of("");
    final Ref<DiagramCanvas> canvas = new Ref<>(new DiagramCanvas());
    private DiagramProject project = new DiagramProject();
    private DiagramProject.Diagram diagram;
    private String selectedNodeId;
    private Path directory;
    private FlowProject flows = new FlowProject();

    DiagramViewModel() {
        canvas.current().onSelect(id -> {
            selectedNodeId = id;
            if (diagram != null) diagram.nodes.stream().filter(node -> node.id.equals(id)).findFirst()
                    .ifPresent(node -> {
                        componentName.set(node.name);
                        selectedLabel.set("CALLBACK".equals(node.type) ? "Início · comando recebido"
                                : "JAVA".equals(node.type) ? "Java · ação existente: " + node.settings.get("flowId")
                                : "Resposta síncrona · texto enviado ao Telegram");
                        showAction(node);
                    });
        });
        canvas.current().onMove(move -> {
            if (diagram == null) return;
            diagram.nodes.stream().filter(node -> node.id.equals(move.id())).findFirst().ifPresent(node -> {
                node.x = move.x(); node.y = move.y(); refresh();
            });
        });
    }

    void load(Path projectDirectory) throws Exception {
        directory = projectDirectory;
        project = directory == null ? new DiagramProject() : DiagramStore.read(directory);
        flows = directory == null ? new FlowProject() : FlowStore.read(directory.resolve("bot.json"));
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

    private void showSelected() {
        selectedNodeId = null;
        componentName.set("");
        action.set(""); javaScript.set(""); javaSource.set("");
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
        chosen.set(diagram.id);
        showSelected(); canvas.current().resetView(); open.set(true);
    }

    void save() {
        if (diagram == null || directory == null) return;
        if (pattern.get().isBlank() || (diagram.nodes.stream().anyMatch(node -> "RESPONSE".equals(node.type))
                && response.get().isBlank())) {
            status.set("Informe o comando e o texto da resposta, quando houver."); return;
        }
        try {
            diagram.name = name.get().isBlank() ? "start" : name.get().trim();
            if (selectedNodeId != null && !componentName.get().isBlank())
                diagram.nodes.stream().filter(node -> node.id.equals(selectedNodeId)).findFirst()
                        .ifPresent(node -> node.name = componentName.get().trim());
            diagram.nodes.stream().filter(node -> "CALLBACK".equals(node.type)).findFirst().orElseThrow()
                    .settings = Map.of("event", "COMMAND", "pattern", pattern.get().trim());
            diagram.nodes.stream().filter(node -> "RESPONSE".equals(node.type)).findFirst()
                    .ifPresent(node -> node.settings = Map.of("text", response.get()));
            diagram.enabled = true;
            DiagramStore.write(directory, project);
            status.set("Diagrama salvo e ativo: " + pattern.get().trim());
            refresh();
        } catch (Exception error) { status.set("Não foi possível salvar o diagrama: " + error.getMessage()); }
    }

    void preview() {
        if (diagram == null) return;
        status.set(diagram.nodes.stream().anyMatch(node -> "RESPONSE".equals(node.type))
                ? "Prévia: " + response.get()
                : "Esta resposta depende da ação Java. Use o teste local do fluxo para executá-la.");
    }

    private void showAction(DiagramProject.Node node) {
        action.set(""); javaScript.set(""); javaSource.set("");
        if (!"JAVA".equals(node.type)) return;
        String flowId = String.valueOf(node.settings.getOrDefault("flowId", ""));
        var flow = flows.flows.stream().filter(item -> flowId.equals(item.id)).findFirst().orElse(null);
        if (flow == null) { action.set("Ação não encontrada em bot.json: " + flowId); return; }
        action.set(flow.title + " (" + flow.id + ") · bot.json");
        javaScript.set(flow.script);
        var reference = java.util.regex.Pattern.compile("\\b([A-Za-z_$][\\w$]*)\\.[A-Za-z_$][\\w$]*\\s*\\(")
                .matcher(flow.script);
        if (reference.find() && directory != null) {
            Path source = directory.resolve("scripts").resolve(reference.group(1) + ".java");
            try {
                javaSource.set(Files.isRegularFile(source) ? "scripts/" + source.getFileName() + "\n\n"
                        + Files.readString(source) : "Fonte da action não encontrada: " + source.getFileName());
            } catch (Exception error) { javaSource.set("Não foi possível ler a action: " + error.getMessage()); }
        } else javaSource.set("Script Java inline: não há classe de action separada.");
    }

    private void refresh() {
        if (diagram == null) { canvas.current().show(java.util.List.of(), java.util.List.of()); return; }
        canvas.current().show(diagram.nodes.stream().map(node -> new DiagramCanvas.Node(
                        node.id, node.name, "CALLBACK".equals(node.type) ? "INÍCIO"
                                : "JAVA".equals(node.type) ? "JAVA" : "RESPOSTA",
                        node.x, node.y)).toList(),
                diagram.edges.stream().map(edge -> new DiagramCanvas.Edge(edge.from, edge.to, edge.kind)).toList());
    }
}
