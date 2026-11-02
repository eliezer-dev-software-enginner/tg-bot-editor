# Proposta: editor de diagramas para bots Telegram

> Registro de ideia, sem decisão de implementação. A análise de requisitos, a pesquisa sobre o TOTVS iPaaS e a refatoração ficam para uma próxima sessão ou autorização explícita.

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

## Vocabulário proposto

| Área | Ideia inicial | Questão a resolver depois |
| --- | --- | --- |
| Início | **Timer** para execuções agendadas; **Callback** para eventos do Telegram | O runtime atual também recebe `COMMAND`, `TEXT`, `PHOTO` e `DOCUMENT`. É preciso definir se todos entram pelo componente Callback, se viram filtros nele ou se exigem outros inícios. Não eliminar esses gatilhos sem preservar os fluxos existentes. |
| Etapas | **Converter**, **Java**, **Generator** e **Resposta síncrona** | Definir entradas, saídas, contratos de dados, erros e o efeito de cada etapa no bot. Os nomes são candidatos, não APIs confirmadas. |
| Canvas | Inserir e conectar componentes; no hover, oferecer informação, configuração, duplicação e exclusão | Definir regras de conexão, ramificações, ciclo, ordenação e validação. |
| Conexões condicionais | Ao clicar na seta de uma conexão, abrir a configuração de uma condição. O ramo `if` aparece com tracejado mais leve; o ramo alternativo (`else`) usa traços mais longos e densos. | Definir precedência entre condições, comportamento quando nenhuma corresponde, limites de ramos e como apresentar essas diferenças também por texto/ícone, além do tracejado. |
| Configuração | Árvore das etapas do diagrama à esquerda e formulário à direita; permitir preencher ou arrastar dados de etapas anteriores | Definir tipagem, escopo, representação das referências e atualização ao renomear ou excluir uma etapa. |
| Projeto | Permitir múltiplos diagramas no mesmo projeto | Definir como cada diagrama é acionado, identificado, chamado por outro diagrama e publicado sem conflito de comandos/callbacks. |

Uma hipótese útil para o bot é tratar cada diagrama como um fluxo de entrada independente, composto por um início e etapas subsequentes. Essa hipótese ainda precisa ser confrontada com os fluxos atuais, especialmente respostas a botões, ações reutilizáveis e agendamentos.

## Condições nas conexões

As novas capturas mostram que a seta da conexão permite criar um ramo condicional. O formulário exibe nome, uma opção **Tipo alternativa**, valores e grupos de condições. Em uma condição comum (`if`), cada comparação combina dado de entrada, operador e dado de saída; os operadores visíveis são igual, diferente, menor, maior, menor ou igual e maior ou igual. Há controles para adicionar condições e grupos, inclusive internos. A área de configuração mostra dados de componentes anteriores para referência no formulário. Quando **Tipo alternativa** está ativado, o ramo equivale a `else`: é executado se nenhuma condição configurada for atendida e não recebe comparações próprias. No canvas, os padrões de tracejado distinguem visualmente os ramos `if` e `else`.

Para o bot, isso sugere que a decisão de seguir um caminho pertence à **conexão**, enquanto a etapa executa uma ação. Essa é apenas uma hipótese de modelagem. A semântica de grupos, operadores para tipos não numéricos, dados ausentes, múltiplos ramos verdadeiros e convergência de caminhos ainda precisa ser definida antes de implementação.

## Estado atual a preservar

Hoje o projeto guarda fluxos em `bot.json` e lógica Java em `scripts/`. O runtime aceita `COMMAND`, `TEXT`, `CALLBACK`, `PHOTO`, `DOCUMENT`, `INTERVAL` e `DAILY`; o editor oferece criação por gatilho e edição de script. A lógica e as respostas de um bot específico pertencem ao projeto gerado, não ao editor. Uma futura migração deverá abrir projetos existentes e conservar seus comandos, callbacks, dados e comportamento antes de substituir o formato ou a interface.

## Investigação futura, ainda não iniciada

1. Levantar requisitos do bot e mapear cada fluxo existente para o modelo de diagramas, incluindo comandos, callbacks, agendamentos, roteamento, persistência e execução no Termux.
2. Estudar documentação pública do TOTVS iPaaS e confrontá-la com as capturas e com o uso real pretendido. Referências indicadas: [visão geral](https://www.totvs.com/blog/negocios/totvs-ipaas/), [conceitos de iPaaS](https://www.totvs.com/blog/gestao-de-vendas/ipaas/), [central de atendimento](https://centraldeatendimento.totvs.com/hc/pt-br/sections/41614636890903-Integra%C3%A7%C3%B5es-e-Plataforma) e [ficha técnica](https://produtos.totvs.com/ficha-tecnica/tudo-sobre-o-totvs-ipaas/). Os links estão registrados, mas seu conteúdo não foi analisado nesta sessão.
3. Avaliar um formato versionado para múltiplos diagramas, modelo de dados entre etapas, compatibilidade com `bot.json`, execução e testes.
4. Só então planejar os componentes de UI necessários na codebase Megalodonte e as mudanças no editor e runtime, com migração verificável do projeto canônico do bot.
