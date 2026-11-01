# Editor de projetos de bot

O editor cria e executa fluxos definidos por cada projeto. Não incorpore aqui
comandos, respostas, permissões, assinaturas de canais ou regras de negócio de
um bot específico. Essas regras pertencem a `bot.json` e `scripts/` no projeto
gerado ou no checkout canônico do bot. A UI deve listar apenas comandos
`COMMAND` realmente presentes no projeto.

O runtime empacotado pelo editor fornece somente gatilhos, execução de ações,
agendamento, persistência e transporte Telegram. Não adicione verificadores
periódicos específicos no `BotRunner`; represente-os como fluxos do projeto.
Ao alterar a API do runtime, atualize as fontes empacotadas em
`ProjectWorkspace` e valide um projeto criado pelo editor.
