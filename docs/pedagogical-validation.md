# Validação do módulo Pedagógico

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
