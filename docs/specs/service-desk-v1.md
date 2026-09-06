# Especificação — Central de Serviços v1

## Objetivo

Permitir que colaboradores de uma única instituição abram solicitações, acompanhem a trilha de atendimento e colaborem com uma fila interna segura e responsiva.

## Perfis

- REQUESTER abre e visualiza apenas solicitações próprias.
- AGENT visualiza a fila única e opera solicitações.
- MANAGER possui as capacidades de AGENT e também redistribui e supervisiona prazos.
- ADMIN administra a instalação; precisa acumular AGENT ou MANAGER para acessar conteúdo operacional.

## Solicitações

- Assunto e descrição são obrigatórios; anexos são opcionais.
- O MVP não executa varredura antimalware. Novos anexos usam NOT_SCANNED; permanecem as validações de extensão, conteúdo, tamanho e quantidade.
- Categoria é opcional na abertura e obrigatória antes de `IN_PROGRESS`.
- Estados: OPEN, IN_PROGRESS, WAITING_REQUESTER e RESOLVED.
- Comentários e anexos são imutáveis; notas internas não aparecem ao solicitante.
- RESOLVED pode voltar para IN_PROGRESS pelo solicitante durante a janela configurada, inicialmente 7 dias.
- Prioridades: LOW, NORMAL, HIGH e CRITICAL.
- Prazo é opcional, manual e não representa SLA.

## Administração

- Contas são criadas por ADMIN com senha temporária de exibição única.
- Cores e fundo do login podem mudar sem rebuild, respeitando contraste AA.
- SMTP envia apenas recuperação de senha e mensagens de teste.
- Configuração sensível nunca é devolvida ao navegador.

## Agenda

- MANAGER cria e gerencia eventos institucionais e demandas internas compartilhadas entre todos os Administrativos.
- REQUESTER visualiza somente eventos institucionais; demandas internas são removidas da consulta no backend.
- AGENT e ADMIN sem REQUESTER ou MANAGER não acessam a Agenda.
- Eventos possuem título, descrição opcional, local opcional e período; são publicados imediatamente.
- Demandas possuem título, descrição opcional, zero ou mais responsáveis MANAGER ativos e andamento PENDING ou COMPLETED.
- Itens aceitam horário ou dia inteiro no fuso configurado para a instituição; o início é inclusivo e o término exclusivo.
- Qualquer MANAGER pode alterar, concluir, reabrir ou excluir qualquer item da Agenda.

## Tarefas

- A rota `/tarefas` é exclusiva de MANAGER e apresenta as mesmas demandas internas da Agenda em tabela mensal; não existe cópia ou sincronização de cadastros.
- Criar, editar, concluir, reabrir e excluir usa a API da Agenda, com auditoria e versão obrigatória nas alterações.
- Prioridades de demandas: LOW (Baixa), MEDIUM (Média) e HIGH (Alta). Demandas existentes e novas sem prioridade recebem MEDIUM; atualizações sem prioridade ou com null preservam o valor. Eventos não possuem prioridade.
- A tabela apresenta data, dia da semana, tarefa, prioridade, status, horário, responsável, observações (descrição) e ações. Responsáveis são Administrativos ativos, selecionados individualmente; o filtro encontra a tarefa por qualquer responsável.
- O mês inicial é o atual no fuso institucional. Itens com sobreposição ao mês aparecem uma vez, incluindo períodos entre meses; o término permanece exclusivo.
- Os indicadores são totais do mês e não mudam com os filtros de texto, prioridade, status e responsável.
- Formulários e detalhes são compartilhados com a Agenda. Conflitos atualizam a consulta e exibem erro sem substituir o formulário em edição.
- Não inclui turnos, importação ou atualização em tempo real.

## Ocorrências do dia e múltiplos responsáveis

- O painel de ocorrências fica junto da tabela de Tarefas, à direita em telas largas e expansível acima da tabela em telas menores. A seleção de data é limitada ao mês atual da tela; dias com anotações têm atalhos visíveis.
- Ocorrências são anotações independentes de demandas, com data civil da instituição, texto obrigatório de até 4000 caracteres, autor, timestamps e versão. É possível registrar várias no mesmo dia, mesmo sem tarefas.
- MANAGER pode ler, criar, editar e excluir qualquer ocorrência. As operações são auditadas sem registrar o texto em logs. REQUESTER, AGENT e ADMIN sem MANAGER não acessam ocorrências.
- `/api/v1/agenda/occurrences` consulta datas com início inclusivo e fim exclusivo; POST cria; PATCH e DELETE em `/{id}` exigem versão. Conflitos mantêm o rascunho do usuário e atualizam os dados consultados.
- A edição de demandas envia `assigneeIds` (até 100 IDs, deduplicados), e a resposta inclui `assignees` com id e displayName. Uma lista vazia remove todos. Todos precisam ser Administrativos ativos; eventos aceitam somente lista vazia.
- Os campos legados `assigneeId` e `assigneeName` refletem a primeira pessoa. A lista tem precedência; se o campo singular também for preenchido, precisa corresponder ao primeiro ID. Sem lista, o comportamento singular é mantido; repetir a primeira pessoa preserva as demais, mudar a pessoa substitui a lista e null limpa as atribuições.
- A migração 005 copia os responsáveis existentes para a tabela de associação e cria o armazenamento de ocorrências sem modificar registros de tarefas, status ou versões. A data da ocorrência usa DATE (sem horário); os timestamps de auditoria continuam em UTC.

## Fora de escopo

Multi-tenancy, departamentos, catálogo, base de conhecimento, aprovações, SSO, MFA, workflow configurável, e-mail operacional, S3, alta disponibilidade, recorrência e integrações com calendários externos.
