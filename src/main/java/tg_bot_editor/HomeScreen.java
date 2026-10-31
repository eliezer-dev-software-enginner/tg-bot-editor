package tg_bot_editor;

import megalodonte.base.components.Component;
import megalodonte.base.components.ScreenComponent;
import megalodonte.components.*;
import megalodonte.components.inputs.*;
import megalodonte.components.layout_components.*;
import megalodonte.props.*;

/** Declarative editor view; state and project operations live in HomeViewModel. */
public final class HomeScreen implements ScreenComponent {
    private final HomeViewModel model = new HomeViewModel();

    @Override public Component render() {
        return new Stack().children(
                new Container(new ContainerProps().fillHeight().themeBackground()).children(
                        heading(),
                        new Row(new RowProps().spacingOf(12).fillHeight()).children(
                                model.canvas.current(),
                                new Scroll(inspector()).fillAvailableHeight())),
                new Modal(model.newProjectOpen, newProjectDialog()),
                new Modal(model.newScriptOpen, newScriptDialog())
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
                        new Select<String>(new SelectProps().minWidth(230).maxWidth(230))
                                .items(model.recentPaths).value(model.recentChoice),
                        new Button("Abrir recente").onClick(model::openRecent),
                        new Button("+ Novo comando").onClick(model::add),
                        new Button("+ Script").onClick(model::openNewScript)));
    }

    private Component inspector() {
        return new Container(new ContainerProps().paddingAll(16).spacingOf(10).width(330).themeSurface()).children(
                new Text("Propriedades", new TextProps().fontSize(20)),
                new Text("Título"), Components.input(model.title, "Nome da ação"),
                new Text("Descrição do comando"), Components.input(model.description, "O que este comando faz"),
                new Text("Script Java · retorne texto ou BotReply"), new JavaCodeEditor(model.script, 230),
                new Row(new RowProps().spacingOf(6)).children(
                        new Button("Aplicar").tooltip("Aplica as alterações da ação selecionada ao projeto. Salve para persistir no disco.").onClick(model::apply),
                        new Button("Excluir").tooltip("Remove a ação selecionada do projeto. Salve para persistir no disco.").onClick(model::remove)),
                new Text("Tipo de gatilho"), new Text(model.trigger),
                new Row(new RowProps().spacingOf(6)).children(
                        new Button("Comando").tooltip("Executa a ação quando receber um comando como /start.").onClick(() -> model.chooseTrigger("COMMAND")),
                        new Button("Texto").tooltip("Executa a ação quando receber uma mensagem de texto correspondente ao padrão.").onClick(() -> model.chooseTrigger("TEXT"))),
                new Row(new RowProps().spacingOf(6)).children(
                        new Button("Callback").tooltip("Executa a ação ao clicar em um botão de resposta do bot.").onClick(() -> model.chooseTrigger("CALLBACK")),
                        new Button("Foto").tooltip("Executa a ação quando receber uma foto.").onClick(() -> model.chooseTrigger("PHOTO"))),
                new Row(new RowProps().spacingOf(6)).children(
                        new Button("Intervalo").tooltip("Executa a ação periodicamente; informe o intervalo em minutos no campo Padrão.").onClick(() -> model.chooseTrigger("INTERVAL")),
                        new Button("Horários diários").tooltip("Executa a ação nos horários HH:mm informados no campo Padrão.").onClick(() -> model.chooseTrigger("DAILY"))),
                new Text("Padrão · comando, texto, minutos ou HH:mm"),
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
}
