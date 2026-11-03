# Próximas etapas

Esta lista registra trabalho pendente; itens propostos não são funcionalidades já disponíveis.

- [ ] Migrar progressivamente os fluxos que ainda vivem no editor legado, preservando respostas, permissões, estado e comportamento do bot.
- [ ] Substituir chamadas de classes legadas contidas nas actions Java por etapas visuais de dados, decisões e respostas, mantendo Java para operações pontuais. A orquestração compartilhada de `YouTubeAction.check` ainda atende ao comando e ao agendamento.
- [ ] Implementar teste local e prévia das actions Java a partir do diagrama; a interface inicial já não expõe o editor de comandos antigos.
- [ ] Ampliar a edição de conexões, configurar, duplicar e excluir componentes no diagrama. A paleta adiciona nós por arraste; as alças conectam saída a entrada e a seleção da seta permite excluir a conexão.
- [ ] Ampliar o editor de condições para grupos e comparações múltiplas; o executor já avalia grupos e o painel edita uma comparação simples IF ou ELSE ao clicar na seta.
- [ ] Ampliar o JsonDB além de LIST, INSERT, UPSERT e DELETE (incluindo filtros), implementar JDBC, Converter e Generator; ampliar o tratamento de erros do Splitter e a seleção visual de caminhos.
- [ ] Permitir compor visualmente mensagens, imagens e botões do Telegram, incluindo o encaminhamento dos cliques para outros fluxos.
- [ ] Criar o componente **Button** na paleta e exibir, no painel de propriedades à direita, uma prévia de como o Telegram apresentará os componentes de saída, incluindo **Resposta síncrona** e **Button**. A prévia deve acompanhar os valores configurados.
- [ ] Definir o modelo dos gatilhos restantes, dos agendamentos e das interações entre diagramas, preservando os tipos existentes no runner.
- [ ] Estudar a documentação pública do TOTVS iPaaS indicada na proposta e confrontá-la com os requisitos do bot antes de assumir comportamentos não observados.
- [ ] Validar os percursos migrados em execução real no Telegram quando houver autorização e configuração para isso; os testes locais não comprovam entregas reais.
- [ ] Atualizar [CONTEXT.md](CONTEXT.md), [DECISIONS.md](DECISIONS.md) e esta lista conforme as próximas alterações forem concluídas ou decisões forem tomadas.

- [x] Criar diagramas visualmente como rascunhos, com ativação após configurar e conectar o início.
- [ ] Ampliar os componentes de mensagens, botões e persistência para substituir a lógica ainda concentrada em scripts.
- [ ] Migrar a lógica interna das tarefas de anúncios e YouTube hoje delegadas a `GenshinAdsAction.tick()` e `YouTubeAction.check()`.

- [x] Ampliar o componente de composição com prévia visual e agrupamento de botões por linha.
- [ ] Substituir progressivamente `BotReply` nos fluxos por composição visual; a mensagem de resultado do teste G1 já usa composição.
- [x] Para o G1 agendado, retornar objetos de notícia na action de busca e usar IF/ELSE nas setas do subfluxo para selecionar composições com ou sem imagem.

- [ ] Ampliar o **Rest HTTP** com cabeçalhos personalizados, autenticação e valores dinâmicos. Migrar as requisições manuais dos fluxos que precisarem de HTTP; os dois fluxos G1 já usam esse componente, preservando validações, tratamento de erros e destinos.

- [ ] Continuar migrando os fluxos de anúncios para composição visual. `/listar`, `/publicar` e `/config` já compõem mensagens e botões no diagrama; callbacks e outros comandos ainda usam o fluxo legado.

- [ ] Substituir os retornos antecipados `FlowStop` por condições visuais quando as condições visuais alcançarem todas as verificações e dados necessários.
# Persistência de anúncios

- [ ] Investigar operações transacionais entre componentes JsonDB e a concorrência com código legado antes de remover as pontes remanescentes.
- [ ] Avaliar filtros e edição de propriedades de objetos no JsonDB; `OBJECT` oferece apenas leitura integral.
- [ ] Permitir filtrar e alterar campos individuais e registros dentro de listas pelo caminho, com seleção visual de propriedades.
- [ ] Substituir referências digitadas de dados recebidos por seleção visual das saídas dos componentes anteriores.
