# Proposta: editor de diagramas para bots Telegram

> O primeiro percurso funcional foi o diagrama `start`: entrada `/start` conectada a uma resposta de texto. O projeto canônico já tem diagramas separados para nove comandos sem subcomandos nem botões; oito contêm uma etapa Java e uma resposta síncrona; algumas actions ainda chamam classes legadas. A análise detalhada do TOTVS iPaaS segue pendente.

## Estado da implementação

Projetos novos recebem `diagrams.json` ao lado de `bot.json`. O arquivo tem uma lista de diagramas com componentes, posições, configurações e conexões. As conexões podem guardar condições `IF`, um ramo `ELSE` e grupos de comparações; o Splitter pode referenciar um diagrama de subfluxo. A leitura e a gravação são validadas, e a gravação é atômica. O editor permite escolher um diagrama, mover nós, editar o nome do diagrama e o nome do componente selecionado. O texto da resposta é editável em nós de resposta; nós Java guardam uma action no diagrama e encaminham seu resultado para a resposta síncrona; `flowId` continua aceito por compatibilidade. No bot canônico estão habilitados `/start`, `/testeg1`, `/topic`, `/destinos`, `/whoami`, `/actions`, `/bindings`, `/youtube_list` e `/youtube_check`. Os demais comandos continuam pelo runtime anterior. O objetivo permanece reduzir código Java e montar interação, navegação e dados visualmente; as chamadas restantes a classes legadas são uma etapa de compatibilidade, não a arquitetura final. Os demais tipos de componentes e condições ainda são somente modelo de rascunho, sem execução; a configuração visual geral e os testes com Telegram real permanecem pendentes.

A paleta à esquerda agora permite arrastar componentes para o canvas e salvar suas posições. Eles são adicionados sem conexão; a edição de conexões e a configuração e execução dos novos tipos continuam pendentes. O editor impede a inclusão de um segundo componente de início.

## Objetivo

Evoluir a edição atual de gatilhos e ações isolados para diagramas visuais compostos por etapas conectadas. As capturas fornecidas por Eliezer mostram uma paleta lateral, um canvas com conexões, ações contextuais no componente, configuração com árvore das etapas e um projeto contendo vários diagramas. São referências de interação, não uma especificação do comportamento interno do TOTVS iPaaS.

## Capturas de referência

- [Paleta de componentes e canvas](images/ipaas-paleta-e-canvas.png)
- [Lista de componentes, incluindo Resposta Síncrona](images/ipaas-componentes-resposta-sincrona.png)
- [Seleção de componente de início](images/ipaas-componentes-inicio.png)
- [Ações contextuais ao passar o mouse](images/ipaas-acoes-contextuais.png)
- [Múltiplos diagramas em um projeto](images/ipaas-multiplos-diagramas.png)
- [Formulário de condição aberto pela seta da conexão](images/ipaas-condicao-formulario.png)
- [Operadores de comparação da condição](images/ipaas-condicao-operadores.png)
- [Condição `if` preenchida](images/ipaas-condicao-if.png)
- [Condição alternativa (`else`)](images/ipaas-condicao-alternativa.png)
- [Ramos condicionais no canvas](images/ipaas-ramos-if-else.png)
- [JDBC seguido de Splitter](images/ipaas-jdbc-splitter-canvas.png)
- [Consulta configurada no componente JDBC](images/ipaas-jdbc-query.png)
- [Configurações reutilizáveis do JDBC](images/ipaas-jdbc-configuracoes.png)
- [Fluxo que entrega uma lista ao Splitter](images/ipaas-splitter-fluxo.png)
- [Splitter no canvas](images/ipaas-splitter-canvas.png)
- [Caminho do array de entrada do Splitter](images/ipaas-splitter-configuracao.png)
- [Ações contextuais do Splitter](images/ipaas-splitter-acoes.png)
- [Ação Criar subfluxo](images/ipaas-splitter-criar-subfluxo.png)
- [Subfluxo aberto para processamento dos itens](images/ipaas-splitter-subfluxo.png)
- [Splitter configurado com o caminho do array](images/ipaas-splitter-caminho-array.png)
- [Script anterior que prepara a lista para o Splitter](images/ipaas-script-transformacao-lista.png)

As capturas que expõem configuração de conexão e caminho particular do computador não foram copiadas para a documentação versionada.

## Vocabulário proposto

| Área | Ideia inicial | Questão a resolver depois |
| --- | --- | --- |
| Início | **Timer** para execuções agendadas; **Callback** para eventos do Telegram | O runtime atual também recebe `COMMAND`, `TEXT`, `PHOTO` e `DOCUMENT`. É preciso definir se todos entram pelo componente Callback, se viram filtros nele ou se exigem outros inícios. Não eliminar esses gatilhos sem preservar os fluxos existentes. |
| Etapas | **Converter**, **Java**, **Generator**, **Resposta síncrona**, **JDBC**, **JsonDB** e **Splitter** | Definir entradas, saídas, contratos de dados, erros e o efeito de cada etapa no bot. Os nomes são candidatos, não APIs confirmadas. |
| Canvas | Inserir e conectar componentes; no hover, oferecer informação, configuração, duplicação e exclusão | Definir regras de conexão, ramificações, ciclo, ordenação e validação. |
| Conexões condicionais | Ao clicar na seta de uma conexão, abrir a configuração de uma condição. O ramo `if` aparece com tracejado mais leve; o ramo alternativo (`else`) usa traços mais longos e densos. | Definir precedência entre condições, comportamento quando nenhuma corresponde, limites de ramos e como apresentar essas diferenças também por texto/ícone, além do tracejado. |
| Configuração | Árvore das etapas do diagrama à esquerda e formulário à direita; permitir preencher ou arrastar dados de etapas anteriores | Definir tipagem, escopo, representação das referências e atualização ao renomear ou excluir uma etapa. |
| Projeto | Permitir múltiplos diagramas no mesmo projeto | Definir como cada diagrama é acionado, identificado, chamado por outro diagrama e publicado sem conflito de comandos/callbacks. |

Uma hipótese útil para o bot é tratar cada diagrama como um fluxo de entrada independente, composto por um início e etapas subsequentes. Essa hipótese ainda precisa ser confrontada com os fluxos atuais, especialmente respostas a botões, ações reutilizáveis e agendamentos.

## Meta: reduzir a lógica nos scripts Java

O editor visual deve assumir a navegação e a orquestração do bot: reconhecer entradas e padrões de callback, escolher caminhos, avaliar condições, ligar etapas, montar respostas e determinar qual ação vem depois. A composição da interface enviada ao Telegram também deve ser visual: texto, imagens, botões, rótulos, dados de callback e outras opções de resposta devem ser configuráveis no diagrama, inclusive com valores retornados pelas etapas anteriores. Scripts não devem instanciar ou manipular `Button`, `Image`, `BotButton`, `BotReply` ou componentes equivalentes para montar a interação. O componente **Java** deve ficar reservado a uma operação pequena sobre os dados recebidos, retornando seu resultado para o diagrama. Não deve precisar interpretar famílias de comandos, decidir telas ou encadear a conversa.

O projeto canônico ilustra a necessidade dessa mudança: vários fluxos de `bot.json` chamam `GenshinAdsAction.run(context)`, inclusive o padrão `ads:*`. A classe `GenshinAdsAction` examina o prefixo `ads:`, resolve os diferentes callbacks e também cria os botões e conduz a navegação dos anúncios. A meta é representar esse roteamento, as escolhas, as transições e a composição das mensagens no diagrama, deixando em Java apenas operações pontuais que precisem de código. Por exemplo: o fluxo visual reconhece `ads:edit:<id>`, extrai o identificador, decide a etapa seguinte e configura a mensagem, a imagem e os botões; uma etapa Java, se necessária, recebe o identificador, consulta ou altera os dados do anúncio e devolve um resultado estruturado.

Isso é uma direção de arquitetura, não uma migração já implementada. Na análise futura, será preciso decidir quais operações de dados merecem componentes próprios e quais ainda justificam Java, de modo que a redução de scripts não apenas transfira lógica complexa para outro arquivo invisível no editor. A migração deverá preservar o comportamento dos anúncios e demais fluxos existentes.

## Dados e processamento de listas

**JDBC** seria a etapa para bancos relacionais, inspirada no componente mostrado nas capturas: uma configuração de conexão reutilizável e, no diagrama, a operação que usa essa configuração. **JsonDB** seria a opção nativa para os projetos que já persistem e fazem CRUD em JSON, permitindo manter esses dados e comportamentos durante a evolução do bot, sem exigir a criação de um banco relacional. Sua configuração visual poderia seguir a lógica de selecionar um serviço/operação: **Listar**, **Criar**, **Consultar**, **Atualizar** ou **Deletar**, com campos próprios para coleção/caminho, filtro, identificador e valores recebidos das etapas anteriores. Esses nomes e campos são uma proposta inicial; formato, concorrência, gravação atômica, erros e compatibilidade com o JSON atual precisam ser definidos depois.

O **Splitter** recebe uma lista produzida por uma etapa anterior e executa um subfluxo para cada item. A captura mostra a escolha do caminho do array a partir da árvore de dados e uma ação contextual **Criar subfluxo**, que também deve permitir abrir um subfluxo já associado. O subfluxo é um diagrama visual próprio para o processamento individual do item; por exemplo, `JsonDB Listar anúncios → Splitter → subfluxo que prepara e envia cada anúncio`. Isso evita escrever um laço e a navegação em Java. Na análise futura, definir o contexto de cada item, ordem de execução, tratamento de falhas, agregação de resultados e relação entre subfluxos e os vários diagramas do projeto.

As capturas adicionais deixam explícito o contrato entre etapas no exemplo do iPaaS: o script **validar csv** recebe `id-ftp1.result`, normaliza a resposta JSON, filtra arquivos temporários, não CSV, vazios ou maiores que 5 MB, ordena os válidos e devolve um objeto com `total`, `arquivos` e `ignorados`. O Splitter recebe especificamente `id-javascript2.output.arquivos`, e cada elemento dessa lista entra em seu subfluxo. O código fornecido pelo usuário é referência de comportamento e de passagem de dados, não uma instrução para usar JavaScript no bot nem para copiar toda essa lógica para um script Java. Na nossa proposta, o contrato de saída deve ser visível e selecionável na árvore; operações comuns de conversão, filtro e ordenação devem ser avaliadas para configuração visual, reservando **Java** apenas para transformações pontuais que não tenham componente apropriado. Também será necessário definir como acessar os demais dados do objeto, como `total` e `ignorados`, nas etapas seguintes.

## Condições nas conexões

As novas capturas mostram que a seta da conexão permite criar um ramo condicional. O formulário exibe nome, uma opção **Tipo alternativa**, valores e grupos de condições. Em uma condição comum (`if`), cada comparação combina dado de entrada, operador e dado de saída; os operadores visíveis são igual, diferente, menor, maior, menor ou igual e maior ou igual. Há controles para adicionar condições e grupos, inclusive internos. A área de configuração mostra dados de componentes anteriores para referência no formulário. Quando **Tipo alternativa** está ativado, o ramo equivale a `else`: é executado se nenhuma condição configurada for atendida e não recebe comparações próprias. No canvas, os padrões de tracejado distinguem visualmente os ramos `if` e `else`.

Para o bot, isso sugere que a decisão de seguir um caminho pertence à **conexão**, enquanto a etapa executa uma ação. Essa é apenas uma hipótese de modelagem. A semântica de grupos, operadores para tipos não numéricos, dados ausentes, múltiplos ramos verdadeiros e convergência de caminhos ainda precisa ser definida antes de implementação.

## Estado atual a preservar

Hoje o projeto guarda fluxos em `bot.json` e lógica Java em `scripts/`. O runtime aceita `COMMAND`, `TEXT`, `CALLBACK`, `PHOTO`, `DOCUMENT`, `INTERVAL` e `DAILY`; o editor oferece criação por gatilho e edição de script. A lógica e as respostas de um bot específico pertencem ao projeto gerado, não ao editor. Uma futura migração deverá abrir projetos existentes e conservar seus comandos, callbacks, dados e comportamento antes de substituir o formato ou a interface.

## Investigação e planejamento pendentes

1. Levantar requisitos do bot e mapear cada fluxo existente para o modelo de diagramas, incluindo comandos, callbacks, agendamentos, roteamento, persistência e execução no Termux.
2. Estudar documentação pública do TOTVS iPaaS e confrontá-la com as capturas e com o uso real pretendido. Referências indicadas: [visão geral](https://www.totvs.com/blog/negocios/totvs-ipaas/), [conceitos de iPaaS](https://www.totvs.com/blog/gestao-de-vendas/ipaas/), [central de atendimento](https://centraldeatendimento.totvs.com/hc/pt-br/sections/41614636890903-Integra%C3%A7%C3%B5es-e-Plataforma) e [ficha técnica](https://produtos.totvs.com/ficha-tecnica/tudo-sobre-o-totvs-ipaas/). Os links estão registrados, mas seu conteúdo não foi analisado nesta sessão.
3. Aprofundar o formato versionado já iniciado, o modelo de dados entre etapas, a compatibilidade com `bot.json`, a execução e os testes.
4. Planejar os componentes de UI e as mudanças adicionais no editor e runtime, com migração verificável do projeto canônico do bot. Consulte [CONTEXT.md](CONTEXT.md), [DECISIONS.md](DECISIONS.md) e [TODO.md](TODO.md) para o andamento.
