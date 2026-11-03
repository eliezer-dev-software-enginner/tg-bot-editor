# Próximas etapas

Esta lista registra trabalho pendente; itens propostos não são funcionalidades já disponíveis.

- [ ] Migrar progressivamente os fluxos que ainda vivem no editor legado, preservando respostas, permissões, estado e comportamento do bot.
- [ ] Substituir chamadas de classes legadas contidas nas actions Java por etapas visuais de dados, decisões e respostas, mantendo Java para operações pontuais. A consulta e seleção de vídeos ainda usam `YouTubeAction.checkVisual`; a composição das notificações está nos diagramas.
- [ ] Implementar teste local e prévia das actions Java a partir do diagrama; a interface inicial já não expõe o editor de comandos antigos.
- [x] Permitir duplicar componentes comuns sem conexões, mantendo configurações independentes.
- [x] Permitir excluir componentes e suas conexões, preservando subfluxos.
- [ ] Permitir duplicar Splitter com subfluxo independente e ampliar a edição de conexões.
- [ ] Ampliar o editor de condições para grupos e comparações múltiplas; o executor já avalia grupos e o painel edita uma comparação simples IF ou ELSE ao clicar na seta.
- [ ] Ampliar o JsonDB com filtros; LIST, INSERT, UPSERT, DELETE, SET, REMOVE e caminhos já existem. Implementar JDBC e Generator; ampliar Converter conforme novos formatos necessários; ampliar o tratamento de erros do Splitter e a seleção visual de caminhos.
- [ ] Ampliar a composição visual de mensagens, imagens e botões do Telegram e seus fluxos de clique; composição e botões de callback já estão disponíveis.
- [ ] Criar o componente **Button** na paleta e exibir, no painel de propriedades à direita, uma prévia de como o Telegram apresentará os componentes de saída, incluindo **Button**. A prévia de Resposta síncrona acompanha texto fixo e usa exemplo para referências dinâmicas.
- [ ] Definir o modelo dos gatilhos restantes, dos agendamentos e das interações entre diagramas, preservando os tipos existentes no runner.
- [ ] Estudar a documentação pública do TOTVS iPaaS indicada na proposta e confrontá-la com os requisitos do bot antes de assumir comportamentos não observados.
- [ ] Validar os percursos migrados em execução real no Telegram quando houver autorização e configuração para isso; os testes locais não comprovam entregas reais.
- [ ] Atualizar [CONTEXT.md](CONTEXT.md), [DECISIONS.md](DECISIONS.md) e esta lista conforme as próximas alterações forem concluídas ou decisões forem tomadas.

- [x] Criar diagramas visualmente como rascunhos, com ativação após configurar e conectar o início.
- [ ] Ampliar os componentes de mensagens, botões e persistência para substituir a lógica ainda concentrada em scripts.
- [ ] Migrar as chamadas remanescentes do agendamento de anúncios e a consulta de vídeos do YouTube; as mensagens já usam composição visual e o agendamento de anúncios não chama `GenshinAdsAction.tick()`.

- [x] Ampliar o componente de composição com prévia visual e agrupamento de botões por linha.
- [ ] Substituir progressivamente `BotReply` nos fluxos por composição visual; a mensagem de resultado do teste G1 já usa composição.
- [x] Para o G1 agendado, retornar objetos de notícia na action de busca e usar IF/ELSE nas setas do subfluxo para selecionar composições com ou sem imagem.

- [x] Permitir cabeçalhos personalizados no Rest HTTP.
- [x] Configurar autenticação Bearer por variável de ambiente no Rest HTTP.
- [x] Permitir referências a input e output na URL, no corpo e nos valores dos cabeçalhos do Rest HTTP.
- [ ] Migrar as demais requisições manuais para Rest HTTP; os fluxos G1 já usam esse componente.

- [ ] Continuar migrando os fluxos de anúncios para composição visual. `/listar`, `/publicar` e `/config` já compõem mensagens e botões no diagrama; exclusão e início da edição possuem callbacks visuais, enquanto outros callbacks ainda usam o fluxo legado.
- [ ] Migrar o recebimento de ZIP de `/importarads`; o comando e os callbacks de escolha já são visuais e persistem pelo JsonDB.

- [ ] Substituir os retornos antecipados `FlowStop` por condições visuais quando as condições visuais alcançarem todas as verificações e dados necessários.
# Persistência de anúncios

- [ ] Investigar operações transacionais entre componentes JsonDB e a concorrência com código legado antes de remover as pontes remanescentes.
- [ ] Avaliar filtros e edição de propriedades de objetos no JsonDB; `OBJECT` oferece apenas leitura integral.
- [ ] Permitir filtrar e alterar campos individuais e registros dentro de listas pelo caminho, com seleção visual de propriedades.
- [ ] Substituir referências digitadas de dados recebidos por seleção visual das saídas dos componentes anteriores.
- [ ] Permitir alterações transacionais que dependem da leitura anterior de uma lista, evitando conflitos entre execuções simultâneas.
- [x] Gerar distribuição portátil com JAR, diagramas e scripts Java, sem incluir `.env` nem dados particulares.
- [x] Selecionar `gradlew.bat` no Windows e `gradlew` em Unix na construção.
- [ ] Validar a construção em uma instalação real do Windows e do Termux.
- [ ] Avaliar inclusão configurável de recursos adicionais necessários a scripts, sem empacotar dados particulares automaticamente.

- [ ] Evoluir o tratamento visual de erros e transações entre componentes; a conexão `ON_ERROR` atual cobre apenas falhas do JsonDB.

- [x] Exibir prévia da Resposta síncrona no painel de propriedades.

- [x] Implementar Converter com JSON_PARSE, JSON_STRINGIFY, BASE64_ENCODE, BASE64_DECODE e TEXT_TRIM.
