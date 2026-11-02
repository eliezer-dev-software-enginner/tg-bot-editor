# Contexto e andamento

O TG-bot Editor cria e executa projetos de bots Telegram. O editor fornece mecanismos genéricos; comandos, respostas e regras de negócio pertencem a cada projeto. O bot `meu-primeiro-projeto` é um repositório separado: alterações no editor não atualizam automaticamente os seus dados ou runtime.

## Estado atual

- A tela inicial lista os diagramas de `diagrams.json` em cards. Cada card abre seu diagrama; **Editar comandos antigos** mantém acesso aos fluxos de `bot.json` que ainda não foram migrados.
- O diagrama `start` contém um início `CALLBACK` configurado como comando `/start` e uma `RESPONSE` com texto editável. Selecionar um nó mostra seu nome e apenas suas propriedades: comando no início e texto na resposta.
- A paleta à esquerda permite arrastar Callback, Timer, Converter, Java, Generator, Resposta síncrona, JDBC, JsonDB e Splitter para o canvas. O diagrama admite um único início. Os novos nós são salvos sem conexão e não alteram o percurso executado; criação de conexões e configuração/execução dos novos tipos continuam pendentes.
- Os nós `JAVA` existentes apresentam a action, o script em `bot.json` e a classe em `scripts/` para consulta. A action legada continua responsável pela resposta dinâmica.
- No projeto do bot mencionado acima existem nove diagramas habilitados: `/start`, `/testeg1`, `/topic`, `/destinos`, `/whoami`, `/actions`, `/bindings`, `/youtube_list` e `/youtube_check`. Oito delegam à action Java anterior; eles não representam ainda a lógica completa visualmente.
- O runner prioriza um diagrama habilitado compatível com o evento; os demais fluxos continuam em `bot.json`. A implementação atual executa uma conexão incondicional para uma resposta textual ou uma action Java existente.
- `diagrams.json` armazena componentes, posições e conexões, é validado na leitura e na gravação e salvo de forma atômica. O modelo admite condições e subfluxos, mas isso ainda não significa que o editor ou o runner os executem.

Os detalhes e referências visuais estão em [proposta do editor de diagramas](proposta-editor-diagramas.md). Registre mudanças de direção em [DECISIONS.md](DECISIONS.md) e mantenha as próximas entregas em [TODO.md](TODO.md).
