# Especificação — Central de Serviços v1

## Objetivo

Permitir que colaboradores de uma única instituição abram solicitações, acompanhem a trilha de atendimento e colaborem com uma fila interna segura e responsiva.

## Perfis

- REQUESTER abre e visualiza apenas solicitações próprias.
- AGENT visualiza a fila única e opera solicitações.
- MANAGER possui as capacidades de AGENT e também redistribui solicitações.
- ADMIN administra a instalação; precisa acumular AGENT ou MANAGER para acessar conteúdo operacional.

## Solicitações

- Categoria ativa e descrição são obrigatórias; anexos são opcionais.
- O MVP não executa varredura antimalware. Novos anexos usam NOT_SCANNED; permanecem as validações de extensão, conteúdo, tamanho e quantidade.
- Categoria substitui assunto na identificação visual. Solicitações antigas sem categoria exibem “Sem categoria” e devem ser classificadas antes de `IN_PROGRESS`.
- Estados: OPEN, IN_PROGRESS, WAITING_REQUESTER e RESOLVED.
- Comentários e anexos são imutáveis; notas internas não aparecem ao solicitante.
- RESOLVED pode voltar para IN_PROGRESS pelo solicitante durante a janela configurada, inicialmente 7 dias.
- Solicitações não possuem assunto, prioridade ou prazo de atendimento. A janela de reabertura permanece configurável.

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

- A rota `/tarefas` é exclusiva de MANAGER e apresenta as mesmas demandas internas da Agenda em tabela diária por padrão, com opção mensal; não existe cópia ou sincronização de cadastros.
- Criar, editar, concluir, reabrir e excluir usa a API da Agenda, com auditoria e versão obrigatória nas alterações.
- Prioridades de demandas: LOW (Baixa), MEDIUM (Média) e HIGH (Alta). Demandas existentes e novas sem prioridade recebem MEDIUM; atualizações sem prioridade ou com null preservam o valor. Eventos não possuem prioridade.
- A tabela apresenta data, dia da semana, tarefa, prioridade, status, horário, responsável, observações (descrição) e ações. Responsáveis são Administrativos ativos, selecionados individualmente; o filtro encontra a tarefa por qualquer responsável.
- A tela abre no dia atual do fuso institucional. O seletor Dia/Mês mantém a data de referência, com navegação anterior/próxima e retorno a Hoje/Mês atual. Itens com sobreposição ao período aparecem uma vez, incluindo tarefas de vários dias e turnos; o término permanece exclusivo. Nova tarefa usa a data selecionada no modo diário.
- Os indicadores acompanham o período selecionado (dia ou mês) e não mudam com os filtros de texto, prioridade, status e responsável.
- Formulários e detalhes são compartilhados com a Agenda. Conflitos atualizam a consulta e exibem erro sem substituir o formulário em edição.
- Demandas sem dia inteiro permitem Manhã (06–12), Tarde (12–18), Noite (18–00) ou horário personalizado. Turnos exigem apenas datas inicial e final inclusivas, no fuso institucional.
- O campo opcional shift (MORNING, AFTERNOON, NIGHT) persiste o turno. startAt/endAt delimitam o primeiro e último turno, com término exclusivo; ausência ou null desativa o turno. Eventos e dia inteiro não aceitam turno.
- A agenda exibe um bloco por data selecionada, incluindo fins de semana e feriados, todos vinculados à mesma tarefa. Totais contam a tarefa uma vez. Editar, concluir ou excluir afeta todos os blocos.
- Não inclui importação ou atualização em tempo real.

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

## Pedagógico

- ADMIN e MANAGER administram turmas e planejamentos; esta permissão de ADMIN é específica do Pedagógico.
- REQUESTER representa a professora. Acesso depende da alocação atual e de conta ativa com esse perfil. Alocações não têm ano ou vencimento; remanejamentos revogam acesso ao histórico da turma anterior.
- Turmas podem ter várias professoras e professoras podem integrar várias turmas. Arquivar mantém consulta/impressão e bloqueia alterações até reativar.
- Diretórios virtuais: Turma / Ano / Mês / Semana. A semana é segunda–sexta; um único planejamento por turma e segunda-feira. Semanas entre meses/anos aparecem nos dois períodos sem duplicação.
- Estados DRAFT e FINALIZED. Rascunhos aceitam dados parciais; finalização exige tema, responsável e os quatro campos de cada dia, ou Sem aula com motivo.
- Qualquer professora atualmente alocada, MANAGER ou ADMIN pode finalizar/reabrir. Não há aprovação, exclusão, anexos ou edição em tempo real.
- Responsáveis são registradas com nome histórico na criação. Em rascunho podem ser mantidas/removidas; novas responsáveis são selecionadas entre solicitantes ativas alocadas. Remanejamentos não alteram o documento.
- API /api/v1/pedagogical: turmas e alocações em /classes, elegíveis em /teachers, semanas em /classes/{id}/plans, conteúdo/transições em /plans/{id}. Dias da edição são cinco objetos ordenados de segunda a sexta; datas são geradas no servidor.
- Edições exigem version; conflitos retornam 409 e preservam o formulário. Consultas e escrita verificam autorização no backend. Alterações são auditadas sem conteúdo pedagógico nos logs.
- Impressão em A4 paisagem com indicação de rascunho e alterações não salvas. Nome institucional e logotipo opcional vêm das configurações; apenas ADMIN altera o logotipo.
- Projetos e Atividade Pedagógica aparecem como Em breve.


## Pedagógico — Registros

- A opção Registros substitui Atividade Pedagógica e apresenta álbuns por turma/data, independentes dos planejamentos.
- Cadastro publicado ao salvar: título de até 200 caracteres, data civil entre 1900 e 9998, uma a vinte fotos. A primeira foto é a capa. A ordem de envio é preservada.
- ADMIN/MANAGER gerenciam todos os álbuns. Solicitantes ativos alocados consultam todos da turma; somente a autora atualmente alocada pode editar/excluir. Turmas arquivadas permitem apenas consulta. Autoria histórica é preservada.
- Remoção de alocação, desativação ou perda de perfil revoga também o acesso às URLs dos originais e miniaturas. Respostas de imagens usam no-store.
- JPG, PNG e WebP exigem assinatura e decodificação válidas, até 60 megapixels. Limite individual reutiliza as configurações existentes; cada envio permite até 100 MiB. Miniaturas JPEG de até 640 pixels respeitam orientação EXIF; originais são preservados.
- Listagem paginada em 24 itens, data da proposta decrescente e desempate por criação/UUID. Filtros por ano e mês; mês exige ano.
- APIs multipart enviam metadata como JSON e files como partes repetidas. retainedPhotoIds indica fotos existentes a manter. Alterações/exclusões exigem versão; conflitos retornam 409 sem sobrescrever dados.
- As alterações são publicadas atomicamente. Arquivos novos são registrados em fila durável antes da escrita; após uma hora o coletor remove somente arquivos sem referência. Exclusões entram na fila na mesma transação e tornam-se elegíveis imediatamente. O coletor roda a cada minuto, em lotes de 100; falhas de disco são repetidas após cinco minutos.
- Migração 009 é aditiva e portável. Auditoria registra identificação e tipo da operação sem título nem fotos.
- Galeria responsiva com carregamento tardio; ampliação preserva proporção, permite teclado e devolve foco à miniatura. Salvamento explícito e aviso de saída; falhas preservam dados locais.
