# Próximas etapas

Esta lista registra trabalho pendente; itens propostos não são funcionalidades já disponíveis.

- [ ] Migrar progressivamente os fluxos que ainda vivem no editor legado, preservando respostas, permissões, estado e comportamento do bot.
- [ ] Substituir chamadas de classes legadas contidas nas actions Java por etapas visuais de dados, decisões e respostas, mantendo Java para operações pontuais. A orquestração compartilhada de `YouTubeAction.check` ainda atende ao comando e ao agendamento.
- [ ] Implementar teste local e prévia das actions Java a partir do diagrama; o teste dos comandos antigos executa o fluxo legado.
- [ ] Permitir conectar, configurar, duplicar e excluir componentes no diagrama, com operações de salvamento e reabertura confiáveis. A paleta já adiciona nós por arraste, inicialmente sem conexão.
- [ ] Implementar e validar condições nas conexões, incluindo grupos, comparações e ramo alternativo; diferenciá-las visualmente conforme as referências.
- [ ] Definir e implementar componentes de dados e processamento, incluindo JsonDB, JDBC, Converter, Generator e Splitter com subfluxos e acesso aos dados de etapas anteriores.
- [ ] Permitir compor visualmente mensagens, imagens e botões do Telegram, incluindo o encaminhamento dos cliques para outros fluxos.
- [ ] Definir o modelo dos gatilhos restantes, dos agendamentos e das interações entre diagramas, preservando os tipos existentes no runner.
- [ ] Estudar a documentação pública do TOTVS iPaaS indicada na proposta e confrontá-la com os requisitos do bot antes de assumir comportamentos não observados.
- [ ] Validar os percursos migrados em execução real no Telegram quando houver autorização e configuração para isso; os testes locais não comprovam entregas reais.
- [ ] Atualizar [CONTEXT.md](CONTEXT.md), [DECISIONS.md](DECISIONS.md) e esta lista conforme as próximas alterações forem concluídas ou decisões forem tomadas.
