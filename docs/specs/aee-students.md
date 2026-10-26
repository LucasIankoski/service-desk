# Atendimento Educacional Especial por turma e aluno

A turma com `aee=true` é a entrada para AEE → turma regular → aluno → Planejamento Pedagógico, Projetos, Registros e Documentos. O cadastro administrativo de turma oferece essa opção na criação e edição. Turmas existentes recebem `false` na migração 015. Omitir o campo na edição preserva seu valor.

## Acesso e ciclo de vida

Somente contas ativas ADMIN, MANAGER e REQUESTER acessam o Pedagógico. ADMIN/MANAGER e REQUESTER atualmente alocadas na AEE ou na turma regular selecionada podem cadastrar, renomear, arquivar e reativar alunos e produzir seus conteúdos. A consulta é permitida a essas editoras ou pela visibilidade compartilhada da AEE. A visibilidade da turma regular não substitui a visibilidade da AEE.

Professoras com alocação regular encontram as entradas AEE no diretório do Pedagógico. Todas as turmas regulares ativas aparecem dentro da AEE; o filtro inclui arquivadas. Outras AEEs são excluídas. Cartões sem autorização mostram acesso restrito e não fazem consulta de alunos. O acesso à entrada não concede acesso aos documentos anteriores da AEE.

O aluno tem UUID, nome completo de até 160 caracteres, estado ativo/arquivado e versão. Homônimos são aceitos e distinguidos pelo identificador. Não há exclusão ou transferência automática. Cada cadastro pertence exclusivamente ao par AEE/turma regular. O mesmo nome em outra AEE representa um cadastro independente.

Arquivar a AEE, a turma regular ou o aluno bloqueia alterações dos conteúdos, mantendo consulta autorizada. Reativação do aluno exige versão atual e pais ativos. Quando o próprio cadastro está arquivado, deve ser reativado antes de outras alterações. Desmarcar uma AEE com alunos ou converter uma turma regular referenciada por alunos é rejeitado com 422, inclusive para vínculos arquivados.

## Conteúdo individual

Os serviços, formulários, filtros, impressão, finalização/reabertura e conflitos existentes são reutilizados com um escopo explícito de aluno. Não há escopo inferido a partir da URL antiga ou do documento. A API confere AEE/turma/aluno/documento; IDs de outro contexto retornam 404, incluindo fotos e PDFs. Listagens de turma e de registros gerais excluem conteúdos individuais.

Planejamentos e projetos são compartilhados entre editoras autorizadas; sua exclusão permanece administrativa. Registros continuam alteráveis somente pela autora ou administração, desde que também possuam escrita no contexto ativo. O total do álbum individual é de 1 a 50 fotos, considerando mantidas e novas. Registros gerais continuam com 100 fotos.

O planejamento usa `scope_type`, `scope_id` e `week_start` com unicidade composta e campos não nulos. Documentos antigos recebem `CLASS` e o ID da turma; novos individuais usam `AEE_STUDENT` e o UUID do aluno. Assim turma e alunos diferentes podem planejar a mesma semana. A identificação AEE/turma/aluno acompanha a impressão dos documentos individuais.


## PDFs privados

`/api/v1/pedagogical/aee/{aeeId}/classes/{classId}/students/{studentId}/documents` permite listagem e envio multipart de um campo `file` por operação. O recurso `/{id}` permite leitura, download com `download=true` e exclusão com `version`. A listagem informa arquivo, tamanho, autora, data, versão e permissões.

O servidor valida nome/extensão `.pdf`, assinatura `%PDF-`, MIME detectado e tamanho pelo limite configurado de anexos (1 a 25 MiB). A autora e ADMIN/MANAGER podem excluir com versão atual e contexto ativo. Toda leitura revalida autorização e retorna `Cache-Control: no-store`, `Vary: Cookie` e `nosniff`. O armazenamento é privado e a mesma fila durável de limpeza das fotos protege arquivos em exclusões, falhas de transação e reinício do processo; arquivos ainda referenciados são preservados.

PDF.js 6.4.299, worker, CMaps, fontes e decodificadores são servidos localmente. O visualizador oferece páginas anterior/próxima, contagem, zoom, ajuste à largura e camada de texto. A senha permanece apenas no estado transitório do navegador e é apagada após envio/fechamento. Erros de carregamento e arquivos inválidos são apresentados sem registrar conteúdo. CSP permite worker local, fontes PDF blob/data e compilação WebAssembly; `object-src 'none'` e `frame-ancestors 'none'` permanecem.

## Rotas e auditoria

Interface: `/pedagogico/turmas/:aeeId/turmas/:classId/alunos`, seguido de `/:studentId` e suas áreas. API: `/api/v1/pedagogical/aee/{aeeId}/classes/{classId}/students`, seguido de `/{studentId}` e recursos `plans`, `projects`, `records`, `documents`. Os contratos detalhados estão no OpenAPI.

Todas as mutações exigem sessão e CSRF. Versões obsoletas retornam 409. Auditoria guarda ator, ação, tipo e identificador, sem nomes de alunos, textos pedagógicos, nomes/conteúdo de PDFs ou senhas.
