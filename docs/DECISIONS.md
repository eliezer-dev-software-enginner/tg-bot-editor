# Decisões de desenvolvimento

A prévia de composição usa uma imagem incluída no aplicativo e texto de exemplo para referências dinâmicas, sem executar o fluxo ou acessar imagens externas. Botões com o mesmo número de linha compartilham uma linha do teclado do Telegram; botões sem linha mantêm o comportamento anterior de uma linha por botão.

O JsonDB opera sobre dados configurados no diagrama, sem regras de comandos do bot no editor. Os formatos ARRAY e GROUPED permitem listas simples ou agrupadas; LIST lê uma seção, INSERT inclui somente se a identidade não existir e retorna `inserted` e `value`, UPSERT substitui ou acrescenta e DELETE remove um registro, preservando os demais registros e usando a gravação atômica do banco. A importação de arquivo legado é opcional e relativa ao projeto. Consultas mais avançadas permanecem pendentes.

O caminho configurado no JsonDB determina o arquivo realmente lido e gravado. A referência `${TG_BOT_DATABASE_FILE}` usa a configuração do ambiente ou o banco padrão, mantendo compatibilidade com os diagramas que não informam caminho.

## Propriedade dos fluxos

O editor e o runtime oferecem componentes e mecanismos reutilizáveis. Comandos, respostas, permissões e regras específicas do bot ficam no projeto do bot, em seus diagramas, `bot.json` e scripts. Quando o runtime muda, suas fontes distribuídas aos projetos gerados precisam acompanhar a alteração.

## Um projeto, vários diagramas

Cada diagrama representa um fluxo de entrada independente. Os diagramas ficam em `diagrams.json`, separado de `bot.json`, para permitir a migração gradual. Quando um diagrama habilitado reconhece o evento, ele tem prioridade sobre o fluxo legado correspondente; a lógica antiga permanece como compatibilidade até ser migrada e validada.

Novos diagramas recebem identificador único e começam desativados. A primeira ativação exige um componente de início conectado; diagramas existentes mantêm sua compatibilidade. A alteração é gravada ao salvar e passa a valer na próxima inicialização do bot.

## Primeiro fluxo visual

O `/start` é o primeiro percurso simples: **Callback de comando → Resposta síncrona**. O nome é comum a todo componente; o formulário de cada nó mostra somente as propriedades pertinentes ao seu tipo. A visualização inicial do projeto apresenta cards dos diagramas e mantém os comandos legados em um editor separado.

## Actions Java durante a transição

A action editável de um nó Java pertence ao próprio diagrama. Seu retorno chega à resposta síncrona por `{{output}}`, que isolado preserva também mensagens e listas. Projetos antigos com `flowId` continuam funcionando; ao salvar o nó no editor, o corpo legado é transferido para o diagrama. A compilação usa as classes de `scripts/` do projeto, que ainda podem conter lógica a migrar visualmente.

Na migração de comandos existentes, mova a lógica específica de cada comando para sua action no diagrama e mantenha nas classes apenas operações ainda compartilhadas. Enquanto `bot.json` servir de alternativa para execução legada e chamadas por action, mantenha seu corpo equivalente ao do diagrama e valide os dois percursos.

## Paleta e inclusão de componentes

A paleta lista os componentes planejados e permite arrastá-los ao canvas. Os nós adicionados ficam inicialmente sem conexão: sua presença não altera o percurso já executado. Conexões são criadas arrastando da alça direita de saída para a esquerda de entrada, com validação de ciclos e duplicidade; inícios só têm saída e respostas só têm entrada. A seleção das setas permite excluir a conexão ou editar uma comparação IF ou um ramo ELSE. O Rest HTTP já é configurável e executável; outros tipos ainda aguardam implementação. Um diagrama admite somente um componente de início.

## Splitter e subfluxos

O editor abre cada subfluxo em um popup sobre seu diagrama de origem, com estado e canvas próprios; os diagramas compartilham o projeto em memória e o salvamento grava o conjunto. O Splitter recebe a lista retornada pela etapa anterior ou uma lista em caminho como `{{output.result.items}}` e executa o subfluxo uma vez por item, na ordem original. O início do subfluxo é do tipo `ITEM`; a primeira action Java recebe esse item em `input` e sua posição, começando em zero, em `index`. A configuração `concat` une respostas de texto, `list` mantém cada resultado e `flatten` reúne os itens de listas devolvidas pelo subfluxo. As opções são editáveis nas propriedades do Splitter. Uma referência cíclica de subfluxos é rejeitada em execução. A configuração de caminhos é textual e aceita mapas aninhados.

## Direção da arquitetura

A meta é configurar navegação, condições, mensagens, imagens e botões visualmente. Scripts Java devem realizar operações pontuais sobre dados e retornar resultados; não devem concentrar roteamento de comandos, navegação ou construção da interface do bot. A proposta de componentes, incluindo JsonDB, JDBC e Splitter, está em [proposta do editor de diagramas](proposta-editor-diagramas.md).

## Timers na transição

O Timer de intervalo configurado no diagrama tem prioridade sobre o agendamento legado de mesmo ID. Diagramas desabilitados suspendem o agendamento correspondente; intervalos devem ser inteiros positivos. A execução, inclusive por `/runaction`, usa o diagrama habilitado. O runner conserva agendamentos legados sem diagrama.

## Gatilhos de eventos

O componente de início permite selecionar o tipo e o padrão do evento. O runner compara especificidade entre diagramas e fluxos legados, com prioridade do diagrama em empate, para que um curinga visual não oculte um padrão legado mais específico.

## Composição de mensagens

Mensagens podem ser montadas no componente de composição com elementos TEXT, IMAGE, LINK e BUTTON vinculados aos dados anteriores. A meta é eliminar chamadas manuais a `BotReply` dos fluxos. No G1 agendado, IF/ELSE escolhe a composição com ou sem imagem; a prévia e a migração dos demais fluxos continuam pendentes. O executor processa grupos ALL/ANY e comparações EQ/NE/LT/GT/LE/GE, enquanto a interface edita apenas uma comparação simples por seta e não sobrescreve grupos avançados.

## Requisições HTTP visuais

A direção é oferecer um componente Rest HTTP reutilizável por todos os fluxos que precisarem de requisições, com URL e método configuráveis e resposta disponível ao próximo componente. O G1 é apenas um exemplo inicial. Em **G1: testar envio** e no G1 agendado, o GET antecede o Java de processamento e o Splitter. A implementação retorna um mapa com status, body e headers, inclusive em respostas HTTP de erro; falhas de rede interrompem o fluxo. Cabeçalhos personalizados e parâmetros dinâmicos permanecem pendentes.

O componente de composição visual também será usado nos anúncios. Imagens, textos, links e botões, inclusive os que acionam callbacks, deverão ser configurados no diagrama, substituindo progressivamente sua construção manual em Java.

Como solução transitória até que os ramos condicionais visuais alcancem todas as verificações necessárias, uma action pode retornar `new FlowStop(valor)` para encerrar o diagrama antes da próxima etapa. O Rest HTTP pode usar uma variável de ambiente opcional como URL, com a URL configurada como padrão quando a variável estiver vazia ou ausente.
