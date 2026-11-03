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

O formato `OBJECT` lê a seção inteira com `LIST`, útil quando várias propriedades são necessárias ao mesmo fluxo; ele não oferece operações de escrita.

O botão **Construir bot** salva o projeto e o diagrama aberto, exporta os comandos, executa `gradlew build` em segundo plano e verifica o JAR com `--check`. O resultado aparece no estado da interface e os detalhes ficam em `build/tg-bot-editor-build.log` no projeto.

O arquivo de comandos e o menu Telegram reúnem os gatilhos de comando dos diagramas ativos e os fluxos legados, com prioridade para o nome do diagrama quando há duplicidade. O JAR continua dependendo do projeto externo para carregar `bot.json`, `diagrams.json` e `scripts/`.

A construção também produz `build/distributions/bot-portatil.zip`, com JAR, arquivos de fluxo e scripts Java, sem `.env`, dados ou imagens. O editor seleciona `gradlew` no Unix e `gradlew.bat` no Windows; a seleção foi testada automaticamente, mas a execução no Windows ainda precisa de validação naquele sistema.

O painel JsonDB apresenta o arquivo e o caminho dos dados separadamente. Novos componentes aceitam caminhos como `user.name` e `products.fixes_id`; a leitura atravessa listas e reúne os resultados. Diagramas existentes conservam sua configuração anterior ao serem abertos, inclusive as importações já definidas, sem exibir campos legados no painel.

No projeto canônico do bot, a exclusão de anúncios agora usa diagramas para pedir confirmação e para excluir pelo JsonDB; outros callbacks continuam pendentes.

O projeto do bot também usa diagramas para iniciar a edição de anúncios e compor as mensagens do teste G1 e das notificações YouTube; a busca dos vídeos e etapas posteriores da edição ainda são legadas.

O comando `/importarads` usa composição e JsonDB para escolher o modo; os callbacks de escolha também são visuais. O processamento do arquivo continua legado no projeto do bot.

Na operação LIST, o painel mostra apenas arquivo e caminho dos dados. Para inserção, atualização ou exclusão, ele também pede os dados recebidos e os campos que identificam cada registro, com exemplos. O valor encaminhado após alteração mantém a configuração existente e não precisa ser preenchido no painel.

As operações `SET` e `REMOVE` definem e removem um valor por caminho dentro de objetos; apenas `SET` solicita o valor recebido, e nenhuma delas requer campos de identificação.

A composição aceita `BUTTON_LIST` para repetir botões a partir de uma lista, com modelos visuais para texto e destino. Uma conexão `ON_ERROR` de JsonDB permite tratar falhas da operação em outro componente; sem essa conexão o erro é propagado.

O Rest HTTP também aceita cabeçalhos configuráveis, token Bearer obtido de variável do ambiente e referências a input/output na URL, corpo e valores dos cabeçalhos. Valores dinâmicos de URL devem estar codificados previamente.
