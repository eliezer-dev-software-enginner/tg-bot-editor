# Projeto do TG-bot Editor

Este projeto foi criado pelo TG-bot Editor e pode ser compilado e executado sem
o aplicativo desktop. `bot.json` contém os fluxos, `scripts/` contém as classes
Java editáveis, e `runtime/` contém o runner de terminal. Nenhum WebView ou
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
`COMMAND` e os comandos internos de actions e YouTube. Reinicie o bot após
alterar os comandos para atualizar o menu. Falhas nesse registro aparecem no
terminal; o runner continua atendendo mensagens.

O arquivo `.env`, a pasta `data/` e o build `build/` são locais e ignorados
pelo Git. Variáveis do processo têm prioridade sobre as de `.env`. Use apenas
uma instância por token para evitar disputas no long polling.
O bot precisa ter permissão de publicar nos canais configurados. O runner
mostra a descrição de erros da API do Telegram e encerra o polling quando
recebe um HTTP 4xx permanente, evitando tentativas repetidas sem fim.

## Actions em grupos e tópicos

Envie `/whoami` ao bot, copie seu ID para `TG_BOT_ADMIN_ID` no `.env` e reinicie
o runner. No grupo ou tópico desejado, `/actions` lista as actions do projeto,
`/bindaction <id>` vincula uma delas, `/bindings` mostra os vínculos atuais,
`/unbindaction <id>` remove um vínculo e `/runaction <id>` executa a action ali
imediatamente. É possível vincular várias actions ao mesmo destino e uma action
a vários destinos. Os vínculos são salvos em `data/action-bindings.json` e
passam a valer sem reiniciar. Actions agendadas publicam em todos os destinos
vinculados; sem vínculos, usam o destino antigo de `targetChatEnv`. Só o usuário
configurado como administrador pode usar esses comandos, exceto `/whoami`.

## Novos vídeos do YouTube

Use `/youtube_add <@handle, username ou URL de canal>` para inscrever um canal,
`/youtube_list` para listar, `/youtube_remove <canal>` para remover e
`/youtube_check` para consultar agora. No tópico de destino, envie
`/bindaction youtube-new-videos`. O runner verifica os canais a cada dez minutos
e envia apenas vídeos publicados após a primeira consulta naquele destino.
As inscrições e confirmações de entrega ficam em `data/youtube-subscriptions.json`.
Uma falha de entrega é tentada novamente. URLs `/channel/UC...` funcionam sem
chave; para resolver `@handle` o bot usa a página pública ou, se houver
`YOUTUBE_API_KEY` no `.env`, a API oficial de canais.

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
