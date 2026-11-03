# Contexto e andamento

O TG-bot Editor cria e executa projetos de bots Telegram. O editor fornece mecanismos genéricos; comandos, respostas e regras de negócio pertencem a cada projeto. O bot `meu-primeiro-projeto` é um repositório separado: alterações no editor não atualizam automaticamente os seus dados ou runtime.

A composição aceita HTML e listas dinâmicas `BUTTONS`. Em actions de subfluxo, `originalSplitterItem` mantém o item original quando `input` já contém o resultado de outra etapa.

## Estado atual

- A tela inicial lista os diagramas de `diagrams.json` em cards. Cada card abre seu diagrama. Os comandos ainda não migrados permanecem em `bot.json` e o runner mantém a execução legada; a interface inicial não oferece mais os botões de comando antigo.
- A tela inicial permite criar diagramas como rascunhos. Após configurar e conectar um início, é possível marcá-los como ativos e salvá-los; reiniciar o bot carrega a alteração.
- O diagrama `start` contém um início `CALLBACK` configurado como comando `/start` e uma `RESPONSE` com texto editável. Selecionar um nó mostra seu nome e apenas suas propriedades: comando no início e texto na resposta.
- A paleta à esquerda permite arrastar Callback, Timer, Rest HTTP, Composição, Converter, Java, Generator, Resposta síncrona, JDBC, JsonDB e Splitter para o canvas. O diagrama admite um único início. Os novos nós são salvos sem conexão e não alteram o percurso executado; arrastar a alça direita de saída até a alça esquerda de entrada cria uma conexão. Inícios não têm entrada e respostas não têm saída; as setas permitem configurar condições e excluir conexões. A maioria dos demais tipos continua pendente.
- O nó `JAVA` tem um campo editável **Action**, guardado em `diagrams.json`. O botão de expansão abre o mesmo conteúdo em um editor maior; as edições permanecem sincronizadas. Seu retorno pode ser encaminhado ao nó de resposta com `{{output}}`; respostas e listas mantêm seus tipos quando a marca ocupa todo o campo.
- Os editores Java destacam tipos comuns, a API do bot, `context` e membros acessados diretamente por `context`. Os nomes de arquivos Java em `scripts/` são identificados no projeto aberto; a lista é atualizada ao trocar de projeto, abrir um diagrama ou criar um script pelo editor.
- No projeto do bot mencionado acima existem nove diagramas habilitados: `/start`, `/testeg1`, `/topic`, `/destinos`, `/whoami`, `/actions`, `/bindings`, `/youtube_list` e `/youtube_check`. O fluxo `/youtube_list` usa Callback → Java → Splitter e um subfluxo Item → Java → Resposta síncrona. Algumas actions ainda chamam classes legadas de `scripts/`; a lógica dessas classes ainda não está representada visualmente.
- As actions de `/topic`, `/destinos`, `/actions`, `/bindings`, `/youtube_list` e `/testeg1` agora guardam no diagrama a lógica específica do comando. As classes retêm operações compartilhadas, como leitura de dados e feed. `/youtube_check` ainda usa `YouTubeAction.check`, também usada pelo fluxo periódico; sua orquestração visual segue pendente.
- O runner prioriza um diagrama habilitado compatível com o evento; os demais fluxos continuam em `bot.json`. O Splitter recebe `{{output}}` ou referências como `{{output.result.items}}`, executa o subfluxo para cada item, disponível como `input` na primeira action Java; nas seguintes, `input` contém o resultado anterior. `index` indica a posição iniciada em zero. As propriedades e o editor expandido exibem essa orientação, e o Splitter retorna as respostas em ordem. A agregação padrão é uma lista; `concat` junta respostas textuais. A agregação é configurável visualmente: `list`, `concat` ou `flatten`; a seleção dos caminhos ainda é textual. Diagramas antigos ainda podem usar a action legada por `flowId`.
- O subfluxo abre em um popup sobre o diagrama que contém o Splitter. Cada popup mantém sua seleção e seu canvas; fechar preserva as edições em memória, e **Salvar diagrama** grava o projeto. Subfluxos aninhados abrem novos popups.
- A paleta do diagrama se ajusta à altura disponível, permitindo que as listas de componentes e as propriedades da condição rolem dentro do popup, inclusive no subfluxo.
- O JsonDB executa LIST, INSERT, UPSERT e DELETE em seções de listas simples ou coleções agrupadas, com importação opcional de JSON legado e identificação configurável. INSERT retorna `inserted` e `value` e não altera registros existentes; consultas com filtros ainda não estão disponíveis.
- O caminho do arquivo JSON pode ser definido no componente por caminho direto ou variável de ambiente; `${TG_BOT_DATABASE_FILE}` mantém o banco padrão do projeto quando a variável não está definida.
- `diagrams.json` armazena componentes, posições e conexões, é validado na leitura e na gravação e salvo de forma atômica. O executor executa IF/ELSE, grupos de comparações e subfluxos; a interface edita uma comparação simples por seta e preserva grupos avançados ainda não editáveis.

Os detalhes e referências visuais estão em [proposta do editor de diagramas](proposta-editor-diagramas.md). Registre mudanças de direção em [DECISIONS.md](DECISIONS.md) e mantenha as próximas entregas em [TODO.md](TODO.md).

- Timers visuais usam `intervalMinutes`, iniciam com o bot e substituem agendamentos legados de mesmo ID, sem duplicação. Os diagramas `g1-news`, `ads-tick` e `youtube-new-videos` do bot canônico usam Timer → Java → Splitter. O arquivo `bot.json` permanece como fallback para outros fluxos.

- Gatilhos visuais aceitam `COMMAND`, `CALLBACK`, `TEXT`, `PHOTO` e `DOCUMENT`, com padrão configurável. A disputa com fluxos legados preserva a prioridade de padrão exato, prefixo mais longo e curinga.

- A action “Buscar notícias do G1” retorna objetos sem criar `BotReply`; o subfluxo usa IF/ELSE e composições com ou sem imagem, e uma action encaminha o resultado aos destinos. O editor permite adicionar ou remover elementos TEXT, IMAGE, LINK e BUTTON, agrupar botões por linha e exibir uma prévia ilustrativa com dados fixos. A edição de grupos avançados de condições continua pendente.

- O componente reutilizável **Rest HTTP** permite configurar URL, método e corpo, e retorna status, body e headers para a action Java imediatamente seguinte. Os fluxos G1 de teste e agendado usam Rest HTTP (GET), precedido por uma verificação temporária e seguido por Java e Splitter.

- O Rest HTTP permite informar uma variável de ambiente opcional para a URL; seu valor preenchido substitui a URL configurada. `FlowStop` encerra antecipadamente um diagrama com o valor informado, permitindo preservar validações antes de chamadas HTTP enquanto as condições não acessarem todos os dados e verificações necessários.
# JsonDB com lista em objeto

O formato `OBJECT_ARRAY` permite operar uma lista identificada por nome dentro de uma seção que também contém outras propriedades. Em `LIST`, a opção de preservar entrada retorna `input` e `data` para as próximas etapas.
