# Contexto e andamento

O TG-bot Editor cria e executa projetos de bots Telegram. O editor fornece mecanismos genéricos; comandos, respostas e regras de negócio pertencem a cada projeto. O bot `meu-primeiro-projeto` é um repositório separado: alterações no editor não atualizam automaticamente os seus dados ou runtime.

## Estado atual

- A tela inicial lista os diagramas de `diagrams.json` em cards. Cada card abre seu diagrama; **Editar comandos antigos** mantém acesso aos fluxos de `bot.json` que ainda não foram migrados.
- O diagrama `start` contém um início `CALLBACK` configurado como comando `/start` e uma `RESPONSE` com texto editável. Selecionar um nó mostra seu nome e apenas suas propriedades: comando no início e texto na resposta.
- A paleta à esquerda permite arrastar Callback, Timer, Converter, Java, Generator, Resposta síncrona, JDBC, JsonDB e Splitter para o canvas. O diagrama admite um único início. Os novos nós são salvos sem conexão e não alteram o percurso executado; criação de conexões e configuração/execução dos novos tipos continuam pendentes.
- O nó `JAVA` tem um campo editável **Action**, guardado em `diagrams.json`. Seu retorno pode ser encaminhado ao nó de resposta com `{{output}}`; respostas e listas mantêm seus tipos quando a marca ocupa todo o campo.
- Os editores Java destacam `ProjectEnvironment`, `context` e membros acessados diretamente por `context` com a cor de palavra-chave do tema; esses identificadores são configurados pelo aplicativo.
- No projeto do bot mencionado acima existem nove diagramas habilitados: `/start`, `/testeg1`, `/topic`, `/destinos`, `/whoami`, `/actions`, `/bindings`, `/youtube_list` e `/youtube_check`. Oito possuem etapas Java e de resposta síncrona. Algumas actions ainda chamam classes legadas de `scripts/`; a lógica dessas classes ainda não está representada visualmente.
- As actions de `/topic`, `/destinos`, `/actions`, `/bindings`, `/youtube_list` e `/testeg1` agora guardam no diagrama a lógica específica do comando. As classes retêm operações compartilhadas, como leitura de dados e feed. `/youtube_check` ainda usa `YouTubeAction.check`, também usada pelo fluxo periódico; sua orquestração visual segue pendente.
- O runner prioriza um diagrama habilitado compatível com o evento; os demais fluxos continuam em `bot.json`. A implementação atual executa conexões incondicionais de início para Java e resposta síncrona, ou diretamente para resposta; diagramas antigos ainda podem usar a action legada por `flowId`.
- `diagrams.json` armazena componentes, posições e conexões, é validado na leitura e na gravação e salvo de forma atômica. O modelo admite condições e subfluxos, mas isso ainda não significa que o editor ou o runner os executem.

Os detalhes e referências visuais estão em [proposta do editor de diagramas](proposta-editor-diagramas.md). Registre mudanças de direção em [DECISIONS.md](DECISIONS.md) e mantenha as próximas entregas em [TODO.md](TODO.md).
