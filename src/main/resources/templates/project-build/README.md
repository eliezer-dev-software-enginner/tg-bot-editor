# Projeto do TG-bot Editor

Este projeto foi criado pelo TG-bot Editor e pode ser compilado e executado sem
o aplicativo desktop. `bot.json` contém os fluxos legados, `diagrams.json` os
diagramas e suas actions Java, `scripts/` as classes Java editáveis e `runtime/`
o runner de terminal. Nenhum WebView ou
JavaFX é necessário para executar o bot.

## Build e execução

Use OpenJDK 21 ou superior com `javac` e o Gradle Wrapper incluído. Na pasta do projeto:

```sh
./gradlew clean build
java -jar build/libs/*.jar --check bot.json
```

`--check` valida os gatilhos e compila todas as ações sem conectar ao Telegram
ou exigir um token. Para executar o bot, copie `.env.example` para `.env`,
preencha `TG_BOT_TOKEN` com o token do seu bot e rode:

```sh
java -jar build/libs/*.jar bot.json
```

Ao iniciar, o runner registra no menu `/` do Telegram os comandos dos fluxos
`COMMAND` definidos pelo projeto. Reinicie o bot após
alterar os comandos para atualizar o menu. Falhas nesse registro aparecem no
terminal; o runner continua atendendo mensagens.

O arquivo `.env`, a pasta `data/` e o build `build/` são locais e ignorados
pelo Git. Variáveis do processo têm prioridade sobre as de `.env`. Use apenas
uma instância por token para evitar disputas no long polling.
O bot precisa ter permissão de publicar nos canais configurados. O runner
mostra a descrição de erros da API do Telegram e encerra o polling quando
recebe um HTTP 4xx permanente, evitando tentativas repetidas sem fim.

## Rest HTTP

O componente Rest HTTP retorna à action Java imediatamente seguinte um mapa em `input` com `status`, `body` e `headers`:

```java
var resposta = (java.util.Map<?, ?>) input;
return resposta.get("body");
```

A URL, o método e o corpo opcional são configurados no diagrama. Erros HTTP são retornados em `status`; falhas de conexão interrompem o fluxo.

## Splitter

No subfluxo de um Splitter, a primeira action Java recebe o item atual em
`input` e sua posição, começando em zero, em `index`. As actions Java seguintes
recebem em `input` o resultado da etapa anterior. Por exemplo:

```java
return "Item " + (index + 1) + ": " + input;
```

Uma Resposta síncrona seguinte pode usar `{{output}}` para enviar o resultado.

## Comandos do projeto

### Padrões e botões de resposta

Em `bot.json`, `COMMAND` usa `/comando`; `INTERVAL` usa minutos (`10`) e
`DAILY` usa horários (`09:00,18:30`). Nos gatilhos `TEXT`, `CALLBACK`, `PHOTO`
e `DOCUMENT`, `*` aceita qualquer valor; `menu:*` aceita valores iniciados por
`menu:`. O asterisco só funciona no final. Um padrão exato tem prioridade sobre
um prefixo, e um prefixo tem prioridade sobre `*`. Comandos não usam `*`.

Um fluxo `/menu` pode montar botões no script Java assim:

```java
return BotReply.text("Escolha:").withButtons(java.util.List.of(
    BotButton.callback("Detalhes", "menu:detalhes"),
    BotButton.link("Site", "https://example.com")
));
```

Cada botão ocupa uma linha. `BotButton.link` abre a URL. Ao clicar em
`BotButton.callback`, o Telegram envia o segundo argumento como dado do
callback: crie outro fluxo com gatilho `CALLBACK`, padrão `menu:detalhes` e
script `return "Você clicou em Detalhes.";`. Nesse fluxo, `context.text()`
contém `menu:detalhes`. Para vários botões, um padrão `menu:*` pode tratar os
cliques pelo valor de `context.text()`; `*` funciona como fallback geral.

O runtime não cria comandos próprios. Defina cada comando em `bot.json` como
fluxo `COMMAND` e coloque suas respostas e regras em `scripts/`. Tarefas
periódicas usam fluxos `INTERVAL` ou `DAILY`. O menu `/` do Telegram inclui
somente os comandos presentes no projeto.

## Termux

Com o Git e o OpenJDK 21 ou superior já configurados, clone o repositório na pasta privada
do Termux (`$HOME`), execute o Gradle Wrapper incluído:

```sh
cd "$HOME"
git clone URL-DO-SEU-REPOSITORIO
cd NOME-DA-PASTA-CLONADA
java -version
javac -version
./gradlew clean build
java -jar build/libs/*.jar --check bot.json
cp .env.example .env
chmod 600 .env
# Edite .env com seu editor de preferência antes de iniciar.
java -jar build/libs/*.jar bot.json
```

O wrapper baixa o Gradle na primeira execução. Use `./gradlew --version` para
verificar o JDK. O alvo do projeto é Java 21; o Gradle usa o JDK instalado no
Termux, sem exigir um toolchain 25. Para um projeto criado antes dessa correção,
remova a linha `java { toolchain { languageVersion.set(JavaLanguageVersion.of(25)) } }`
do `build.gradle.kts` e execute `./gradlew clean build` novamente.

## Anúncios em tópicos

Adicione o bot ao grupo, envie `/topic` dentro do tópico desejado e cadastre
os IDs retornados com `/addchat <grupo> <tópico> [nome]`. Use `/config` para
revisar os destinos. Em `/listar`, o botão **Publicar em tópico** mostra os
tópicos cadastrados e publica somente o anúncio selecionado no tópico escolhido.
O Bot API não lista automaticamente todos os tópicos existentes.
`/addchat <grupo>` continua cadastrando o grupo inteiro.
