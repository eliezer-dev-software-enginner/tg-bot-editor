package tg_bot_editor;

import megalodonte.base.components.Component;
import megalodonte.base.components.ScreenComponent;
import megalodonte.base.state.State;
import megalodonte.components.Button;
import megalodonte.components.FlowCanvas;
import megalodonte.components.Scroll;
import megalodonte.components.Text;
import megalodonte.components.inputs.Input;
import megalodonte.components.inputs.JavaCodeEditor;
import megalodonte.components.layout_components.Container;
import megalodonte.components.layout_components.Row;
import megalodonte.props.ContainerProps;
import megalodonte.props.InputProps;
import megalodonte.props.RowProps;
import megalodonte.props.TextProps;

import java.nio.file.Path;
import java.util.UUID;

/** Editor surface composed entirely of Megalodonte components. */
public final class HomeScreen implements ScreenComponent {
    private final Path file = Path.of(System.getProperty("user.home"), "Documents", "TG-bot Editor", "bot.json");
    private final State<String> status = State.of("Crie um fluxo ou selecione um nó no canvas.");
    private final State<String> title = State.of("");
    private final State<String> pattern = State.of("");
    private final State<String> trigger = State.of("COMMAND");
    private final State<String> targetChatEnv = State.of("TG_BOT_TARGET_CHAT");
    private final State<String> script = State.of("");
    private final State<String> sample = State.of("/start");
    private final FlowCanvas canvas = new FlowCanvas();
    private FlowProject project = new FlowProject();
    private FlowProject.Flow selected;

    public HomeScreen() {
        try {
            if (java.nio.file.Files.exists(file)) project = FlowStore.read(file);
            else try (var template = getClass().getResourceAsStream("/templates/arsenal-soup.json")) {
                if (template != null) project = FlowStore.read(template);
            }
        } catch (Exception error) {
            status.set("Não foi possível abrir o projeto: " + error.getMessage());
        }
        canvas.onSelect(this::select).onMove(move -> {
            project.flows.stream().filter(flow -> flow.id.equals(move.id())).findFirst().ifPresent(flow -> {
                flow.x = move.x();
                flow.y = move.y();
                refresh();
            });
        });
        refresh();
        if (!project.flows.isEmpty()) select(project.flows.getFirst().id);
    }

    @Override public Component render() {
        var heading = new Container(new ContainerProps().paddingAll(16).spacingOf(6)).children(
                new Text("TG-bot Editor", new TextProps().fontSize(28)),
                new Text("Um bot, vários fluxos: gatilho → ação Java → resposta"),
                new Row(new RowProps().spacingOf(8)).children(
                        new Button("+ Novo comando").onClick(this::add),
                        new Button("Salvar projeto").onClick(this::save),
                        new Button("Excluir fluxo").onClick(this::remove)));

        var inspector = new Container(new ContainerProps().paddingAll(16).spacingOf(10).width(330)).children(
                new Text("Propriedades", new TextProps().fontSize(20)),
                new Text("Título"), new Input(title, field("Nome da ação")),
                new Text("Script Java · edite e retorne texto ou BotReply"),
                new JavaCodeEditor(script, 260),
                new Button("Aplicar script e propriedades").onClick(this::apply),
                new Text("Tipo de gatilho"), new Text(trigger),
                new Row(new RowProps().spacingOf(6)).children(
                        new Button("Comando").onClick(() -> chooseTrigger("COMMAND")),
                        new Button("Texto").onClick(() -> chooseTrigger("TEXT"))),
                new Row(new RowProps().spacingOf(6)).children(
                        new Button("Callback").onClick(() -> chooseTrigger("CALLBACK")),
                        new Button("Intervalo").onClick(() -> chooseTrigger("INTERVAL"))),
                new Button("Horários diários").onClick(() -> chooseTrigger("DAILY")),
                new Text("Padrão · comando, minutos ou HH:mm"), new Input(pattern, field("/start")),
                new Text("Variável de ambiente do canal agendado"),
                new Input(targetChatEnv, field("TG_BOT_TARGET_CHAT")),
                new Text("Teste local (sem Telegram)"),
                new Input(sample, field("/start")),
                new Button("Executar teste").onClick(this::test),
                new Text(status));

        var workspace = new Row(new RowProps().spacingOf(12).fillHeight()).children(
                new Scroll(canvas).fillAvailableHeight(), new Scroll(inspector).fillAvailableHeight());
        return new Container(new ContainerProps().fillHeight()).children(heading, workspace);
    }

    private void refresh() {
        canvas.show(project.flows.stream().map(flow ->
                new FlowCanvas.Node(flow.id, flow.title, flow.trigger + " · " + flow.pattern, flow.x, flow.y)).toList());
    }

    private void select(String id) {
        if (selected != null && !selected.id.equals(id)) {
            copyFieldsToSelected();
            refresh();
        }
        selected = project.flows.stream().filter(flow -> flow.id.equals(id)).findFirst().orElse(null);
        if (selected == null) return;
        title.set(selected.title);
        pattern.set(selected.pattern);
        trigger.set(selected.trigger);
        targetChatEnv.set(selected.targetChatEnv);
        script.set(selected.script);
        canvas.select(id);
        status.set("Editando " + selected.title);
    }

    private void add() {
        int index = project.flows.size();
        var flow = new FlowProject.Flow(UUID.randomUUID().toString(), "Nova ação", "/comando" + (index + 1),
                35 + (index % 3) * 315, 30 + (index / 3) * 125);
        project.flows.add(flow);
        refresh();
        select(flow.id);
    }

    private void apply() {
        updateSelected();
    }

    private boolean updateSelected() {
        if (selected == null) { status.set("Selecione uma ação no canvas."); return false; }
        if (pattern.get().isBlank() || script.get().isBlank()) {
            status.set("Informe o gatilho e o script Java."); return false;
        }
        copyFieldsToSelected();
        refresh();
        status.set("Fluxo atualizado. Salve o projeto para persistir.");
        return true;
    }

    private void copyFieldsToSelected() {
        selected.title = title.get().trim();
        selected.pattern = pattern.get().trim();
        selected.trigger = trigger.get();
        selected.targetChatEnv = targetChatEnv.get().trim();
        selected.script = script.get();
    }

    private void remove() {
        if (selected == null) { status.set("Selecione uma ação no canvas."); return; }
        project.flows.remove(selected);
        selected = null;
        title.set(""); pattern.set(""); script.set("");
        trigger.set("COMMAND");
        targetChatEnv.set("TG_BOT_TARGET_CHAT");
        refresh();
        status.set("Fluxo removido. Salve o projeto para persistir.");
    }

    private void save() {
        if (selected != null && !updateSelected()) return;
        try {
            FlowValidator.validate(project);
            for (var flow : project.flows) ActionCompiler.compile(flow.script);
            FlowStore.write(file, project);
            status.set("Salvo em " + file);
        }
        catch (Exception error) { status.set("Falha ao salvar: " + error.getMessage()); }
    }

    private void test() {
        if (selected != null && !updateSelected()) return;
        try {
            var flow = selected != null && "INTERVAL".equals(selected.trigger) ? selected
                    : FlowRouter.find(project, "COMMAND", sample.get())
                    .or(() -> FlowRouter.find(project, "TEXT", sample.get()))
                    .or(() -> FlowRouter.find(project, "CALLBACK", sample.get()))
                    .orElseThrow(() -> new IllegalArgumentException("Nenhum gatilho corresponde ao texto de teste"));
            Object result = ActionCompiler.compile(flow.script).run(new BotContext(1, 1, sample.get(), "teste"));
            status.set("Resposta: " + (result == null ? "(nula)" : result));
        } catch (Exception error) {
            status.set("Erro na ação: " + error.getMessage());
        }
    }

    private void chooseTrigger(String kind) {
        trigger.set(kind);
        if ("INTERVAL".equals(kind) && pattern.get().startsWith("/")) pattern.set("180");
        if ("DAILY".equals(kind) && (pattern.get().startsWith("/") || pattern.get().equals("180")))
            pattern.set("09:00,15:00,21:00");
    }

    private static InputProps field(String placeholder) {
        return new InputProps().placeHolder(placeholder).borderWidth(1).borderRadius(6);
    }
}
