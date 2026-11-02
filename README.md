# TG-bot Editor

![Ícone do TG-bot Editor](src/main/resources/icon.png)

Editor desktop para criar, testar e executar fluxos de bots do Telegram. Cada projeto guarda seus gatilhos em `bot.json` e suas regras Java em `scripts/`. O editor oferece um painel visual; o bot gerado também pode rodar sozinho no Linux ou no Termux, sem JavaFX.

## Começar

O editor requer JDK 25, com `javac`, e um ambiente gráfico. Na raiz deste repositório:

```sh
./gradlew test
./gradlew run
```

Por padrão, o Gradle inclui os checkouts de `megalodonte-components` e `megalodonte-theme` do workspace Megalodonte. Em outra estrutura de pastas, use `-PuseLocalSources=false` se as dependências Megalodonte já estiverem disponíveis no Maven Local ou em um repositório configurado:

```sh
./gradlew -PuseLocalSources=false run
```

## Criar e executar um projeto

1. Clique em **Novo projeto**, informe o nome e a pasta de destino. O editor cria `bot.json`, `diagrams.json`, `scripts/`, o código do runner, o Gradle Wrapper e `.env.example`.
2. Abra um card de diagrama e edite seus componentes em **Propriedades**. Use **+ Script** quando a ação precisar de uma classe própria. A criação visual de novos diagramas ainda está em desenvolvimento.
3. Clique em **Aplicar** para atualizar o fluxo e em **Salvar projeto** para gravá-lo. **Executar teste** roda a ação selecionada localmente com a entrada de exemplo, sem enviar mensagem ao Telegram.
4. Preencha `TG_BOT_TOKEN` em **Variáveis de ambiente do projeto** ou no `.env` do projeto. Clique em **Executar** para iniciar o bot; o mesmo botão passa a **Parar** enquanto ele estiver ativo. A saída fica em `data/bot-runner.log` na pasta do projeto.

**Abrir projeto** seleciona um `bot.json` existente. **Abrir pasta do projeto** mostra os arquivos para edição externa. **Exportar comandos** cria `comandos.txt` a partir dos fluxos `COMMAND`; ao iniciar, o runner também publica esses comandos no menu `/` do Telegram. Reinicie o bot depois de alterar fluxos ou scripts. Use apenas uma instância por token, pois duas instâncias disputam o long polling.

Um projeto criado pelo editor tem esta estrutura:

```text
meu-bot/
├── bot.json                 # gatilhos, padrões e ações
├── diagrams.json            # diagramas visuais; os habilitados têm prioridade sobre bot.json
├── scripts/                 # classes Java do projeto
├── runtime/                 # runner e API genérica do bot
├── gradlew                  # build independente do editor
├── .env.example             # variáveis disponíveis, sem segredos
├── .env                     # configuração local, ignorada pelo Git
└── data/                    # estado e logs locais, ignorados pelo Git
```

A tela inicial mostra os diagramas do projeto em cards. Clique em **Abrir diagrama** para editar o fluxo visual; fluxos ainda não migrados continuam no `bot.json` e são executados pelo runner. Ao selecionar um componente no canvas, o painel mostra seu nome e suas propriedades: **Tipo de evento** e **Padrão** para o início ou **Texto da resposta** para a resposta síncrona. No componente **Timer**, informe o intervalo em minutos; o fluxo inicia com o bot e é repetido após cada intervalo. No diagrama `start`, selecione cada um dos dois componentes para editar esses campos e depois clique em **Salvar diagrama**. Ao selecionar um nó **Java**, edite sua **Action** no próprio diagrama. Para passar seu retorno à resposta síncrona, use `{{output}}` no campo de texto; isolado, ele também encaminha mensagens ou listas sem convertê-las em texto. A action pode chamar classes existentes em `scripts/`. Ao salvar, um diagrama habilitado tem prioridade sobre o fluxo legado do mesmo comando. O formato e as decisões em aberto estão em [proposta do editor de diagramas](docs/proposta-editor-diagramas.md).

O [contexto e andamento](docs/CONTEXT.md), as [decisões](docs/DECISIONS.md) e as [próximas etapas](docs/TODO.md) acompanham a evolução do projeto.

Na tela de um diagrama, arraste um item da paleta **Componentes** para posicioná-lo no canvas e clique em **Salvar diagrama** para guardá-lo. Cada diagrama admite um início. Os novos componentes começam sem conexão: arraste a alça direita da origem até a alça esquerda do destino. Componentes de início possuem somente saída; a resposta síncrona possui somente entrada. Para excluir uma ligação, clique na seta e use **Excluir conexão** nas propriedades. A seta também permite escolher ALWAYS, IF ou ELSE e editar uma comparação simples. Para criar outro ramo, primeiro configure a saída existente como IF e arraste novamente da origem até outro destino; a nova saída será ELSE. Conexões duplicadas ou que criariam ciclos são recusadas.

O **Rest HTTP** permite configurar URL, método (GET, POST, PUT, PATCH, DELETE ou HEAD) e corpo opcional em UTF-8. Opcionalmente, informe uma variável de ambiente que substitui a URL quando estiver preenchida. Conecte-o diretamente a um componente Java: a action recebe `input` como um mapa com `status` (inteiro), `body` (texto) e `headers` (mapa de listas). Exemplo:

```java
var resposta = (java.util.Map<?, ?>) input;
return resposta.get("body");
```

Respostas HTTP de erro também são retornadas com seu status; falhas de conexão e URLs inválidas interrompem o fluxo. A conexão tem limite de 10 segundos e a requisição, 20 segundos. Cabeçalhos personalizados, autenticação configurável, parâmetros dinâmicos e seleção de codificação ainda não estão disponíveis. Projetos antigos precisam atualizar os arquivos do runtime para executar esse componente.

Condições aceitam `EQ`, `NE`, `LT`, `GT`, `LE` e `GE`. Use `{{input.campo}}` para dados recebidos pelo subfluxo e `{{output.campo}}` para o resultado anterior; `''` representa texto vazio. Por exemplo, `{{input.image}} NE ''` segue o ramo IF quando há imagem; caso contrário, segue ELSE. Se mais de um IF corresponder, o primeiro na ordem do diagrama é escolhido. O editor ainda não altera grupos avançados existentes.

O componente **Composição** monta uma mensagem sem código de apresentação. Em suas propriedades, adicione ou remova elementos `TEXT`, `IMAGE`, `LINK` e `BUTTON`; os valores podem conter referências como `{{input.title}}`. Em um botão, escolha `URL` ou `CALLBACK` e informe o destino. Uma composição aceita uma imagem; a legenda longa é reduzida para caber no Telegram, preservando o último link. A prévia visual e a edição de grupos de botões ainda estão pendentes.

O **JsonDB** manipula seções do banco JSON como uma lista de registros (`ARRAY`) ou listas agrupadas por chave (`GROUPED`). Em **Caminho do JSON**, informe um arquivo `.json`, absoluto ou relativo ao projeto, ou uma variável como `${TG_BOT_DATABASE_FILE}`; essa variável pode estar no `.env` e, se ausente, usa `data/bot-database.json`. O arquivo `.env` contém a configuração e não é o banco. `LIST` retorna a seção; `UPSERT` adiciona ou substitui um registro e `DELETE` remove o registro identificado. Por exemplo, para uma action que retorna `{"grupo":"noticias", "registro":{"chatId":-100, "threadId":165}}`, configure o formato `GROUPED`, a chave `{{output.grupo}}`, o registro `{{output.registro}}` e os campos de identificação `chatId,threadId`. Para `ARRAY`, não é necessária uma chave. O campo de retorno pode usar `{{output}}` ou um caminho como `{{output.mensagem}}`. Um arquivo legado opcional é relativo ao projeto e importado quando a seção ainda não existe. Filtros e estruturas além desses formatos ainda não são suportados.

Para inspecionar um Splitter existente, selecione-o e clique em **Criar ou abrir subfluxo**; o subfluxo abre em um popup sobre o diagrama atual. Use **Salvar diagrama** para gravar as alterações e **Fechar** para retornar ao diagrama que o contém. O caminho `{{output}}` percorre a lista devolvida pela etapa anterior; use, por exemplo, `{{output.result.items}}` para uma lista dentro de mapas aninhados. No subfluxo, a primeira action Java recebe cada item em `input` e sua posição (começando em zero) em `index`; nas actions seguintes, `input` recebe o resultado da etapa anterior. O Splitter devolve as respostas em ordem. Nas propriedades, `list` mantém os resultados, `concat` reúne textos e `flatten` reúne os itens das listas retornadas.

Os editores Java destacam os tipos comuns, a API do bot e as classes encontradas pelos nomes dos arquivos `.java` em `scripts/`. Ao criar um script pelo editor ou abrir novamente o diagrama, a lista é atualizada para o projeto atual.

![Diagrama start no editor, com entrada e resposta ligadas](docs/images/diagrama-start-editor.png)

Para validar ou executar o projeto sem abrir o editor, use JDK 21 ou superior dentro da pasta criada:

```sh
./gradlew clean build
java -jar build/libs/*.jar --check bot.json
java -jar build/libs/*.jar bot.json
```

`--check` compila e valida os fluxos sem conectar ao Telegram nem exigir token. Antes da execução real, copie `.env.example` para `.env` e configure o token. O README gerado no projeto explica o uso no Termux.

## Gatilhos e botões

O campo **Padrão** depende do gatilho:

| Gatilho | Exemplo de padrão | Quando executa |
| --- | --- | --- |
| `COMMAND` | `/menu` | Comando recebido, inclusive com argumentos ou `@nome_do_bot`. |
| `TEXT` | `pedido:*` | Texto que começa com `pedido:`. |
| `CALLBACK` | `menu:detalhes` | Clique em botão que envia esse dado. |
| `PHOTO` ou `DOCUMENT` | `*` | Qualquer mensagem do tipo correspondente. |
| `INTERVAL` | `10` | A cada 10 minutos. |
| `DAILY` | `09:00,18:30` | Nos horários indicados. |

Em `TEXT`, `CALLBACK`, `PHOTO` e `DOCUMENT`, `*` aceita qualquer valor e um asterisco final cria um prefixo, como `menu:*`. Um padrão exato tem prioridade sobre prefixos; `*` serve como fallback. `COMMAND` não usa asterisco.

Para montar um menu visual, adicione uma **Composição** com um elemento `TEXT` com o valor `Escolha uma opção:` e dois elementos `BUTTON`:

- Para **Detalhes**, informe o texto `Detalhes`, o tipo `CALLBACK` e o destino `menu:detalhes`.
- Para **Site**, informe o texto `Site`, o tipo `URL` e o destino `https://example.com`.

O clique em **Detalhes** envia `menu:detalhes` ao bot. Crie outro diagrama com início `CALLBACK` e esse padrão e conecte uma resposta com o texto desejado. Use `menu:*` para receber vários callbacks da mesma família; `context.text()` contém o dado recebido. O botão **Site** abre a URL. Cada botão aparece em sua própria linha. Fluxos legados ainda podem retornar `BotReply` enquanto são migrados para a composição.

As respostas, permissões, assinaturas de canais e regras de anúncios pertencem aos scripts do **projeto do bot**. O editor fornece os gatilhos, a persistência e o transporte Telegram; ele não define os comandos particulares de um bot.

## Configuração e dados

O `.env` é local e ignorado pelo Git. Variáveis do processo prevalecem sobre ele. O token do bot é `TG_BOT_TOKEN`; `TG_BOT_ADMIN_ID` e `YOUTUBE_API_KEY` ficam disponíveis para scripts que precisem deles. O banco JSON do runner fica, por padrão, em `data/bot-database.json` dentro do projeto. Defina `TG_BOT_DATABASE_FILE` para usar outro arquivo `.json`; um caminho relativo é resolvido a partir da pasta do projeto.

O editor guarda a lista de projetos recentes na configuração do usuário, fora dos projetos. Ao abrir, restaura o projeto principal configurado, o mais recente ou cria um projeto inicial. Se precisar indicar explicitamente o projeto principal, use a propriedade de sistema `tg.bot.editor.canonicalBot` com o caminho de seu `bot.json`.

## Empacotar

O empacotamento usa Python 3 e a biblioteca separada `megalodonte-packaging`, instalada no ambiente ou indicada por `MEGALODONTE_PACKAGING_HOME`:

```sh
python3 scripts/package.py build appimage
```

O [workflow de empacotamento](.github/workflows/package.yml) produz pacotes para Linux e Windows em execução manual ou tag `v*`.

Exemplo de action Java dentro do subfluxo de um Splitter:

```java
return "Item " + (index + 1) + ": " + input;
```

Uma Resposta síncrona seguinte pode enviar esse resultado com `{{output}}`.
