# TG-bot Editor

A native Megalodonte desktop application for visually defining Telegram bot routes. One runner and one Telegram identity can execute command, text, photo, callback and scheduled flows. The canvas, project controls, Java code editor and inputs are Megalodonte components; no WebView is used.

## Projects and scripts

The editor and bot projects are separate. **Novo projeto** creates a portable folder (by default under `~/Documents/TG-bot Editor/projects/`):

On first launch, the editor installs and opens an editable `meu-primeiro-projeto` starter at `~/Documents/TG-bot Editor/projects/meu-primeiro-projeto/`. Its 15 G1, ROM, and Genshin Ads flows and three Java script files are copied from the package. An earlier starter stored as `projects/arsenal-soup/` is renamed in place so its scripts and local data remain intact. Later launches reopen the most recent project; existing project files are not overwritten.

```text
Meu bot/
├── bot.json          # visual flows and Java action bodies
├── scripts/          # editable Java classes such as MeuNovoScript.java
├── .env              # local variable values; never commit
├── .gitignore        # excludes .env and data/
└── data/             # generated runtime state; never commit
```

The flow body is a short Java expression or block, such as `return MeuNovoScript.run();`. **+ Script** asks for a Java class name and creates a compilable `scripts/MeuNovoScript.java`. **Abrir pasta do projeto** opens this folder for IntelliJ, VS Code or a file manager. Edit these classes externally, then use **Salvar projeto** to compile every script and action together. Flow bodies and project Java files are loaded by a project class loader; the example-specific `G1NewsAction`, `RomPostAction` and `GenshinAdsAction` are not built into the editor application.

**Abrir projeto** selects a `bot.json`; **Fechar projeto** preserves the current draft and clears the canvas; **Abrir recente** uses a list of the ten most recent paths. The path list is stored separately at `~/.config/tg-bot-editor/recent-projects.properties` (or under `XDG_CONFIG_HOME`). Existing `~/Documents/TG-bot Editor/bot.json` projects are recognized and receive missing external scripts. When an older bundled G1/ROM starter is opened, the missing ads routes are merged without replacing existing flows.

Click a card to edit its trigger and Java body. **Excluir** in Properties removes the selected command; **Salvar projeto** persists the change. Switching cards retains edits in memory. The Java editor provides syntax colors, line numbers and indentation. **Executar teste** runs the selected action locally; use it only for project code you trust, since project scripts run with your user permissions.
Drag empty canvas space with the primary mouse button to pan the graph without bounds or scroll bars. Drag a card to change its position; pan position never changes saved card coordinates.

## Project variables and runner

The **Variáveis de ambiente do projeto** multiline input edits the project's `.env`, one `NAME=value` per line. For example:

```dotenv
TG_BOT_TOKEN=your-existing-arsenal-soup-bot-token
TG_BOT_G1_CHAT=-1000000000000
TG_BOT_ROMS_CHAT=-1000000000000
TG_BOT_ROMS_DATA_DIR=/absolute/path/to/post-roms-bot/jsons
```

Process environment variables override `.env` values. The **Canal agendado · nome da variável** field on a scheduled G1 or ROM route chooses which variable holds that route's destination chat. Tokens and chat IDs do not go into `bot.json`; the generated `.gitignore` excludes `.env`. The default runner uses the existing `arsenal-soup` bot identity. Do not run it alongside an old publisher using the same token, because long polling would conflict and scheduled posts could be duplicated.

```bash
./gradlew test
./gradlew run
./gradlew runBot -PbotProject=examples/meu-primeiro-projeto/bot.json
```

The example project is [examples/meu-primeiro-projeto](examples/meu-primeiro-projeto). Copy `.env.example` to `.env` and set local values before starting its runner. The editor itself does not start the runner.

## Migrated example flows

| Source bot | Project flows |
| --- | --- |
| `arsenal-soup` | G1 feed every three hours, with per-article acknowledgement after Telegram confirms delivery. |
| `post-roms-bot` | ROM publication at 09:00, 15:00 and 21:00 using its external JSON catalog, local covers and inline links. |
| `genshin-ads` | `/ads`, `/novo`, `/save`, `/listar`, `/publicar`, `/config`, `/addchat`, `/removechat`, photo/text wizard, edit/delete/publish callbacks, and a one-minute scheduler for saved ad intervals. Ads and channel IDs are stored in ignored project `data/`. Existing `genshin-ads` data is not imported automatically. |
| `promove-grupo-bot-ads` | Not yet migrated; its payment and admin features still need a separate project script/flow port. |

The new ads flow accepts Telegram photo file IDs for the unified bot identity. Old photo IDs from a different bot identity cannot be reused. Configure target channels with `/addchat <id>` after cutover. The example's `GenshinAdsAction` is project source, so you can change it without rebuilding the editor.

## Packaging

```bash
python3 scripts/package.py build appimage
```

The shared `megalodonte-packaging` workflow builds AppImage, DEB and MSI when manually dispatched or when a `v*` tag is pushed. The packaged runtime includes Java compiler modules so project scripts can compile at run time.
