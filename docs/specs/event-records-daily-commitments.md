# Registros gerais de eventos e Compromissos

## Registros gerais

O item Registros aparece após Pedagógico no menu e na navegação móvel. As rotas `/registros` e `/registros/{id}` apresentam álbuns independentes com título e data, sem vínculo com turma ou evento da Agenda. Há filtros por ano/mês, paginação de 24 itens, capa na primeira foto, galeria com teclado, edição, confirmação de exclusão e revisão de conflitos sem perder o preenchimento local.

ADMIN, MANAGER e REQUESTER ativos consultam e criam. Somente a autora e ADMIN/MANAGER ativos alteram e excluem. AGENT sem um desses perfis não acessa. A API revalida os perfis atuais e a conta ativa; fotos e miniaturas privadas passam pela mesma autorização e retornam `Cache-Control: no-store`.

Cada álbum geral deve conter de 1 a 100 fotos, incluindo as mantidas e novas em uma edição. Registros por turma continuam limitados a 50. Os formatos são JPG, PNG e WebP; o limite individual vem das configurações existentes. São permitidos até 100 MiB de novos arquivos por envio e os limites de dimensão existentes permanecem. É possível completar 100 fotos em edições sucessivas. O Tomcat aceita 101 partes multipart (100 fotos e metadados); a aplicação aplica o limite de cada contexto.

`/api/v1/records` fornece GET com `year`, `month`, `page` e POST multipart com `metadata`/`files`. `/{id}` fornece GET, PUT multipart e DELETE com `version`; `/{id}/photos/{photoId}` e `/thumbnail` fornecem as imagens. A página retorna `canCreate`, `maxPhotos=100` e `attachmentLimitMb`; o detalhe retorna `canEdit` e os limites, sem campos de turma. Alterações e exclusões exigem versão atual e retornam 409 em conflito. Metadados inválidos retornam 400; regras de quantidade/conteúdo inválido retornam 422; limites de bytes retornam 413.

A migration 013 permite `ped_record.class_id` nulo para álbuns gerais e mantém os valores e as fotos dos álbuns existentes. Os dois contextos compartilham processamento, armazenamento e limpeza durável, mas são isolados nas consultas. IDs do outro contexto retornam 404 em consulta, edição, exclusão, fotos e miniaturas. Eventos de auditoria `RECORD_CREATED`, `RECORD_UPDATED` e `RECORD_DELETED` não incluem conteúdo; os eventos pedagógicos existentes são preservados.

## Compromissos

Tarefas apresenta Compromissos acima de Ocorrências do dia, empilhados na mesma coluna. Em telas menores ambos aparecem antes da tabela de tarefas e podem ser recolhidos. Cada painel mantém seleção de data e estado de expansão independentes, no mês selecionado em Tarefas. A funcionalidade é de anotações por data, sem horário, responsável, status, lembretes ou ligação obrigatória com tarefas.

Somente MANAGER ativo consulta, cria, altera ou exclui as anotações, inclusive as criadas por outro MANAGER. A data é civil, o texto é obrigatório e possui até 4.000 caracteres; há suporte a várias anotações no mesmo dia. Editar/excluir exige versão atual e o formulário preserva o texto diante de conflito.

`/api/v1/agenda/commitments` usa GET com início inclusivo e fim exclusivo (`start`/`end`) e POST com `date`/`body`; `/{id}` usa PATCH com `date`/`body`/`version` e DELETE com parâmetro `version`. O contrato de ocorrências permanece em `/api/v1/agenda/occurrences`.

A migration 014 adiciona `agenda_occurrence.kind_name`: registros existentes e novos sem valor explícito recebem `OCCURRENCE`, enquanto compromissos usam `COMMITMENT`. Consultas e alterações são isoladas por categoria; usar um ID na API da categoria errada retorna 404. A auditoria usa `AGENDA_COMMITMENT_CREATED`, `AGENDA_COMMITMENT_UPDATED` e `AGENDA_COMMITMENT_DELETED`, sem texto da anotação. As chaves de cache e os identificadores acessíveis dos dois painéis são distintos.

## Validação

Ver o [roteiro e as evidências de validação](../event-records-validation.md). As duas funcionalidades são entregues na mesma branch do compartilhamento de turmas, preservando suas regras e contratos.

## Colaborador do compromisso

Cada compromisso novo ou editado exige selecionar um usuário cadastrado. O seletor lista todos os perfis, identifica inativos e exclui contas anonimizadas; retorna somente ID, nome e estado ativo. A autoria continua sendo a pessoa que cadastrou a anotação. O cartão exibe o colaborador e o texto, por exemplo “Pamela Vidaletti — vai ao médico e estará ausente”.

A migração 016 adiciona vínculo opcional com usuário, preservando compromissos anteriores e ocorrências. Compromissos antigos aparecem como “Colaborador não informado” e exigem seleção ao editar. A listagem e alterações continuam exclusivas de MANAGER ativo, com CSRF, versão, isolamento de categoria e auditoria sem texto ou nomes. Ocorrências não recebem seletor nem vínculo.
