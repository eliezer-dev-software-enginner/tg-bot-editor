# Contexto e andamento

O TG-bot Editor cria e executa projetos de bots Telegram. O editor fornece mecanismos genéricos; comandos, respostas e regras de negócio pertencem a cada projeto. O bot `meu-primeiro-projeto` é um repositório separado: alterações no editor não atualizam automaticamente os seus dados ou runtime.

## Estado atual

- A tela inicial lista os diagramas de `diagrams.json` em cards. Cada card abre seu diagrama. Os comandos ainda não migrados permanecem em `bot.json` e o runner mantém a execução legada; a interface inicial não oferece mais os botões de comando antigo.
- O diagrama `start` contém um início `CALLBACK` configurado como comando `/start` e uma `RESPONSE` com texto editável. Selecionar um nó mostra seu nome e apenas suas propriedades: comando no início e texto na resposta.
- A paleta à esquerda permite arrastar Callback, Timer, Converter, Java, Generator, Resposta síncrona, JDBC, JsonDB e Splitter para o canvas. O diagrama admite um único início. Os novos nós são salvos sem conexão e não alteram o percurso executado; criação de conexões e configuração/execução dos novos tipos continuam pendentes.
- O nó `JAVA` tem um campo editável **Action**, guardado em `diagrams.json`. O botão de expansão abre o mesmo conteúdo em um editor maior; as edições permanecem sincronizadas. Seu retorno pode ser encaminhado ao nó de resposta com `{{output}}`; respostas e listas mantêm seus tipos quando a marca ocupa todo o campo.
- Os editores Java destacam tipos comuns, a API do bot, `context` e membros acessados diretamente por `context`. Os nomes de arquivos Java em `scripts/` são identificados no projeto aberto; a lista é atualizada ao trocar de projeto, abrir um diagrama ou criar um script pelo editor.
- No projeto do bot mencionado acima existem nove diagramas habilitados: `/start`, `/testeg1`, `/topic`, `/destinos`, `/whoami`, `/actions`, `/bindings`, `/youtube_list` e `/youtube_check`. O fluxo `/youtube_list` usa Callback → Java → Splitter e um subfluxo Item → Java → Resposta síncrona. Algumas actions ainda chamam classes legadas de `scripts/`; a lógica dessas classes ainda não está representada visualmente.
- As actions de `/topic`, `/destinos`, `/actions`, `/bindings`, `/youtube_list` e `/testeg1` agora guardam no diagrama a lógica específica do comando. As classes retêm operações compartilhadas, como leitura de dados e feed. `/youtube_check` ainda usa `YouTubeAction.check`, também usada pelo fluxo periódico; sua orquestração visual segue pendente.
- O runner prioriza um diagrama habilitado compatível com o evento; os demais fluxos continuam em `bot.json`. O Splitter recebe `{{output}}`, executa o subfluxo para cada item, disponível como `input` na primeira action Java; nas seguintes, `input` contém o resultado anterior. `index` indica a posição iniciada em zero. As propriedades e o editor expandido exibem essa orientação, e o Splitter retorna as respostas em ordem. A agregação padrão é uma lista; `concat` junta respostas textuais. A agregação é configurável visualmente: `list`, `concat` ou `flatten`; ainda não há seleção visual de caminhos estruturados. Diagramas antigos ainda podem usar a action legada por `flowId`.
- O subfluxo abre em um popup sobre o diagrama que contém o Splitter. Cada popup mantém sua seleção e seu canvas; fechar preserva as edições em memória, e **Salvar diagrama** grava o projeto. Subfluxos aninhados abrem novos popups.
- `diagrams.json` armazena componentes, posições e conexões, é validado na leitura e na gravação e salvo de forma atômica. O modelo admite condições e subfluxos, mas isso ainda não significa que o editor ou o runner os executem.

Os detalhes e referências visuais estão em [proposta do editor de diagramas](proposta-editor-diagramas.md). Registre mudanças de direção em [DECISIONS.md](DECISIONS.md) e mantenha as próximas entregas em [TODO.md](TODO.md).

- Timers visuais usam `intervalMinutes`, iniciam com o bot e substituem agendamentos legados de mesmo ID, sem duplicação. Os diagramas `g1-news`, `ads-tick` e `youtube-new-videos` do bot canônico usam Timer → Java → Splitter. O arquivo `bot.json` permanece como fallback para outros fluxos.

- Gatilhos visuais aceitam `COMMAND`, `CALLBACK`, `TEXT`, `PHOTO` e `DOCUMENT`, com padrão configurável. A disputa com fluxos legados preserva a prioridade de padrão exato, prefixo mais longo e curinga.

- A action “Buscar notícias do G1” retorna objetos sem criar `BotReply`; uma action provisória no subfluxo ainda compõe a mensagem. O componente de composição (Image, Text e Button) e a edição/execução de condições por clique nas setas ainda não foram implementados.
