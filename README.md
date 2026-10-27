# TG-bot Editor

A native Megalodonte desktop application for visually defining Telegram bot routes. One runner and one Telegram identity can execute command, text, photo, callback and scheduled flows. The canvas, project controls, Java code editor and inputs are Megalodonte components; no WebView is used.

## Build and launch

The desktop editor requires a JDK 25 installation with `javac`. Its Gradle build resolves `megalodonte-components`, `megalodonte-base` and `megalodonte-theme` version `1.0.0-beta` from Maven Local or Maven Central. These artifacts are not included in this repository. By default, `settings.gradle.kts` includes sibling source checkouts at `../../framework/libraries/megalodonte-components` and `../../framework/libraries/megalodonte-theme`. Outside that workspace, disable those included builds with `-PuseLocalSources=false`; the three artifacts must then be available in one of the configured Maven repositories. From the repository root:

```bash
./gradlew -PuseLocalSources=false test
./gradlew -PuseLocalSources=false run
```

The generated bot projects have a separate Gradle Wrapper build that compiles for Java 21 using the JDK running Gradle (version 21 or newer); see below. The editor has a graphical interface, so `run` requires a desktop environment.

## Projects and scripts

The editor and bot projects are separate. **Novo projeto** creates a portable,
independently buildable folder (by default under `~/Documents/TG-bot Editor/projects/`):

On first launch, the editor installs and opens an editable `meu-primeiro-projeto` starter at `~/Documents/TG-bot Editor/projects/meu-primeiro-projeto/`. Its 15 G1 and Genshin Ads flows and two Java script files are copied from the package. An earlier starter stored as `projects/arsenal-soup/` is renamed in place so its scripts and local data remain intact. Later launches reopen the most recent project; existing project files are not overwritten. When an older project is opened, the editor adds missing Gradle Wrapper files without overwriting its existing build or scripts.

```text
Meu bot/
├── bot.json          # visual flows and Java action bodies
├── scripts/          # editable Java classes such as MeuNovoScript.java
├── runtime/          # standalone headless runner and bot API source
├── gradlew, build.gradle.kts  # Gradle Wrapper; produces build/libs/<project>.jar
├── README.md         # build and Termux instructions
├── .env.example      # local configuration template without secrets
├── .env              # local variable values; never commit
├── .gitignore        # excludes .env, data/ and build/
└── data/             # generated runtime state; never commit
```

Every new project includes the runner sources and Gradle Wrapper. In that project's
directory, run `./gradlew clean build` followed by
`java -jar build/libs/<project>.jar --check bot.json` to validate it without a
Telegram token. Fill an ignored `.env` and omit `--check` to start the bot.
The editor packages these runner sources from its own code so generated projects
do not depend on the editor installation. The included project README documents
cloning and running with OpenJDK 21 or newer on Termux.

The flow body is a short Java expression or block, such as `return MeuNovoScript.run();`. **+ Script** asks for a Java class name and creates a compilable `scripts/MeuNovoScript.java`. **Abrir pasta do projeto** opens this folder for IntelliJ, VS Code or a file manager. Edit these classes externally, then use **Salvar projeto** to compile every script and action together. Flow bodies and project Java files are loaded by a project class loader; the example-specific `G1NewsAction` and `GenshinAdsAction` are not built into the editor application.

**Abrir projeto** selects a `bot.json`; **Fechar projeto** preserves the current draft and clears the canvas. The editor reopens the most recent project on startup; its path list is stored separately at `~/.config/tg-bot-editor/recent-projects.properties` (or under `XDG_CONFIG_HOME`). Existing `~/Documents/TG-bot Editor/bot.json` projects are recognized and receive missing external scripts. When an older bundled G1 starter is opened, the missing ads routes are merged without replacing existing flows.

Click a card to edit its trigger and Java body. **Excluir** in Properties removes the selected command; **Salvar projeto** persists the change. Switching cards retains edits in memory. The Java editor provides syntax colors, line numbers and indentation. **Executar teste** runs the selected action locally; use it only for project code you trust, since project scripts run with your user permissions.
**Executar** saves and validates the open project, then starts its Telegram runner in a separate Java process. Set `TG_BOT_TOKEN` in the project's ignored `.env` first. **Parar** stops that process; closing or switching projects also stops it. The editor shows the runner's state and appends its output to `data/bot-runner.log` in the project folder. Restart the bot after changing a script or flow. Do not start the same project from a terminal at the same time.

## Actions em grupos e tópicos

No chat com o bot, use `/whoami` para descobrir seu ID numérico e configure `TG_BOT_ADMIN_ID` no `.env` do projeto. Reinicie o runner. Apenas esse usuário pode listar, vincular e executar actions. Dentro de um grupo ou tópico, use:

| Comando | Resultado |
| --- | --- |
| `/actions` | Lista IDs, nomes e gatilhos das actions do projeto. |
| `/bindaction <id>` | Vincula a action ao grupo ou tópico atual. Repita para vincular várias actions ou use o comando em outro destino. |
| `/bindings` | Mostra as actions vinculadas ao destino atual. |
| `/unbindaction <id>` | Remove o vínculo apenas do destino atual. |
| `/runaction <id>` | Executa a action imediatamente no grupo ou tópico atual. |

Os vínculos ficam em `data/action-bindings.json`, fora do Git, e passam a valer sem reiniciar o bot. Para gatilhos `INTERVAL` e `DAILY`, cada execução usa todos os destinos vinculados, incluindo o `message_thread_id` do tópico. Se não houver vínculos, continua valendo o destino configurado em `targetChatEnv`. Uma action agendada sem destino apenas aguarda um vínculo. Comandos normais do projeto continuam seguindo seus gatilhos existentes; o vínculo configura os destinos de ações agendadas e o `/runaction` permite testar qualquer action sob demanda. O bot precisa estar no grupo e ter permissão para publicar ali.

### Vídeos novos do YouTube

O runner inclui a action `youtube-new-videos`. Use `/youtube_add <@handle, username ou URL do canal>` para inscrever um canal e `/youtube_list` para revisar as inscrições. `/youtube_remove <canal>` remove a inscrição; `/youtube_check` consulta os feeds imediatamente. Dentro do tópico que deve receber os avisos, envie `/bindaction youtube-new-videos`. É possível vincular essa action a vários tópicos ou grupos, sem criar um script Java por destino. O bot verifica os feeds a cada dez minutos; `/runaction youtube-new-videos` também faz uma consulta apenas para o chat atual. Esses comandos exigem `TG_BOT_ADMIN_ID`.

As inscrições e o último vídeo entregue por destino ficam em `data/youtube-subscriptions.json`. Na primeira consulta, os vídeos já presentes no feed estabelecem o ponto inicial; só vídeos posteriores geram avisos. Uma entrega com falha é tentada novamente, sem marcar o vídeo como enviado. Para `@handle` ou URL `/user/`, o bot tenta identificar o canal na página pública do YouTube. Opcionalmente, configure `YOUTUBE_API_KEY` no `.env` para usar a [API oficial de canais](https://developers.google.com/youtube/v3/docs/channels/list) nessa resolução. URLs `/channel/UC...` dispensam resolução. A página pública pode mudar; se a resolução falhar, use a URL com o ID do canal. O feed público determina quais vídeos estão disponíveis para notificação.
**Exportar comandos** grava `comandos.txt` ao lado de `bot.json` com os comandos
dos fluxos `COMMAND` e os comandos internos de actions e YouTube. Ao iniciar,
o runner atualiza o menu `/` do Telegram com essa mesma lista. Reinicie o bot
depois de editar os comandos. Fluxos agendados, de texto, foto e callback não
aparecem no menu; o Telegram aceita até 100 comandos, com nomes em letras
minúsculas, números e sublinhados.
Drag empty canvas space with the primary mouse button to pan the graph without bounds or scroll bars. Drag a card to change its position; pan position never changes saved card coordinates.

## Project variables and runner

The **Variáveis de ambiente do projeto** multiline input edits the project's `.env`, one `NAME=value` per line. For example:

```dotenv
TG_BOT_TOKEN=your-bot-token
TG_BOT_G1_CHAT=-1000000000000
TG_BOT_ADS_DATA_DIR=/absolute/path/to/ads_data
```

Process environment variables override `.env` values. Set the scheduled destinations directly in the project variables: the starter's G1 route uses `TG_BOT_G1_CHAT`, while new scheduled routes use `TG_BOT_TARGET_CHAT` by default. Existing routes retain their saved variable names. Tokens and chat IDs do not go into `bot.json`; the generated `.gitignore` excludes `.env`. The runner uses the bot identity supplied through `TG_BOT_TOKEN`. Do not run it alongside another publisher using the same token, because long polling would conflict and scheduled posts could be duplicated.

```bash
./gradlew -PuseLocalSources=false runBot -PbotProject=examples/meu-primeiro-projeto/bot.json
```

The example project is [examples/meu-primeiro-projeto](examples/meu-primeiro-projeto). Copy `.env.example` to `.env` and set local values before starting its runner. `runBot` uses the editor's Gradle classpath; it does not create a standalone project JAR. The editor's **Executar** button starts the saved project directly.
The runner schedules daily flows at their exact time; subsecond rounding cannot
trigger repeated posts immediately before a scheduled hour. Permanent
Telegram HTTP 4xx polling errors include the API description and stop the runner
instead of retrying every few seconds. An HTTP error while delivering a reply
is logged with its destination and the failed update is skipped; it does not
stop long polling. Scheduled channels must grant the bot
permission to publish messages.

## Migrated example flows

| Source bot | Project flows |
| --- | --- |
| `arsenal-soup` | G1 feed every three hours, with per-article acknowledgement after Telegram confirms delivery. |
| `genshin-ads` | `/ads`, `/novo`, `/save`, `/listar`, `/publicar`, `/config`, `/addchat`, `/removechat`, photo/text wizard, edit/delete/publish callbacks, and a one-minute scheduler for saved ad intervals. Set `TG_BOT_ADS_DATA_DIR` to an absolute folder such as the existing `genshin-ads/ads_data`; otherwise state stays in the ignored project `data/`. |
| `promove-grupo-bot-ads` | Not yet migrated; its payment and admin features still need a separate project script/flow port. |

The new ads flow accepts Telegram photo file IDs for the unified bot identity.
Old photo IDs from another bot identity cannot be reused, but imported ads use
their local image files instead. The example's `GenshinAdsAction` is project
source, so you can change it without rebuilding the editor.
When `TG_BOT_ADS_DATA_DIR` points to a folder with legacy `ads.json`,
`configs.json` and `images/`, the first read imports the ads and channel IDs
into `genshin-ads.json` in that folder. The original files are preserved; local
images are referenced by absolute path. Imported ads start a fresh publishing
interval, avoiding an immediate burst of scheduled posts. Stop the old publisher
before using the new runner against the same channels.

Ad destinations may be a whole group/channel or a specific forum topic. Send
`/topic` inside a topic to obtain its `chat_id` and `message_thread_id`, then
register it with `/addchat <group-id> <topic-id> [name]`, for example
`/addchat -1001234567890 37 Atualizações`. In `/listar`, choose a saved ad and
press **Publicar em tópico** to see the registered topics as buttons; selecting
one publishes only that ad to that topic. **Publicar agora** still sends it to
all registered destinations. The Bot API cannot list every existing topic in
a group, so the picker shows only topics registered for that user. Existing
channel IDs keep working. The standalone runner sends `message_thread_id` for
topic deliveries.

## Packaging

The command below requires Python 3 and the separate `megalodonte-packaging` Python library. Install that library in your Python environment or point `MEGALODONTE_PACKAGING_HOME` to its source checkout; the library is not included here.

```bash
python3 scripts/package.py build appimage
```

The shared `megalodonte-packaging` workflow builds AppImage, DEB and MSI when manually dispatched or when a `v*` tag is pushed. The packaged runtime includes Java compiler modules so project scripts can compile at run time.

## License

No license file is present in this repository. The licensing terms need confirmation from the project owner.
