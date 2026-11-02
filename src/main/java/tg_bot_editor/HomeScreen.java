package tg_bot_editor;

import megalodonte.base.components.Component;
import megalodonte.base.components.ScreenComponent;
import megalodonte.base.state.ForEachState;
import megalodonte.base.state.ReadableState;
import megalodonte.components.*;
import megalodonte.components.inputs.*;
import megalodonte.components.layout_components.*;
import megalodonte.props.*;
import java.util.List;

/** Declarative editor view; state and project operations live in HomeViewModel. */
public final class HomeScreen implements ScreenComponent {
    private final HomeViewModel model = new HomeViewModel();

    @Override public Component render() {
        return new Stack().children(
                new Container(new ContainerProps().fillHeight().themeBackground()).children(
                        heading(),
                        new Scroll(diagramCards()).fillAvailableHeight()),
                new Modal(model.newProjectOpen, newProjectDialog()),
                new Modal(model.newScriptOpen, newScriptDialog()),
                new Modal(model.legacyOpen, legacyEditorDialog()),
                new Modal(model.diagrams.open, diagramDialog(model.diagrams, false))
        ).fillHeight();
    }

    private Component heading() {
        return new Container(new ContainerProps().paddingAll(12).spacingOf(6).themeBackground()).children(
                new Text("TG-bot Editor", new TextProps().fontSize(26)),
                new Text(model.projectLabel),
                new Text(model.status),
                new Row(new RowProps().spacingOf(7)).children(
                        new Button("Novo projeto").onClick(() -> model.newProjectOpen.set(true)),
                        new Button("Abrir projeto").onClick(model::openProject),
                        new Button("Fechar projeto").onClick(model::closeProject),
                        new Button("Abrir pasta do projeto").onClick(model::openFolder),
                        new Button("Salvar projeto").onClick(model::save),
                        new Button(model.botActionLabel, new ButtonProps().primary()).onClick(model::toggleBot),
                        new Button("Exportar comandos").onClick(model::exportCommands)),
                new Row(new RowProps().spacingOf(7)).children(
                        new Button("+ Script").onClick(model::openNewScript)));
    }

    private Component diagramCards() {
        return new Container(new ContainerProps().paddingAll(16).spacingOf(12).themeBackground()).children(
                new Text("Diagramas do projeto", new TextProps().fontSize(21)),
                new Text("Cada card abre um fluxo visual independente."),
                new FlowRow(new FlowRowProps().spacingOf(12).verticalSpacing(12).fillWidth())
                        .items(new ForEachState<DiagramViewModel.Summary, Card>() {
                            @Override public ReadableState<List<DiagramViewModel.Summary>> getState() {
                                return model.diagrams.summaries;
                            }
                            @Override public List<Card> getComponents() {
                                return model.diagrams.summaries.get().stream().map(HomeScreen.this::diagramCard).toList();
                            }
                        }));
    }

    private Card diagramCard(DiagramViewModel.Summary summary) {
        return new Card(new Container(new ContainerProps().paddingAll(14).spacingOf(9).themeSurface()).children(
                new Text(summary.name(), new TextProps().fontSize(18)),
                new Text("Gatilho: " + summary.trigger()),
                new Text(summary.steps() + " componentes · " + (summary.enabled() ? "Ativo" : "Rascunho")),
                new Button("Abrir diagrama").onClick(() -> model.openDiagram(summary.id()))),
                new CardProps().width(240));
    }

    private Component legacyEditorDialog() {
        return new Container(new ContainerProps().paddingAll(14).spacingOf(8).width(1080).height(550).themeSurface()).children(
                new Row(new RowProps().spacingOf(8)).children(
                        new Text("Comandos antigos", new TextProps().fontSize(20)),
                        new Button("Salvar projeto").onClick(model::save),
                        new Button("Fechar").onClick(() -> model.legacyOpen.set(false))),
                new Row(new RowProps().spacingOf(10).fillHeight()).children(
                        model.canvas.current(), new Scroll(inspector()).fillAvailableHeight()));
    }

    private Component inspector() {
        return new Container(new ContainerProps().paddingAll(16).spacingOf(10).width(330).themeSurface()).children(
                new Text("Propriedades", new TextProps().fontSize(20)),
                new Text("Título"), Components.input(model.title, "Nome da ação"),
                new Text("Descrição do comando"), Components.input(model.description, "O que este comando faz"),
                new Row(new RowProps().spacingOf(6)).children(
                        new Text("Script Java · retorne texto ou BotReply"),
                        new Button("?").tooltip("Exemplo: return BotReply.text(\"Escolha\").withButtons(java.util.List.of(BotButton.callback(\"Abrir\", \"menu:abrir\"))); Crie um fluxo CALLBACK com padrão menu:abrir para tratar o clique.")),
                new JavaCodeEditor(model.script, 230, model.javaKeywords, List.of("context")),
                new Row(new RowProps().spacingOf(6)).children(
                        new Button("Aplicar").tooltip("Aplica as alterações da ação selecionada ao projeto. Salve para persistir no disco.").onClick(model::apply),
                        new Button("Excluir").tooltip("Remove a ação selecionada do projeto. Salve para persistir no disco.").onClick(model::remove)),
                new Text("Tipo de gatilho"), new Text(model.trigger),
                new Row(new RowProps().spacingOf(6)).children(
                        new Button("Comando").tooltip("Executa a ação quando receber um comando como /start.").onClick(() -> model.chooseTrigger("COMMAND")),
                        new Button("Texto").tooltip("Texto exato, prefixo como pedido:* ou * para qualquer texto. O asterisco só funciona no fim do padrão.").onClick(() -> model.chooseTrigger("TEXT"))),
                new Row(new RowProps().spacingOf(6)).children(
                        new Button("Callback").tooltip("Use BotButton.callback(\"Detalhes\", \"menu:detalhes\") na resposta; crie outro fluxo CALLBACK com padrão menu:detalhes. menu:* recebe todos os cliques desse prefixo.").onClick(() -> model.chooseTrigger("CALLBACK")),
                        new Button("Foto").tooltip("Use * como padrão para receber qualquer foto; context.photoRef() contém a referência da imagem.").onClick(() -> model.chooseTrigger("PHOTO"))),
                new Row(new RowProps().spacingOf(6)).children(
                        new Button("Intervalo").tooltip("Executa a ação periodicamente; informe o intervalo em minutos no campo Padrão.").onClick(() -> model.chooseTrigger("INTERVAL")),
                        new Button("Horários diários").tooltip("Executa a ação nos horários HH:mm informados no campo Padrão.").onClick(() -> model.chooseTrigger("DAILY"))),
                new Row(new RowProps().spacingOf(6)).children(
                        new Text("Padrão · comando, texto, minutos ou HH:mm"),
                        new Button("?").tooltip("* aceita qualquer TEXT, CALLBACK, PHOTO ou DOCUMENT. pedido:* aceita valores que começam com pedido:. Exato tem prioridade sobre prefixo, e prefixo sobre *. COMMAND usa /comando; INTERVAL usa minutos; DAILY usa HH:mm.")),
                Components.input(model.pattern, "/start"),
                new Text("Variáveis de ambiente do projeto"),
                Components.environment(model.environment),
                new Text("Apenas local: .env não entra no Git."),
                new Text("Teste local (sem Telegram)"),
                Components.input(model.sample, "/start"),
                new Button("Executar teste").tooltip("Executa a ação selecionada localmente com a mensagem de exemplo, sem enviar ao Telegram.").onClick(model::test));
    }

    private Component newProjectDialog() {
        return new Container(new ContainerProps().paddingAll(24).spacingOf(12).width(430).themeSurface()).children(
                new Text("Novo projeto", new TextProps().fontSize(21)),
                new Text("Nome"), Components.input(model.newProjectName, "Meu bot"),
                new Text("Pasta onde criar o projeto"), new Text(model.newParentLabel),
                new Button("Escolher outra pasta").onClick(model::chooseParent),
                new Button("Criar projeto").onClick(model::createProject));
    }

    private Component newScriptDialog() {
        return new Container(new ContainerProps().paddingAll(24).spacingOf(12).width(430).themeSurface()).children(
                new Text("Novo script Java", new TextProps().fontSize(21)),
                new Text("Nome da classe"), Components.input(model.newScriptName, "MeuNovoScript"),
                new Text("Use MeuNovoScript.run(); em um comando."),
                new Button("Criar script").onClick(model::createScript));
    }

    private Component diagramDialog(DiagramViewModel diagram, boolean subflow) {
        return new Stack().children(
                new Container(new ContainerProps().paddingAll(16).spacingOf(9).width(1080).height(545).themeSurface()).children(
                        new Text(subflow ? "Subfluxo" : "Diagramas", new TextProps().fontSize(22)),
                        subflow ? new Row(new RowProps().spacingOf(8)).children(
                                new Text("Nome do subfluxo"), Components.input(diagram.name, "Nome do subfluxo"))
                                : new Row(new RowProps().spacingOf(8)).children(
                                new Select<String>().items(diagram.available).value(diagram.chosen),
                                new Button("Abrir diagrama").onClick(model::selectDiagram),
                                new Text("Nome do diagrama"), Components.input(diagram.name, "Nome do diagrama")),
                        new Row(new RowProps().spacingOf(8)).children(
                                new Button("Salvar diagrama").onClick(diagram::save),
                                new Button("Prévia").onClick(diagram::preview),
                                new Button("Fechar").onClick(diagram::close)),
                        new Row(new RowProps().spacingOf(10).fillHeight()).children(
                                diagramPalette(), diagram.canvas.current(),
                                new Scroll(new Container(new ContainerProps().paddingAll(12).spacingOf(8)
                                        .width(350).themeSurface()).children(
                                        new Text("Propriedades", new TextProps().fontSize(19)),
                                        new Text(diagram.selectedLabel),
                                        new StateList<>(diagram.selectedTypes, type -> componentProperties(diagram, type), 0),
                                        new Text(diagram.status))).fillAvailableHeight())),
                new Modal(diagram.expandedActionOpen, expandedActionDialog(diagram)),
                new Modal(diagram.subflowOpen, new StateList<>(diagram.subflowWindows,
                        child -> diagramDialog(child, true), 0)));
    }

    private Component diagramPalette() {
        return new Container(new ContainerProps().paddingAll(8).spacingOf(8).width(175).fillHeight().themeSurface()).children(
                new Text("Componentes", new TextProps().fontSize(18)),
                new Text("Arraste para o diagrama"),
                new Scroll(new Container(new ContainerProps().spacingOf(7)).children(
                        new Text("Início"),
                        new DiagramPaletteItem("Callback", "CALLBACK"),
                        new DiagramPaletteItem("Timer", "TIMER"),
                        new Text("Etapas"),
                        new DiagramPaletteItem("Composição", "COMPOSITION"),
                        new DiagramPaletteItem("Rest HTTP", "REST_HTTP"),
                        new DiagramPaletteItem("Converter", "CONVERTER"),
                        new DiagramPaletteItem("Java", "JAVA"),
                        new DiagramPaletteItem("Generator", "GENERATOR"),
                        new DiagramPaletteItem("Resposta síncrona", "RESPONSE"),
                        new DiagramPaletteItem("JDBC", "JDBC"),
                        new DiagramPaletteItem("JsonDB", "JSON_DB"),
                        new DiagramPaletteItem("Splitter", "SPLITTER")
                )).fillAvailableHeight());
    }

    private Component componentProperties(DiagramViewModel diagram, String type) {
        if ("EDGE".equals(type)) return edgeProperties(diagram);
        return new Container(new ContainerProps().spacingOf(8)).children(
                new Text("Nome do componente"), Components.input(diagram.componentName, "Nome do componente"),
                specificProperties(diagram, type));
    }

    private Component edgeProperties(DiagramViewModel diagram) {
        return new Container(new ContainerProps().spacingOf(8)).children(
                new Text("Nome da condição"), Components.input(diagram.edgeName, "Nome opcional"),
                new Text("Tipo da conexão"), new Select<String>().items(diagram.edgeKinds).value(diagram.edgeKind),
                new Text("ALWAYS: sempre · IF: condição · ELSE: alternativa"),
                new StateList<>(diagram.edgeConditionForm, ignored -> new Container(new ContainerProps().spacingOf(8)).children(
                        new Text("Dado de entrada"), Components.input(diagram.edgeLeft, "{{output.image}}"),
                        new Text("Operação"), new Select<String>().items(diagram.edgeOperators).value(diagram.edgeOperator),
                        new Text("Valor de comparação"), Components.input(diagram.edgeRight, "'' para vazio"),
                        new Text("Referências: {{input.campo}} ou {{output.campo}}"),
                        new Text("EQ = igual; NE = diferente; '' = vazio.")), 0),
                new Row(new RowProps().spacingOf(6)).children(
                        new Button("Aplicar condição").tooltip("Atualiza a conexão selecionada; salve o diagrama para gravar.").onClick(diagram::applyEdge),
                        new Button("Excluir conexão").tooltip("Remove a conexão selecionada; salve o diagrama para gravar.").onClick(diagram::deleteEdge)));
    }

    private Component specificProperties(DiagramViewModel diagram, String type) {
        return switch (type) {
            case "COMPOSITION" -> new Container(new ContainerProps().spacingOf(8)).children(
                    new Text("Elementos da mensagem"),
                    new Text("Use {{input.campo}} ou {{output.campo}}."),
                    new StateList<>(diagram.compositionItems, item -> compositionItem(diagram, item), 8),
                    new Button("+ Adicionar elemento").tooltip("Adiciona texto, imagem, link ou botão à mensagem.").onClick(diagram::addCompositionItem));
            case "REST_HTTP" -> new Container(new ContainerProps().spacingOf(8)).children(
                    new Text("URL"), Components.input(diagram.restUrl, "https://exemplo.com/api"),
                    new Text("Variável de ambiente da URL (opcional)"),
                    Components.input(diagram.restUrlEnv, "Ex.: API_URL"),
                    new Text("Quando definida, substitui a URL acima."),
                    new Text("Método"), new Select<String>().items(diagram.restMethods).value(diagram.restMethod),
                    new Text("Corpo opcional (UTF-8)"), new TextAreaInput(diagram.restBody,
                            new InputProps().height(100).width(320)),
                    new Text("Resposta: status, body e headers na próxima action."));
            case "JSON_DB" -> new Container(new ContainerProps().spacingOf(8)).children(
                    new Text("Operação"), new Select<String>().items(diagram.jsonOperations).value(diagram.jsonOperation),
                    new Text("Caminho do JSON"), Components.input(diagram.jsonPath, "${TG_BOT_DATABASE_FILE}"),
                    new Text("Arquivo .json ou ${VARIÁVEL} do ambiente."),
                    new Text("Seção"), Components.input(diagram.jsonSection, "Nome da seção"),
                    new Text("Arquivo legado relativo (opcional)"), Components.input(diagram.jsonLegacy, "data/arquivo.json"),
                    new Text("Chave"), Components.input(diagram.jsonKey, "{{output.key}}"),
                    new Text("Registro"), Components.input(diagram.jsonRecord, "{{output.record}}"),
                    new Text("Campos de identificação"), Components.input(diagram.jsonIdentity, "id, outroCampo"),
                    new Text("Valor retornado"), Components.input(diagram.jsonResult, "{{output}}"));
            case "TIMER" -> new Container(new ContainerProps().spacingOf(8)).children(
                    new Text("Intervalo em minutos"), Components.input(diagram.timerInterval, "10"),
                    new Text("Executa ao iniciar e depois de cada intervalo."));
            case "CALLBACK" -> new Container(new ContainerProps().spacingOf(8)).children(
                    new Text("Tipo de evento"), new Select<String>().items(diagram.eventTypes).value(diagram.event),
                    new Text("Padrão"), Components.input(diagram.pattern, "/start ou ads:*"),
                    new Text("Use * para todos ou prefixo:* para filtrar callbacks."));
            case "RESPONSE" -> new Container(new ContainerProps().spacingOf(8)).children(
                    new Text("Texto da resposta"), new TextAreaInput(diagram.response,
                            new InputProps().height(230).width(320)),
                    new Text("Use {{output}} para enviar o retorno da etapa Java."));
            case "JAVA" -> new Container(new ContainerProps().spacingOf(8)).children(
                    new Row(new RowProps().spacingOf(8)).children(
                            new Text("Action"),
                            new Button("⛶").tooltip("Expandir o editor da action para visualizar e editar o código.")
                                    .onClick(() -> diagram.expandedActionOpen.set(true))),
                    new StateList<>(diagram.actionHints, hint -> new Text(hint), 2),
                    new JavaCodeEditor(diagram.javaScript, 230,
                            model.javaKeywords, List.of("context")));
            case "SPLITTER" -> new Container(new ContainerProps().spacingOf(8)).children(
                    new Text("Lista de entrada"), Components.input(diagram.splitterArray, "{{output}}"),
                    new Text("Use {{output}} para percorrer a lista retornada pela etapa anterior."),
                    new Text("Agrupamento das respostas"),
                    new Select<String>().items(diagram.splitterAggregations).value(diagram.splitterAggregation),
                    new Text("list: lista · concat: junta textos"),
                    new Text("flatten: reúne itens de listas retornadas"),
                    new Text("Na primeira action do subfluxo, input é o item atual."),
                    new Text("index indica a posição do item, a partir de 0."),
                    new Button("Criar ou abrir subfluxo").tooltip("Abre o fluxo de cada item: use input e index na action Java.")
                            .onClick(diagram::openSubflow));
            case "ITEM" -> new Container(new ContainerProps().spacingOf(8)).children(
                    new Text("A primeira action Java recebe o item em input."),
                    new Text("index indica a posição do item, a partir de 0."));
            default -> new Container(new ContainerProps());
        };
    }

    private Component compositionItem(DiagramViewModel diagram, DiagramViewModel.CompositionItem item) {
        return new Container(new ContainerProps().spacingOf(5)).children(
                new Text("Tipo: TEXT, IMAGE, LINK ou BUTTON"),
                new Select<String>().items(diagram.compositionTypes).value(item.type),
                new Text("Valor ou texto"), Components.input(item.value, "Texto ou {{input.campo}}"),
                new StateList<>(item.buttonFields, ignored -> new Container(new ContainerProps().spacingOf(5)).children(
                        new Text("Botão: tipo e destino"),
                        new Select<String>().items(diagram.compositionButtonKinds).value(item.buttonKind),
                        Components.input(item.target, "URL ou callback")), 0),
                new Button("− Remover elemento").tooltip("Remove este elemento da composição.").onClick(() -> diagram.removeCompositionItem(item)));
    }

    private Component expandedActionDialog(DiagramViewModel diagram) {
        return new Container(new ContainerProps().paddingAll(20).spacingOf(12).width(1000).height(570).themeSurface()).children(
                new Text("Action", new TextProps().fontSize(21)),
                new StateList<>(diagram.actionHints, hint -> new Text(hint), 2),
                new JavaCodeEditor(diagram.javaScript, 390,
                        model.javaKeywords, List.of("context")),
                new Button("Voltar ao diagrama").onClick(() -> diagram.expandedActionOpen.set(false)));
    }
}
