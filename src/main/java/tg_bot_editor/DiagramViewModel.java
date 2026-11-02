package tg_bot_editor;

import megalodonte.base.components.Ref;
import megalodonte.base.state.State;
import megalodonte.components.DiagramCanvas;

import java.nio.file.Path;
import java.util.Map;

/** First diagram editor slice: /start event connected to a visual text response. */
final class DiagramViewModel {
    final State<Boolean> open = State.of(false);
    final State<String> name = State.of("start");
    final State<String> pattern = State.of("/start");
    final State<String> response = State.of("");
    final State<String> selectedLabel = State.of("Selecione um componente para ver seu papel.");
    final State<String> status = State.of("");
    final Ref<DiagramCanvas> canvas = new Ref<>(new DiagramCanvas());
    private DiagramProject project = new DiagramProject();
    private DiagramProject.Diagram diagram;
    private Path directory;

    DiagramViewModel() {
        canvas.current().onSelect(id -> selectedLabel.set("start-trigger".equals(id)
                ? "Início · comando recebido" : "Resposta síncrona · texto enviado ao Telegram"));
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
        diagram = project.diagrams.stream().filter(item -> "start".equals(item.id)).findFirst().orElse(null);
        if (diagram != null) {
            name.set(diagram.name);
            diagram.nodes.stream().filter(node -> "CALLBACK".equals(node.type)).findFirst()
                    .ifPresent(node -> pattern.set(String.valueOf(node.settings.getOrDefault("pattern", "/start"))));
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
        refresh(); canvas.current().resetView(); open.set(true);
    }

    void save() {
        if (diagram == null || directory == null) return;
        if (!"/start".equals(pattern.get().trim()) || response.get().isBlank()) {
            status.set("O primeiro diagrama deve usar /start e conter uma resposta."); return;
        }
        try {
            diagram.name = name.get().isBlank() ? "start" : name.get().trim();
            diagram.nodes.stream().filter(node -> "CALLBACK".equals(node.type)).findFirst().orElseThrow()
                    .settings = Map.of("event", "COMMAND", "pattern", pattern.get().trim());
            diagram.nodes.stream().filter(node -> "RESPONSE".equals(node.type)).findFirst().orElseThrow()
                    .settings = Map.of("text", response.get());
            diagram.enabled = true;
            DiagramStore.write(directory, project);
            status.set("Diagrama salvo e ativo: /start → resposta síncrona.");
            refresh();
        } catch (Exception error) { status.set("Não foi possível salvar o diagrama: " + error.getMessage()); }
    }

    void preview() {
        if (diagram == null) return;
        status.set("Prévia: " + response.get());
    }

    private void refresh() {
        if (diagram == null) { canvas.current().show(java.util.List.of(), java.util.List.of()); return; }
        canvas.current().show(diagram.nodes.stream().map(node -> new DiagramCanvas.Node(
                        node.id, node.name, "CALLBACK".equals(node.type) ? "INÍCIO" : "RESPOSTA",
                        node.x, node.y)).toList(),
                diagram.edges.stream().map(edge -> new DiagramCanvas.Edge(edge.from, edge.to, edge.kind)).toList());
    }
}
