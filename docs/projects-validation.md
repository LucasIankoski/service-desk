# Validação de Projetos pedagógicos

## Preenchimento

1. Abrir Pedagógico, selecionar uma turma e entrar em Projetos.
2. Criar com título e período. Preencher faixa etária e responsáveis.
3. Preencher as quatro caixas de texto: Objetivo geral, Objetivos específicos (um por linha), Desenvolvimento e Conclusão.
4. Salvar rascunho, recarregar e conferir os textos. Não existem controles de blocos, formatação ou datas individuais de atividades.
5. Finalizar, visualizar/imprimir e reabrir. Verificar que o formulário fica bloqueado enquanto finalizado ou com turma arquivada.
6. Editar em duas sessões: a segunda gravação recebe conflito e mantém o texto local. Se outra pessoa finalizar, escolher manter o texto, reabrir e salvar sem perdê-lo.
7. Remanejar/desativar uma professora e conferir a revogação de acesso; responsáveis históricas continuam no registro.

## Impressão e histórico

- A referência é `PROJETO_ AMOR E GRAÇA NO PAIS DAS MARAVILHAS.pdf`, fornecida pelo usuário. Seu conteúdo aparece apenas na fixture de testes, sem criação automática de projetos.
- A fixture usa o logotipo extraído do anexo. Na aplicação, o logotipo vem das configurações existentes.
- A4 vertical, escala 100%, cabeçalhos e rodapés do navegador desativados. Logotipo apenas no início, título centralizado, corpo Arial 11 pt, objetivos com marcadores e conclusão.
- O PDF de referência tem duas páginas. Texto extenso deve continuar sem cortes e manter a conclusão visível. O Planejamento continua em A4 paisagem.
- Projetos antigos preservam atividades, datas, subtítulos e negritos. O formulário mostra o desenvolvimento completo em uma caixa de texto; sua edição mantém trechos formatados existentes quando possível e passa a salvar o desenvolvimento livre, sem exigir novas datas.

## Testes automatizados

- Backend Java 25: `./mvnw verify`. `ProjectTests` cobre ciclo de vida, autorização, remanejamento, arquivamento, conflitos, validação HTTP/CSRF, texto estruturado, ordenação histórica, meses intermediários e desenvolvimento sem data/título.
- Frontend: `npm test` e `npm run build`. Testes verificam períodos, preservação de formatação histórica e renderização segura.
- Navegadores: `npm run test:e2e -- projects.spec.ts pedagogical.spec.ts --project=chromium-desktop --project=webkit-mobile --workers=2`.
- `projects.spec.ts` cobre formulário simples, ausência dos controles antigos, recarga, conflito, finalização/reabertura, acessibilidade, celular e impressão. Os PDFs são gerados no diretório de resultados do Playwright.

## Bancos e migrações

- Migração 010 cria as tabelas; 011 torna opcional a data interna do desenvolvimento, sem apagar registros anteriores.
- Usar bancos descartáveis, vazios e isolados. Nunca executar a matriz contra bancos de uso da aplicação.
- Configurar `PED_TEST_JDBC_URL`, `PED_TEST_JDBC_USER`, `PED_TEST_JDBC_PASSWORD` e `PED_TEST_JDBC_DRIVER`; executar `./mvnw -Dtest=ProjectDatabaseIT test`.
- PostgreSQL, MySQL e Oracle aplicam o changelog completo. Para SQL Server, definir também `PED_TEST_SCHEMA=isolated`, usando a base de testes anterior ao Pedagógico e aplicando as migrações 008 a 011. A instalação completa continua sujeita ao problema antigo de cascatas da migração 001, documentado em `pedagogical-validation.md`.

## Resultados anteriores — formulário simplificado

- Backend: `verify` aprovado em Java 25, com 67 testes.
- Bancos: cinco testes de Projetos aprovados em cada um dos quatro bancos (PostgreSQL 18, MySQL 8.4, Oracle XE 21 e SQL Server 2022). SQL Server validado sobre a base isolada descrita acima; migração 011 e desenvolvimento sem data incluídos.
- Frontend: build aprovado; 24 testes unitários aprovados.
- E2E: 20 cenários aprovados em Chromium desktop e WebKit mobile, incluindo regressão do Planejamento.
- Firefox: bloqueado antes de abrir a aplicação por `browserType.launch: spawn UNKNOWN` no ambiente Windows.
- OpenAPI: YAML carregado sem chaves duplicadas e referências locais resolvidas.
- PDF: referência inspecionada em duas páginas A4 verticais; desenvolvimento extenso em três páginas, com conclusão e texto completos. Planejamento validado em A4 paisagem, com uma página normal e três no cenário extenso.

## Exclusão administrativa — roteiro

1. Com MANAGER ou ADMIN, abrir um Planejamento ou Projeto de turma ativa, clicar em Excluir e cancelar. O documento e o preenchimento local devem permanecer.
2. Confirmar a exclusão de um rascunho com alterações não salvas: o aviso deve explicar o descarte; após sucesso, voltar à listagem sem uma segunda confirmação de saída.
3. Excluir um documento finalizado sem reabrir. A turma e os demais documentos devem continuar disponíveis. Planejamentos excluídos liberam a semana para novo cadastro.
4. Como professora alocada, conferir a ausência do botão e a resposta 403 ao DELETE direto. Repetir com usuário inativo ou com perfil administrativo removido.
5. Arquivar a turma e conferir que a exclusão fica indisponível; o backend deve recusar a operação até a reativação.
6. Abrir duas sessões, alterar o documento em uma e tentar excluí-lo na outra. Esperado: 409, texto local preservado, revisão da versão atual antes de nova tentativa.
7. Conferir exigência de versão e CSRF, resposta 204 no sucesso e 404 após a remoção. Auditar somente autor, ação e identificador, sem conteúdo pedagógico.
8. Executar `ProjectTests` e `PedagogicalTests`; para bancos reais, executar `./mvnw -Dtest=ProjectDatabaseIT,PedagogicalDatabaseIT test` com as configurações da matriz acima.

## Resultados da exclusão — 29/09/2026

- Backend Java 25: `verify` aprovado, 71 testes sem falhas.
- Frontend: build aprovado e 24 testes unitários aprovados.
- E2E: 32 cenários aprovados em Chromium desktop e WebKit mobile, incluindo confirmação/cancelamento da exclusão, descarte local após sucesso, conflitos, permissões e regressão da impressão.
- OpenAPI: YAML sem chaves duplicadas e referências locais resolvidas.
- Matriz de bancos: 12 testes por banco (7 de Projetos e 5 de Planejamento) aprovados em PostgreSQL 18, MySQL 8.4, Oracle XE 21 e SQL Server 2022. SQL Server usa a base isolada descrita acima. A exclusão não exigiu migração adicional.
