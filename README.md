# TG-bot Editor

A native Megalodonte desktop editor for one Telegram bot with multiple visual flows. Each flow connects a trigger to a Java action. The action **returns** a text response, a `BotReply` (photo and optional inline buttons), or a list of replies. The editor uses the reusable Megalodonte `FlowCanvas` component; it has no WebView.

The project was generated with `megalodonte-cli --mode native`. Its visual graph is saved as JSON in `~/Documents/TG-bot Editor/bot.json`; tokens and chat IDs are never saved in the project. The editor initially loads the bundled [migration starter](examples/arsenal-soup.json) until you save your own project.

## Run and test

```bash
./gradlew test
./gradlew run
```

In the editor, select a graph node, edit its trigger and Java body, click **Aplicar no canvas**, then **Salvar projeto**. **Executar teste** runs a selected interval/daily action locally or routes the sample text to a command, text, or callback flow. Scripts are trusted local Java code and execute with the current user's permissions; open only projects you trust.

The standalone runner loads the same data file and uses **one** bot token. Keep credentials outside Git:

```bash
export TG_BOT_TOKEN='set-in-your-shell-or-secret-manager'
export TG_BOT_G1_CHAT='your-channel-id'
export TG_BOT_ROMS_CHAT='your-other-channel-id'
export TG_BOT_ROMS_DATA_DIR='/absolute/path/to/post-roms-bot/jsons'
./gradlew runBot -PbotProject=examples/arsenal-soup.json
```

`TG_BOT_TOKEN` must be the token of the existing `arsenal-soup` bot chosen as the unified identity. The runner receives commands/callbacks through long polling; only one process should poll that token. Scheduled flows use the environment-variable name saved in each flow's `targetChatEnv`. `INTERVAL` accepts minutes; `DAILY` accepts comma-separated local times such as `09:00,15:00,21:00`. The runner does **not** start when the editor opens.

Do not start the bundled example alongside the old scheduled publishers: that would send duplicate posts. Stop their schedulers after validating the new output and before cutover. The G1 flow seeds its delivered-news history from `~/arsenal-soup/noticias.json` if present and records each article only after Telegram confirms delivery.

Build a Linux AppImage with the shared Megalodonte packaging tool:

```bash
python3 scripts/package.py build appimage
```

A `v*` tag or manual GitHub Actions dispatch uses `megalodonte-packaging` for AppImage, DEB, and MSI. The packaged runtime includes the JDK compiler modules required by Java actions.

## Existing-bot migration

| Existing project | Current migration |
| --- | --- |
| `arsenal-soup` | G1 feed, three-hour interval, photo responses, and delivered-news history are represented in the starter graph. |
| `post-roms-bot` | Daily 09:00/15:00/21:00 catalog publication, local cover photo, caption, and download/emulator buttons are represented in the starter graph. Its catalog remains external through `TG_BOT_ROMS_DATA_DIR`. |
| `genshin-ads` | Still runs separately. Its ad wizard, storage, permissions, callbacks, and scheduler need a stateful-flow migration before disabling it. |
| `promove-grupo-bot-ads` | Still runs separately. Its admin panel, listings, broadcasts, payment integration, and callbacks need a stateful-flow migration before disabling it. |

This starter is a safe consolidation path, not a claim that the two large ad bots have already been replaced. No old bot is stopped or deleted by this project.

Telegram file IDs from the old ROM bot cannot be reused by the arsenal-soup identity. The runner selects only entries whose local cover exists. The current catalog has 31 such entries and one additional entry with only an old-bot file ID; restore that cover locally before retiring the old publisher.

## Action examples

```java
return "Olá, " + context.username() + "!";
```

```java
return BotReply.photo("https://example.com/image.jpg", "Nova imagem")
        .withButtons(java.util.List.of(BotButton.link("Abrir", "https://example.com")));
```

Scripts can call application services such as `G1NewsAction.run()` and `RomPostAction.run()`. A batch may return an empty list when there is nothing new. The editor validates each Java body before saving and rejects duplicate triggers.
