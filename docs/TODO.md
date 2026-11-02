# Próximas etapas

Esta lista registra trabalho pendente; itens propostos não são funcionalidades já disponíveis.

- [ ] Migrar progressivamente os fluxos que ainda vivem no editor legado, preservando respostas, permissões, estado e comportamento do bot.
- [ ] Substituir chamadas de classes legadas contidas nas actions Java por etapas visuais de dados, decisões e respostas, mantendo Java para operações pontuais. A orquestração compartilhada de `YouTubeAction.check` ainda atende ao comando e ao agendamento.
- [ ] Implementar teste local e prévia das actions Java a partir do diagrama; a interface inicial já não expõe o editor de comandos antigos.
- [ ] Permitir conectar, configurar, duplicar e excluir componentes no diagrama, com operações de salvamento e reabertura confiáveis. A paleta já adiciona nós por arraste, inicialmente sem conexão.
- [ ] Implementar e validar condições nas conexões, incluindo grupos, comparações e ramo alternativo; diferenciá-las visualmente conforme as referências.
- [ ] Implementar JsonDB, JDBC, Converter e Generator; ampliar o Splitter para caminhos de dados estruturados e tratamento de erros.
- [ ] Permitir compor visualmente mensagens, imagens e botões do Telegram, incluindo o encaminhamento dos cliques para outros fluxos.
- [ ] Criar o componente **Button** na paleta e exibir, no painel de propriedades à direita, uma prévia de como o Telegram apresentará os componentes de saída, incluindo **Resposta síncrona** e **Button**. A prévia deve acompanhar os valores configurados.
- [ ] Definir o modelo dos gatilhos restantes, dos agendamentos e das interações entre diagramas, preservando os tipos existentes no runner.
- [ ] Estudar a documentação pública do TOTVS iPaaS indicada na proposta e confrontá-la com os requisitos do bot antes de assumir comportamentos não observados.
- [ ] Validar os percursos migrados em execução real no Telegram quando houver autorização e configuração para isso; os testes locais não comprovam entregas reais.
- [ ] Atualizar [CONTEXT.md](CONTEXT.md), [DECISIONS.md](DECISIONS.md) e esta lista conforme as próximas alterações forem concluídas ou decisões forem tomadas.

- [ ] Criar e conectar diagramas visualmente; criar componentes de mensagens, botões, condições e persistência para substituir a lógica ainda concentrada em scripts.
- [ ] Migrar a lógica interna das tarefas de anúncios e YouTube hoje delegadas a `GenshinAdsAction.tick()` e `YouTubeAction.check()`.

- [ ] Criar componente de composição de mensagem com inclusão e remoção visual de elementos Image, Text e Button, vinculados a dados; substituir progressivamente construção manual de `BotReply` nos fluxos.
- [ ] Para o G1, retornar objetos de notícia na action de busca e usar IF/ELSE nas setas do subfluxo para selecionar composições com ou sem imagem.
