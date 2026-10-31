package tg_bot_editor;

import megalodonte.base.components.Ref;
import megalodonte.base.state.State;
import megalodonte.components.FilePicker;
import megalodonte.components.FlowCanvas;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** The editor stores project paths; scripts and settings belong to each project. */
final class HomeViewModel {
    private final RecentProjects recent = new RecentProjects();
    final State<List<String>> recentPaths = State.of(List.of());
    final State<String> recentChoice = State.of(null);
    final State<String> projectLabel = State.of("Nenhum projeto aberto");
    final State<String> status = State.of("Crie ou abra um projeto para começar.");
    final State<String> botActionLabel = State.of("▶ Executar");
    final State<String> title = State.of("");
    final State<String> description = State.of("");
    final State<String> pattern = State.of("");
    final State<String> trigger = State.of("COMMAND");
    final State<String> script = State.of("");
    final State<String> environment = State.of("");
    final State<String> sample = State.of("/start");
    final State<String> newProjectName = State.of("");
    final State<String> newScriptName = State.of("");
    final State<String> newParentLabel = State.of(ProjectWorkspace.defaultProjectsDirectory().toString());
    final State<Boolean> newProjectOpen = State.of(false);
    final State<Boolean> newScriptOpen = State.of(false);
    final Ref<FlowCanvas> canvas = new Ref<>(new FlowCanvas());
    private Path newParent = ProjectWorkspace.defaultProjectsDirectory();
    private Path file;
    private FlowProject project = new FlowProject();
    private FlowProject.Flow selected;
    private volatile Process botProcess;

    HomeViewModel() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Process running = botProcess;
            if (running != null && running.isAlive()) running.destroy();
        }, "tg-bot-editor-stop-runner"));
        canvas.current().onSelect(this::select).onMove(move -> {
            project.flows.stream().filter(flow -> flow.id.equals(move.id())).findFirst().ifPresent(flow -> {
                flow.x = move.x(); flow.y = move.y(); refresh();
            });
        });
        try {
            refreshRecents();
            Path starter = ProjectWorkspace.migrateStarter(ProjectWorkspace.defaultProjectsDirectory());
            Path legacy = Path.of(System.getProperty("user.home"), "Documents", "TG-bot Editor", "bot.json");
            Path initial = recent.list().stream().filter(Files::isRegularFile).findFirst().orElse(null);
            if (initial == null && Files.isRegularFile(legacy)) initial = legacy;
            if (initial == null) initial = starter == null
                    ? ProjectWorkspace.ensureStarter(ProjectWorkspace.defaultProjectsDirectory()) : starter;
            load(initial);
        } catch (Exception error) { status.set("Não foi possível restaurar o projeto: " + error.getMessage()); }
        refresh();
    }

    void openNewScript() {
        if (file == null) status.set("Abra um projeto antes de criar scripts.");
        else newScriptOpen.set(true);
    }

    private void refresh() {
        canvas.current().show(project.flows.stream().map(flow ->
                new FlowCanvas.Node(flow.id, flow.title, flow.trigger + " · " + flow.pattern, flow.x, flow.y)).toList());
    }

    private void select(String id) {
        if (selected != null && !selected.id.equals(id)) { copyFieldsToSelected(); refresh(); }
        selected = project.flows.stream().filter(flow -> flow.id.equals(id)).findFirst().orElse(null);
        if (selected == null) return;
        title.set(selected.title); description.set(selected.description == null ? "" : selected.description);
        pattern.set(selected.pattern); trigger.set(selected.trigger);
        script.set(selected.script); canvas.current().select(id); status.set("Editando " + selected.title);
    }

    void add() {
        if (file == null) { status.set("Crie ou abra um projeto primeiro."); return; }
        int index = project.flows.size();
        var flow = new FlowProject.Flow(UUID.randomUUID().toString(), "Nova ação", "/comando" + (index + 1),
                canvas.current().viewToModelX(35 + (index % 3) * 315),
                canvas.current().viewToModelY(30 + (index / 3) * 125));
        project.flows.add(flow); refresh(); select(flow.id);
    }

    void apply() { updateSelected(); }
    private boolean updateSelected() {
        if (selected == null) { status.set("Selecione uma ação no canvas."); return false; }
        if (pattern.get().isBlank() || script.get().isBlank()) {
            status.set("Informe o gatilho e o script Java."); return false;
        }
        copyFieldsToSelected(); refresh(); canvas.current().select(selected.id);
        status.set("Fluxo atualizado. Salve o projeto para persistir."); return true;
    }
    private void copyFieldsToSelected() {
        selected.title = title.get().trim(); selected.description = description.get().trim();
        selected.pattern = pattern.get().trim();
        selected.trigger = trigger.get();
        selected.script = script.get();
    }
    void remove() {
        if (selected == null) { status.set("Selecione um comando para excluir."); return; }
        project.flows.remove(selected); selected = null;
        title.set(""); description.set(""); pattern.set(""); script.set(""); trigger.set("COMMAND");
        refresh(); canvas.current().select(null);
        if (!project.flows.isEmpty()) select(project.flows.getFirst().id);
        status.set("Comando excluído. Salve o projeto para persistir.");
    }
    void save() { saveProject(); }
    private boolean saveProject() {
        if (file == null) { status.set("Nenhum projeto aberto."); return false; }
        try {
            if (selected != null && !updateSelected()) return false;
            FlowValidator.validate(project);
            ProjectEnvironment.useDraft(file.getParent(), environment.get());
            ActionCompiler.compileProject(file.getParent(), project);
            FlowStore.write(file, project); ProjectEnvironment.write(file.getParent(), environment.get());
            recent.remember(file); refreshRecents(); status.set("Projeto salvo: " + file);
            return true;
        } catch (Exception error) { status.set("Falha ao salvar: " + error.getMessage()); return false; }
    }
    void toggleBot() {
        Process running = botProcess;
        if (running != null && running.isAlive()) {
            if (!"■ Parando...".equals(botActionLabel.get())) stopBot();
            return;
        }
        startBot();
    }
    private void startBot() {
        if (botProcess != null && botProcess.isAlive()) {
            status.set("O bot já está em execução. Use Parar antes de iniciá-lo novamente.");
            return;
        }
        if (!saveProject()) return;
        if (ProjectEnvironment.get("TG_BOT_TOKEN") == null
                || ProjectEnvironment.get("TG_BOT_TOKEN").isBlank()) {
            status.set("Defina TG_BOT_TOKEN nas variáveis do projeto antes de executar.");
            return;
        }
        try {
            Path log = file.getParent().resolve("data").resolve("bot-runner.log");
            Files.createDirectories(log.getParent());
            String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
            Process process = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                    BotRunner.class.getName(), file.toString())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()))
                    .start();
            botProcess = process;
            botActionLabel.set("■ Parar");
            status.set("Bot em execução. Saída: " + log);
            process.onExit().thenAccept(exited -> megalodonte.base.UI.runOnUi(() -> {
                if (botProcess == exited) {
                    botProcess = null;
                    botActionLabel.set("▶ Executar");
                    status.set("Bot encerrado (código " + exited.exitValue() + "). Saída: " + log);
                }
            }));
        } catch (Exception error) { status.set("Não foi possível executar o bot: " + error.getMessage()); }
    }
    private void stopBot() {
        Process running = botProcess;
        if (running == null || !running.isAlive()) { status.set("Nenhum bot em execução."); return; }
        running.destroy();
        botActionLabel.set("■ Parando...");
        status.set("Parando o bot...");
    }
    void exportCommands() {
        if (file == null) { status.set("Crie ou abra um projeto primeiro."); return; }
        try {
            if (selected != null) copyFieldsToSelected();
            Path exported = CommandExporter.export(project, file.getParent());
            status.set("Comandos exportados: " + exported);
        } catch (Exception error) { status.set("Falha ao exportar comandos: " + error.getMessage()); }
    }
    void test() {
        if (file == null || selected == null) { status.set("Selecione uma ação em um projeto aberto."); return; }
        if (!updateSelected()) return;
        try {
            ProjectEnvironment.useDraft(file.getParent(), environment.get());
            var actions = ActionCompiler.compileProject(file.getParent(), project);
            Object result = actions.get(selected.id).run(new BotContext(1, 1, sample.get(), "teste"));
            status.set("Resposta: " + (result == null ? "(nula)" : result));
        } catch (Exception error) { status.set("Erro na ação: " + error.getMessage()); }
    }
    void chooseTrigger(String kind) {
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
        stopRunningBot();
        if (ProjectWorkspace.mergeStarterAds(loaded)) FlowStore.write(absolute, loaded);
        ProjectWorkspace.initializeLegacyScripts(absolute.getParent(), loaded);
        ProjectEnvironment.activate(absolute.getParent());
        project = loaded; file = absolute; selected = null;
        environment.set(ProjectEnvironment.read(file.getParent()));
        projectLabel.set("Projeto aberto: " + file.getParent().getFileName());
        canvas.current().resetView(); refresh(); canvas.current().select(null);
        if (!project.flows.isEmpty()) select(project.flows.getFirst().id);
        else { title.set(""); description.set(""); pattern.set(""); script.set(""); }
        recent.remember(file); refreshRecents(); status.set("Pasta: " + file.getParent());
    }
    void openProject() {
        try {
            var chosen = FilePicker.chooseFile(null,
                    "Abrir projeto TG-bot", "Projeto TG-bot", List.of("bot.json", "*.json"));
            if (chosen.isEmpty()) return;
            persistDraft(); load(Path.of(chosen.get()));
        } catch (Exception error) { status.set("Não foi possível abrir o projeto: " + error.getMessage()); }
    }
    void openRecent() {
        if (recentChoice.get() == null) { status.set("Escolha um projeto recente."); return; }
        try { persistDraft(); load(Path.of(recentChoice.get())); }
        catch (Exception error) { status.set("Não foi possível abrir o projeto recente: " + error.getMessage()); }
    }
    void closeProject() {
        if (file == null) { status.set("Nenhum projeto aberto."); return; }
        try { persistDraft(); }
        catch (Exception error) { status.set("Não foi possível fechar sem salvar o rascunho: " + error.getMessage()); return; }
        stopRunningBot();
        file = null; selected = null; project = new FlowProject();
        projectLabel.set("Nenhum projeto aberto"); title.set(""); description.set(""); pattern.set("");
        script.set(""); environment.set(""); canvas.current().resetView(); refresh(); canvas.current().select(null);
        status.set("Projeto fechado. O último rascunho foi salvo.");
    }
    private void stopRunningBot() {
        Process running = botProcess;
        if (running != null && running.isAlive()) {
            running.destroy();
            try {
                if (!running.waitFor(3, TimeUnit.SECONDS)) {
                    running.destroyForcibly();
                    running.waitFor();
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                running.destroyForcibly();
            }
        }
        botProcess = null;
        botActionLabel.set("▶ Executar");
    }
    private void persistDraft() throws Exception {
        if (file == null) return;
        if (selected != null) copyFieldsToSelected();
        ProjectEnvironment.parse(environment.get()); FlowStore.write(file, project);
        ProjectEnvironment.write(file.getParent(), environment.get());
    }
    void chooseParent() {
        try {
            FilePicker.chooseDirectory(null, "Pasta para novo projeto")
                    .ifPresent(path -> { newParent = path; newParentLabel.set(path.toString()); });
        } catch (Exception error) { status.set("Não foi possível escolher a pasta: " + error.getMessage()); }
    }
    void createProject() {
        try {
            persistDraft(); Path created = ProjectWorkspace.create(newParent, newProjectName.get());
            newProjectOpen.set(false); newProjectName.set(""); load(created);
        } catch (Exception error) { status.set("Não foi possível criar o projeto: " + error.getMessage()); }
    }
    void createScript() {
        if (file == null) { status.set("Abra um projeto primeiro."); return; }
        try {
            Path created = ProjectWorkspace.createScript(file.getParent(), newScriptName.get());
            newScriptOpen.set(false); newScriptName.set("");
            status.set("Script criado: " + created + " · use "
                    + created.getFileName().toString().replace(".java", ".run();"));
        } catch (Exception error) { status.set("Não foi possível criar o script: " + error.getMessage()); }
    }
    void openFolder() {
        if (file == null) { status.set("Nenhum projeto aberto."); return; }
        try { FilePicker.openDirectory(file.getParent()); }
        catch (Exception error) { status.set("Não foi possível abrir a pasta: " + error.getMessage()); }
    }
}
