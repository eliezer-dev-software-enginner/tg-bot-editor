# Decisões de desenvolvimento

## Propriedade dos fluxos

O editor e o runtime oferecem componentes e mecanismos reutilizáveis. Comandos, respostas, permissões e regras específicas do bot ficam no projeto do bot, em seus diagramas, `bot.json` e scripts. Quando o runtime muda, suas fontes distribuídas aos projetos gerados precisam acompanhar a alteração.

## Um projeto, vários diagramas

Cada diagrama representa um fluxo de entrada independente. Os diagramas ficam em `diagrams.json`, separado de `bot.json`, para permitir a migração gradual. Quando um diagrama habilitado reconhece o evento, ele tem prioridade sobre o fluxo legado correspondente; a lógica antiga permanece como compatibilidade até ser migrada e validada.

## Primeiro fluxo visual

O `/start` é o primeiro percurso simples: **Callback de comando → Resposta síncrona**. O nome é comum a todo componente; o formulário de cada nó mostra somente as propriedades pertinentes ao seu tipo. A visualização inicial do projeto apresenta cards dos diagramas e mantém os comandos legados em um editor separado.

## Actions Java durante a transição

A action editável de um nó Java pertence ao próprio diagrama. Seu retorno chega à resposta síncrona por `{{output}}`, que isolado preserva também mensagens e listas. Projetos antigos com `flowId` continuam funcionando; ao salvar o nó no editor, o corpo legado é transferido para o diagrama. A compilação usa as classes de `scripts/` do projeto, que ainda podem conter lógica a migrar visualmente.

Na migração de comandos existentes, mova a lógica específica de cada comando para sua action no diagrama e mantenha nas classes apenas operações ainda compartilhadas. Enquanto `bot.json` servir de alternativa para execução legada e chamadas por action, mantenha seu corpo equivalente ao do diagrama e valide os dois percursos.

## Paleta e inclusão de componentes

A paleta lista os componentes planejados e permite arrastá-los ao canvas. Os nós adicionados ficam inicialmente sem conexão: sua presença não altera o percurso já executado. O editor ainda precisa oferecer criação de conexões e configuração dos novos tipos antes que esses nós sejam executáveis. Um diagrama admite somente um componente de início.

## Direção da arquitetura

A meta é configurar navegação, condições, mensagens, imagens e botões visualmente. Scripts Java devem realizar operações pontuais sobre dados e retornar resultados; não devem concentrar roteamento de comandos, navegação ou construção da interface do bot. A proposta de componentes, incluindo JsonDB, JDBC e Splitter, está em [proposta do editor de diagramas](proposta-editor-diagramas.md).
