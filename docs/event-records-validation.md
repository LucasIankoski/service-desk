# Validação de Registros gerais e Compromissos

## Cenários

1. Entrar com REQUESTER ativo e acessar Registros pelo menu desktop e móvel. Criar álbum com título, data e fotos, sem turma nem evento da Agenda; consultar por ano/mês e navegar na galeria pelo teclado.
2. Criar/editar um álbum geral até atingir 100 fotos; tentar manter 100 e acrescentar uma. A tentativa deve ser rejeitada e o álbum salvo deve permanecer íntegro. Repetir os limites de 50/51 nos registros por turma.
3. Enviar 100 fotos em uma requisição HTTP real para cobrir também o limite multipart do Tomcat. Conferir manutenção de 100 fotos e rejeição da 101ª em edição.
4. Consultar um álbum geral com outra REQUESTER: não pode alterar/excluir. ADMIN/MANAGER podem. AGENT, conta desativada ou conta que perdeu o perfil não podem acessar nem as URLs de imagens.
5. Usar IDs de registros por turma na API geral e o inverso, incluindo fotos e alterações: deve retornar 404. Cada listagem contém somente seu contexto.
6. Simular falha de envio e conflito de versão: preservar título, data e arquivos; revisar o conteúdo remoto antes de salvar novamente. Confirmar exclusão e retorno à listagem correta.
7. Em Tarefas, conferir Compromissos acima de Ocorrências em desktop, tablet e celular. Criar as duas categorias na mesma data, editar e excluir independentemente, inclusive sem tarefas.
8. Usar um ID de ocorrência na API de compromissos e o inverso: 404. REQUESTER, AGENT e ADMIN sem MANAGER não acessam os compromissos. Após perda de perfil/inativação, a API deve recusar novas operações.
9. Aplicar as migrações sobre os dados anteriores: vínculo e fotos dos registros por turma devem ser preservados; as anotações antigas devem continuar sendo ocorrências. Reaplicar não pode reclassificar compromissos.

## Automação

- `GeneralRecordTests`: autorização, autoria, filtros, arquivos, limites 100/101 e 50/51, perfis atuais e isolamento HTTP entre contextos.
- `ActivityRecordUploadTests`: multipart real com 50 fotos por turma e 100 fotos gerais, login, CSRF e edição.
- `AgendaCommitmentTests`: categorias independentes, CRUD, versão, validação HTTP, CSRF, perfis e conta ativa. Contexto de teste separado evita compartilhar o repositório CSRF de MockMvc com testes de cookies reais.
- `EventFeaturesMigrationTests`: atualização de dados anteriores às migrações 013/014 e reaplicação.
- `general-records.spec.ts`, `records.spec.ts`, `tasks.spec.ts`: fluxos de navegador, limites, falhas, conflitos, confirmação de exclusão, acessibilidade e disposição dos painéis. APIs simuladas; autorização real é exercitada pelos testes Java.
- Executar `mvn verify`, `npm run test`, `npm run build` e os cenários dos três navegadores configurados. A matriz de bancos usa `mvn -Pdatabase-compatibility verify` com bancos descartáveis.

## Resultado local — 25/10/2026

- Backend: 86 testes aprovados com JDK 25 e `mvn -o verify`, incluindo upload multipart real de 100 fotos. O modo offline não gera o SBOM.
- PostgreSQL 18 descartável: seis testes aprovados em `GeneralRecordDatabaseIT` e `AgendaCommitmentDatabaseIT`, incluindo aplicação do changelog completo, autorização, CRUD, isolamento e limites de fotos. O banco temporário foi removido após a execução.
- Frontend: build e 24 testes unitários aprovados. Os cinco arquivos de navegador cobriram 135 cenários únicos em Chromium desktop, WebKit celular e Firefox tablet, incluindo regressão de Planejamentos e Projetos. Um fluxo de filtro no WebKit foi revalidado após sincronizar a navegação de ano antes da escolha do mês.
- Capturas da galeria geral e dos painéis empilhados foram geradas; a galeria móvel foi inspecionada. As verificações de acessibilidade automatizadas passaram.
- OpenAPI e migrações YAML validados, com referências internas resolvidas e sem chaves duplicadas.
- O Testcontainers ficou aguardando a conexão/inicialização de containers no Docker Desktop. A validação PostgreSQL foi concluída com um container temporário iniciado pela CLI, com porta de teste limitada a localhost e a variável `EVENT_TEST_JDBC_URL`. Os testes IT só executam quando essa variável está definida e usam exclusivamente banco descartável.
- Docker Desktop foi recuperado após isolar, em backups reversíveis, os sockets vazios inválidos `docker-secrets-engine/engine.sock` e `Docker/run/dockerInference`; não houve remoção de configurações, segredos ou volumes.
- Backup de banco e anexos, com a API parada antes da migração: `output/backups/before-event-features-20261025-165412/`. Imagens locais reconstruídas a partir da branch atual.
- Aplicação publicada em `https://localhost:8443/`: frontend, API e PostgreSQL saudáveis; página inicial HTTP 200 e readiness `UP`. Migrações 013/014 confirmadas como `EXECUTED` no banco local, com `class_id` opcional e `kind_name` obrigatório/padrão `OCCURRENCE`.
- A matriz completa MySQL/Oracle/SQL Server permanece uma validação de release; esta sessão validou H2 e PostgreSQL.

## Colaborador no compromisso

- Criar compromisso escolhendo Pamela Vidaletti em Colaborador e anotando a ausência. Conferir nome no cartão e seleção mantida na edição e em conflitos.
- Conferir usuários de todos os perfis, identificação de inativos, erro/repetição da consulta e bloqueio de salvar sem colaborador. O endpoint de opções exige MANAGER ativo e retorna apenas ID, nome e estado ativo.
- Preservar compromissos anteriores sem vínculo, exigindo seleção na próxima edição. Ocorrências continuam com apenas a anotação.
- Testes: AgendaCommitmentTests, AgendaOccurrenceTests, EventFeaturesMigrationTests e cenários de Tarefas em desktop/celular.

Regressão de perfis legados: cadastrar em banco de teste um usuário com papel `GUARDIAN` fora do enum atual. A consulta de colaboradores, a seleção desse usuário, a criação, a listagem e a edição de compromissos devem funcionar. As consultas de opções, validação de cadastro e nomes usam projeções escalares e não carregam perfis ou senhas. Os perfis existentes não são modificados.
