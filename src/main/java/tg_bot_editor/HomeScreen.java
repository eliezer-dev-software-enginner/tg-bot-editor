package tg_bot_editor;

import megalodonte.base.components.Component;
import megalodonte.base.components.ScreenComponent;
import megalodonte.base.state.State;
import megalodonte.components.*;
import megalodonte.components.inputs.*;
import megalodonte.components.layout_components.*;
import megalodonte.props.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** The editor stores project paths; scripts and settings belong to each project. */
public final class HomeScreen implements ScreenComponent {
    private final RecentProjects recent = new RecentProjects();
    private final State<List<String>> recentPaths = State.of(List.of());
    private final State<String> recentChoice = State.of(null);
    private final State<String> projectLabel = State.of("Nenhum projeto aberto");
    private final State<String> status = State.of("Crie ou abra um projeto para começar.");
    private final State<String> title = State.of("");
    private final State<String> pattern = State.of("");
    private final State<String> trigger = State.of("COMMAND");
    private final State<String> targetChatEnv = State.of("TG_BOT_TARGET_CHAT");
    private final State<String> script = State.of("");
    private final State<String> environment = State.of("");
    private final State<String> sample = State.of("/start");
    private final State<String> newProjectName = State.of("");
    private final State<String> newScriptName = State.of("");
    private final State<String> newParentLabel = State.of(ProjectWorkspace.defaultProjectsDirectory().toString());
    private final State<Boolean> newProjectOpen = State.of(false);
    private final State<Boolean> newScriptOpen = State.of(false);
    private final FlowCanvas canvas = new FlowCanvas();
    private Path newParent = ProjectWorkspace.defaultProjectsDirectory();
    private Path file;
    private FlowProject project = new FlowProject();
    private FlowProject.Flow selected;

    public HomeScreen() {
        canvas.onSelect(this::select).onMove(move -> {
            project.flows.stream().filter(flow -> flow.id.equals(move.id())).findFirst().ifPresent(flow -> {
                flow.x = move.x(); flow.y = move.y(); refresh();
            });
        });
        try {
            refreshRecents();
            Path legacy = Path.of(System.getProperty("user.home"), "Documents", "TG-bot Editor", "bot.json");
            Path initial = recent.list().stream().filter(Files::isRegularFile).findFirst().orElse(null);
            if (initial == null && Files.isRegularFile(legacy)) initial = legacy;
            if (initial == null) initial = ProjectWorkspace.ensureStarter(ProjectWorkspace.defaultProjectsDirectory());
            load(initial);
        } catch (Exception error) { status.set("Não foi possível restaurar o projeto: " + error.getMessage()); }
        refresh();
    }

    @Override public Component render() {
        var heading = new Container(new ContainerProps().paddingAll(12).spacingOf(6)).children(
                new Text("TG-bot Editor", new TextProps().fontSize(26)), new Text(projectLabel), new Text(status),
                new Row(new RowProps().spacingOf(7)).children(
                        new Button("Novo projeto").onClick(() -> newProjectOpen.set(true)),
                        new Button("Abrir projeto").onClick(this::openProject),
                        new Button("Fechar projeto").onClick(this::closeProject),
                        new Button("Abrir pasta do projeto").onClick(this::openFolder),
                        new Button("Salvar projeto").onClick(this::save)),
                new Row(new RowProps().spacingOf(7)).children(
                        new Select<String>(new SelectProps().minWidth(230).maxWidth(230))
                                .items(recentPaths).value(recentChoice),
                        new Button("Abrir recente").onClick(this::openRecent),
                        new Button("+ Novo comando").onClick(this::add),
                        new Button("+ Script").onClick(() -> {
                            if (file == null) status.set("Abra um projeto antes de criar scripts.");
                            else newScriptOpen.set(true);
                        })));
        var inspector = new Container(new ContainerProps().paddingAll(16).spacingOf(10).width(330)).children(
                new Text("Propriedades", new TextProps().fontSize(20)),
                new Text("Título"), new Input(title, field("Nome da ação")),
                new Text("Script Java · retorne texto ou BotReply"), new JavaCodeEditor(script, 230),
                new Row(new RowProps().spacingOf(6)).children(
                        new Button("Aplicar").onClick(this::apply),
                        new Button("Excluir").onClick(this::remove)),
                new Text("Tipo de gatilho"), new Text(trigger),
                new Row(new RowProps().spacingOf(6)).children(
                        new Button("Comando").onClick(() -> chooseTrigger("COMMAND")),
                        new Button("Texto").onClick(() -> chooseTrigger("TEXT"))),
                new Row(new RowProps().spacingOf(6)).children(
                        new Button("Callback").onClick(() -> chooseTrigger("CALLBACK")),
                        new Button("Foto").onClick(() -> chooseTrigger("PHOTO"))),
                new Row(new RowProps().spacingOf(6)).children(
                        new Button("Intervalo").onClick(() -> chooseTrigger("INTERVAL")),
                        new Button("Horários diários").onClick(() -> chooseTrigger("DAILY"))),
                new Text("Padrão · comando, texto, minutos ou HH:mm"), new Input(pattern, field("/start")),
                new Text("Canal agendado · nome da variável"), new Input(targetChatEnv, field("TG_BOT_TARGET_CHAT")),
                new Text("Variáveis de ambiente do projeto"),
                new TextAreaInput(environment, field("NOME=valor\nOUTRA=valor").height(135).width(300)),
                new Text("Apenas local: .env não entra no Git."),
                new Text("Teste local (sem Telegram)"), new Input(sample, field("/start")),
                new Button("Executar teste").onClick(this::test));
        var workspace = new Row(new RowProps().spacingOf(12).fillHeight()).children(
                new Scroll(canvas).fillAvailableHeight(), new Scroll(inspector).fillAvailableHeight());
        var base = new Container(new ContainerProps().fillHeight()).children(heading, workspace);
        var newProject = new Container(new ContainerProps().paddingAll(24).spacingOf(12).width(430)).children(
                new Text("Novo projeto", new TextProps().fontSize(21)),
                new Text("Nome"), new Input(newProjectName, field("Meu bot")),
                new Text("Pasta onde criar o projeto"), new Text(newParentLabel),
                new Button("Escolher outra pasta").onClick(this::chooseParent),
                new Button("Criar projeto").onClick(this::createProject));
        var newScript = new Container(new ContainerProps().paddingAll(24).spacingOf(12).width(430)).children(
                new Text("Novo script Java", new TextProps().fontSize(21)),
                new Text("Nome da classe"), new Input(newScriptName, field("MeuNovoScript")),
                new Text("Use MeuNovoScript.run(); em um comando."),
                new Button("Criar script").onClick(this::createScript));
        return new Stack().children(base, new Modal(newProjectOpen, newProject),
                new Modal(newScriptOpen, newScript)).fillHeight();
    }

    private void refresh() {
        canvas.show(project.flows.stream().map(flow ->
                new FlowCanvas.Node(flow.id, flow.title, flow.trigger + " · " + flow.pattern, flow.x, flow.y)).toList());
    }

    private void select(String id) {
        if (selected != null && !selected.id.equals(id)) { copyFieldsToSelected(); refresh(); }
        selected = project.flows.stream().filter(flow -> flow.id.equals(id)).findFirst().orElse(null);
        if (selected == null) return;
        title.set(selected.title); pattern.set(selected.pattern); trigger.set(selected.trigger);
        targetChatEnv.set(selected.targetChatEnv == null ? "TG_BOT_TARGET_CHAT" : selected.targetChatEnv);
        script.set(selected.script); canvas.select(id); status.set("Editando " + selected.title);
    }

    private void add() {
        if (file == null) { status.set("Crie ou abra um projeto primeiro."); return; }
        int index = project.flows.size();
        var flow = new FlowProject.Flow(UUID.randomUUID().toString(), "Nova ação", "/comando" + (index + 1),
                35 + (index % 3) * 315, 30 + (index / 3) * 125);
        project.flows.add(flow); refresh(); select(flow.id);
    }

    private void apply() { updateSelected(); }
    private boolean updateSelected() {
        if (selected == null) { status.set("Selecione uma ação no canvas."); return false; }
        if (pattern.get().isBlank() || script.get().isBlank()) {
            status.set("Informe o gatilho e o script Java."); return false;
        }
        copyFieldsToSelected(); refresh(); canvas.select(selected.id);
        status.set("Fluxo atualizado. Salve o projeto para persistir."); return true;
    }
    private void copyFieldsToSelected() {
        selected.title = title.get().trim(); selected.pattern = pattern.get().trim();
        selected.trigger = trigger.get(); selected.targetChatEnv = targetChatEnv.get().trim();
        selected.script = script.get();
    }
    private void remove() {
        if (selected == null) { status.set("Selecione um comando para excluir."); return; }
        project.flows.remove(selected); selected = null;
        title.set(""); pattern.set(""); script.set(""); trigger.set("COMMAND");
        targetChatEnv.set("TG_BOT_TARGET_CHAT"); refresh(); canvas.select(null);
        if (!project.flows.isEmpty()) select(project.flows.getFirst().id);
        status.set("Comando excluído. Salve o projeto para persistir.");
    }
    private void save() {
        if (file == null) { status.set("Nenhum projeto aberto."); return; }
        try {
            if (selected != null && !updateSelected()) return;
            FlowValidator.validate(project);
            ProjectEnvironment.useDraft(file.getParent(), environment.get());
            ActionCompiler.compileProject(file.getParent(), project);
            FlowStore.write(file, project); ProjectEnvironment.write(file.getParent(), environment.get());
            recent.remember(file); refreshRecents(); status.set("Projeto salvo: " + file);
        } catch (Exception error) { status.set("Falha ao salvar: " + error.getMessage()); }
    }
    private void test() {
        if (file == null || selected == null) { status.set("Selecione uma ação em um projeto aberto."); return; }
        if (!updateSelected()) return;
        try {
            ProjectEnvironment.useDraft(file.getParent(), environment.get());
            var actions = ActionCompiler.compileProject(file.getParent(), project);
            Object result = actions.get(selected.id).run(new BotContext(1, 1, sample.get(), "teste"));
            status.set("Resposta: " + (result == null ? "(nula)" : result));
        } catch (Exception error) { status.set("Erro na ação: " + error.getMessage()); }
    }
    private void chooseTrigger(String kind) {
        trigger.set(kind);
        if ("INTERVAL".equals(kind) && pattern.get().startsWith("/")) pattern.set("180");
        if ("DAILY".equals(kind) && (pattern.get().startsWith("/") || pattern.get().equals("180")))
            pattern.set("09:00,15:00,21:00");
        if ("PHOTO".equals(kind)) pattern.set("*");
    }

    private void refreshRecents() throws Exception {
        recentPaths.set(recent.list().stream().map(Path::toString).toList());
    }
    private void load(Path target) throws Exception {
        Path absolute = target.toAbsolutePath().normalize();
        if (!absolute.getFileName().toString().equals("bot.json"))
            throw new IllegalArgumentException("Selecione o arquivo bot.json de um projeto.");
        var loaded = FlowStore.read(absolute);
        if (ProjectWorkspace.mergeStarterAds(loaded)) FlowStore.write(absolute, loaded);
        ProjectWorkspace.initializeLegacyScripts(absolute.getParent(), loaded);
        ProjectEnvironment.activate(absolute.getParent());
        project = loaded; file = absolute; selected = null;
        environment.set(ProjectEnvironment.read(file.getParent()));
        projectLabel.set(project.name + " · " + file.getParent());
        refresh(); canvas.select(null);
        if (!project.flows.isEmpty()) select(project.flows.getFirst().id);
        else { title.set(""); pattern.set(""); script.set(""); }
        recent.remember(file); refreshRecents(); status.set("Projeto aberto: " + file);
    }
    private void openProject() {
        try {
            var chosen = FilePicker.chooseFile(canvas.getJavaFxNode().getScene().getWindow(),
                    "Abrir projeto TG-bot", "Projeto TG-bot", List.of("bot.json", "*.json"));
            if (chosen.isEmpty()) return;
            persistDraft(); load(Path.of(chosen.get()));
        } catch (Exception error) { status.set("Não foi possível abrir o projeto: " + error.getMessage()); }
    }
    private void openRecent() {
        if (recentChoice.get() == null) { status.set("Escolha um projeto recente."); return; }
        try { persistDraft(); load(Path.of(recentChoice.get())); }
        catch (Exception error) { status.set("Não foi possível abrir o projeto recente: " + error.getMessage()); }
    }
    private void closeProject() {
        if (file == null) { status.set("Nenhum projeto aberto."); return; }
        try { persistDraft(); }
        catch (Exception error) { status.set("Não foi possível fechar sem salvar o rascunho: " + error.getMessage()); return; }
        file = null; selected = null; project = new FlowProject();
        projectLabel.set("Nenhum projeto aberto"); title.set(""); pattern.set("");
        script.set(""); environment.set(""); refresh(); canvas.select(null);
        status.set("Projeto fechado. O último rascunho foi salvo.");
    }
    private void persistDraft() throws Exception {
        if (file == null) return;
        if (selected != null) copyFieldsToSelected();
        ProjectEnvironment.parse(environment.get()); FlowStore.write(file, project);
        ProjectEnvironment.write(file.getParent(), environment.get());
    }
    private void chooseParent() {
        try {
            FilePicker.chooseDirectory(canvas.getJavaFxNode().getScene().getWindow(), "Pasta para novo projeto")
                    .ifPresent(path -> { newParent = path; newParentLabel.set(path.toString()); });
        } catch (Exception error) { status.set("Não foi possível escolher a pasta: " + error.getMessage()); }
    }
    private void createProject() {
        try {
            persistDraft(); Path created = ProjectWorkspace.create(newParent, newProjectName.get());
            newProjectOpen.set(false); newProjectName.set(""); load(created);
        } catch (Exception error) { status.set("Não foi possível criar o projeto: " + error.getMessage()); }
    }
    private void createScript() {
        if (file == null) { status.set("Abra um projeto primeiro."); return; }
        try {
            Path created = ProjectWorkspace.createScript(file.getParent(), newScriptName.get());
            newScriptOpen.set(false); newScriptName.set("");
            status.set("Script criado: " + created + " · use "
                    + created.getFileName().toString().replace(".java", ".run();"));
        } catch (Exception error) { status.set("Não foi possível criar o script: " + error.getMessage()); }
    }
    private void openFolder() {
        if (file == null) { status.set("Nenhum projeto aberto."); return; }
        try { FilePicker.openDirectory(file.getParent()); }
        catch (Exception error) { status.set("Não foi possível abrir a pasta: " + error.getMessage()); }
    }
    private static InputProps field(String placeholder) {
        return new InputProps().placeHolder(placeholder).borderWidth(1).borderRadius(6);
    }
}
