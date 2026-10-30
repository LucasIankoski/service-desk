# Validação do módulo Pedagógico

Para turmas marcadas como Atendimento Educacional Especializado, seguir também [a validação por turma e aluno](aee-validation.md). Os cenários de compartilhamento abaixo usam turmas regulares.

## Turmas compartilhadas

1. Criar turma regular com duas professoras e visibilidade Compartilhada com o Pedagógico; verificar persistência ao reabrir o cadastro.
2. Com uma terceira solicitante ativa e sem alocação, localizar a turma com indicador de somente consulta e abrir Planejamentos, Projetos e Registros, inclusive rascunhos, fotos e impressão.
3. Verificar ausência das ações de criar, salvar, finalizar, reabrir, excluir e gerenciar para a leitora. Repetir as tentativas diretamente pela API, esperando 403.
4. Remover a alocação da autora de um registro compartilhado: ela mantém consulta, mas perde edição/exclusão e recebe `canEdit=false`.
5. Tornar a turma restrita e confirmar a revogação na próxima consulta à API, inclusive por URL direta de documento, foto e miniatura. Contas inativas ou sem perfil Pedagógico também não consultam.
6. Arquivar a turma compartilhada: todos os usuários habilitados mantêm consulta; nenhuma pessoa altera conteúdo até reativar. ADMIN/MANAGER continuam podendo reativar.
7. Verificar turma compartilhada sem alocações na listagem; turma compartilhada e alocada aparece uma única vez. Turmas existentes continuam restritas após a migration 012.

Automação: `SharedClassTests` cobre autorização HTTP, listagem, compatibilidade do contrato, versões e revogação; os testes E2E de Pedagógico, Projetos e Registros cobrem configuração e consulta sem ações de escrita.

### Resultado da alteração de visibilidade — 24/10/2026

- Backend: `mvn -o verify` com JDK 25, 78 testes aprovados; inclui aplicação do changelog completo e teste de atualização de turmas existentes em H2. O Maven em modo offline não gera o SBOM.
- Frontend: build aprovado e 24 testes unitários aprovados.
- Navegador: 75 cenários aprovados nos arquivos `pedagogical.spec.ts`, `projects.spec.ts` e `records.spec.ts`, distribuídos entre Chromium desktop, Firefox tablet e WebKit celular. As APIs são simuladas nesses cenários; a autorização real é exercitada pelos testes HTTP Java.
- A prévia com rolagem recebe foco pelo teclado e passou pela verificação de acessibilidade também no WebKit. Capturas do cadastro compartilhado e da consulta foram inspecionadas.
- No Windows, o teste de exclusão de fotos fecha os streams antes de validar a limpeza e processa todos os lotes pendentes, evitando bloqueio dos arquivos abertos pelo próprio teste.
- O Firefox foi executado com `PLAYWRIGHT_BROWSERS_PATH` apontando para `output/tooling/browsers`, pois a instalação do usuário falhou ao iniciar.
- Matriz PostgreSQL/MySQL/Oracle/SQL Server não executada nesta sessão: daemon Docker indisponível. Executar antes da release, conforme `docs/operations.md`.

## Execução

- Backend: Java 25, ./mvnw verify.
- Frontend: npm test, npm run build, npm run test:e2e.
- PedagogicalTests cobre autorização, remanejamento, arquivamento, conflitos, histórico, finalização e semanas entre meses/anos.
- pedagogical.spec.ts cobre navegação, editor, conflito, acessibilidade e impressão com APIs simuladas. Contratos reais são cobertos pelos testes Java.

## Bancos isolados sem socket Docker

PedagogicalDatabaseIT herda os testes do módulo e aplica o changelog completo no banco fornecido. Use somente banco descartável e vazio; nunca produção.

Defina PED_TEST_JDBC_URL, PED_TEST_JDBC_USER, PED_TEST_JDBC_PASSWORD e PED_TEST_JDBC_DRIVER; execute ./mvnw -Dtest=PedagogicalDatabaseIT test.

Drivers:

- PostgreSQL: org.postgresql.Driver
- MySQL: com.mysql.cj.jdbc.Driver
- Oracle: oracle.jdbc.OracleDriver
- SQL Server: com.microsoft.sqlserver.jdbc.SQLServerDriver

Para validar somente o upgrade do módulo, defina também PED_TEST_SCHEMA=isolated. A fixture reutiliza as tabelas de identidade, configurações, auditoria e sessão anteriores ao módulo e aplica a migração 008 sem modificações. Esse caminho é necessário no SQL Server porque a migração inicial 001 já contém caminhos de cascata incompatíveis em fk_attach_comment. A instalação completa em banco SQL Server vazio continua pendente de correção dessa migração antiga.

Os testes não precisam acessar o daemon Docker. Bancos podem iniciar em rede isolada, sem publicar portas nem compartilhar volumes de dados.

## Conferência visual

1. Cadastrar turma BII e alocar duas solicitantes.
2. Criar semana de 28/09/2026 e conferir presença em setembro e outubro.
3. Preencher tema e um dia completo; marcar os demais como Sem aula com motivo.
4. Salvar, finalizar, imprimir e reabrir com outra professora.
5. Repetir impressão com texto extenso e conferir continuação em várias páginas.
6. Alterar o logotipo em Administrador / Tema, conferir o cabeçalho e remover.
7. Remanejar uma professora e verificar perda de acesso à turma anterior.

## Persistência de instantes

A configuração Hibernate preferred_instant_jdbc_type usa TIMESTAMP, em conjunto com jdbc.time_zone UTC. Isso corresponde às colunas timestamp sem fuso dos changelogs e evita ORA-18716 ao ler usuários e auditoria no Oracle. Datas pedagógicas continuam LocalDate/DATE, sem conversão de fuso.

## Resultados — 27/09/2026

- Maven verify em Java 25: 51 testes passaram.
- Vitest: 17 testes passaram; build TypeScript/Vite passou.
- E2E: 54 cenários passaram em Chromium/WebKit (52 na suíte geral e 2 adicionais de impressão longa); os 8 cenários finais do módulo também passaram.
- PostgreSQL 18 e MySQL 8.4: changelog completo e 3 testes de serviço/HTTP passaram.
- Oracle XE 21: changelog completo e 3 testes passaram após o ajuste de TIMESTAMP/UTC.
- SQL Server 2022: migração 008 e 3 testes passaram sobre a base isolada; instalação inicial completa bloqueada pelo problema antigo de cascatas da migração 001.
- PDF A4 paisagem inspecionado visualmente em três páginas, com cabeçalho da tabela repetido e conteúdo completo.
- Firefox/Playwright: falha de inicialização do executável (spawn UNKNOWN), inclusive após reinstalação. Os cenários não chegaram à aplicação; validação nesse navegador permanece pendente.

## Exclusão administrativa — 29/09/2026

- MANAGER e ADMIN podem excluir planejamentos em rascunho ou finalizados de turmas ativas, após confirmação. Professoras não podem excluir.
- A operação exige versão atual e CSRF; turmas arquivadas continuam somente para consulta. Confirmação, conflitos e texto local seguem o [roteiro compartilhado de exclusão](projects-validation.md#exclusão-administrativa--roteiro).
- `PedagogicalTests` inclui exclusão, auditoria sem conteúdo, recriação da mesma semana, contas inativas, perda de perfil administrativo e autorização HTTP.
- Validação desta entrega: backend `verify` com 71 testes, 24 testes unitários do frontend, build e 32 cenários E2E de Projetos/Planejamento em Chromium e WebKit aprovados.
- Matriz de exclusão: 12 testes por banco aprovados em PostgreSQL, MySQL, Oracle e SQL Server (base isolada).
