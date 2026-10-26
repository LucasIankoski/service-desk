# Validação do Atendimento Educacional Especial

## Matriz funcional

1. Criar turma comum e AEE; conferir opção desmarcada por padrão e edição preservando valor quando omitido. Converter turma antiga com documentos e conferir consulta/impressão sem edição.
2. Acessar com professora da AEE, professora da turma regular, ambas, colega sem alocação, ADMIN, MANAGER, AGENT e conta inativa. Alternar compartilhamento da AEE e revogar alocação; repetir URLs diretas de documentos, fotos, miniaturas e PDFs.
3. Na AEE restrita, conferir descoberta pela professora regular, cartões restritos sem links e ausência de nomes/conteúdos dos alunos. Filtrar turmas arquivadas e excluir outras AEEs do diretório.
4. Cadastrar homônimos, renomear com conflito, arquivar e reativar. Conferir que outra AEE e outra turma não recebem os mesmos cadastros. Tentar usar IDs cruzados em GET/PUT/DELETE e fotos: 404.
5. Arquivar cada nível isoladamente. Conferir consulta permitida e bloqueio de criação/edição/finalização/reabertura/exclusão. Impedir mudanças do tipo da turma quando houver vínculos, inclusive arquivados.
6. Criar planejamento da turma e de dois alunos na mesma segunda-feira. Conferir independência, duplicidade no mesmo aluno rejeitada e impressão com AEE/turma/aluno. Preservar estados, professoras históricas e conflitos dos formulários.
7. Editar projetos entre professoras das duas alocações; excluir apenas por administração. Registros: autora/administração, 50 fotos aceitas e 51 rejeitadas, inclusive mantidas + novas. Listas antigas e gerais não exibem conteúdos individuais.
8. Enviar PDF válido, extensão inválida, assinatura falsa, arquivo vazio e acima do limite. Testar autoria, versão, confirmação de exclusão e limpeza durável após falha de persistência. Leitura autenticada deve usar `no-store`.
9. Em Chromium desktop, Firefox tablet e WebKit celular: abrir fixture com duas páginas, texto e imagem, navegar, aplicar zoom/ajuste à largura, selecionar texto, consultar PDF protegido com senha incorreta/correta, fechar e abrir arquivo inválido. Conferir nomes acessíveis, foco, axe e ausência de rolagem horizontal da página.

## Automação

- `AeeTests`: autorização, descoberta, homônimos, isolamento, alocação vigente, arquivos diretos, arquivamento, autoria e conflitos.
- `AeeMigrationTests`: atualização da estrutura antiga, preservação do texto, escopo não nulo e unicidade independente.
- `AeeDatabaseIT`: mesmos cenários contra PostgreSQL descartável fornecido por `EVENT_TEST_JDBC_URL`, sem acessar dados da aplicação.
- `frontend/e2e/aee.spec.ts`: cadastro, diretório, CRUD de alunos, reutilização das áreas, visualização real de PDFs, upload/exclusão, senha e acessibilidade nos três projetos Playwright.
- Regressão: testes Java completos, frontend, build e cenários existentes de Pedagógico/Projetos/Registros/Tarefas.

## Atualização local

Parar a API antes do backup. Salvar `pg_dump -Fc` e cópia integral dos anexos do volume privado na mesma pasta `output/backups/before-aee-*`. Conferir tamanho e integridade antes de reconstruir/substituir containers. Aplicar migração 015, conferir readiness e saúde dos três serviços e navegar pela aplicação. O backup deve ficar preservado para recuperação.

Os testes de navegador usam dados sintéticos e PDFs sem dados pessoais. A integração PostgreSQL usa banco descartável. A matriz de MySQL, SQL Server e Oracle continua obrigatória antes de release nesses bancos, conforme o roteiro operacional existente.

## Evidência local — 25/10/2026

- Verificação Java completa: 96 testes aprovados. Após ajuste final de isolamento em exclusões, 21 testes de AEE/planejamentos/projetos novamente aprovados.
- PostgreSQL 18 descartável: 15 testes aprovados, incluindo os nove cenários de AEE e regressão de registros gerais/compromissos. Container encerrado após a validação.
- Frontend: 24 testes aprovados e build de produção concluído.
- Navegadores: 111 cenários distintos aprovados entre Chromium desktop, WebKit celular e Firefox tablet, considerando reexecução dos dois cenários móveis com sincronização de navegação corrigida. Os sete cenários AEE passaram nos três navegadores.
- Bundle real dos containers: opção AEE, PDF com duas páginas, texto, worker local e CSP confirmados com contexto sintético, sem criar alunos locais. A tentativa de login com credencial de bootstrap retornou 401; a navegação autenticada real foi validada nos testes de integração, e o smoke do bundle utilizou sessão simulada no navegador.
- Migração `015-aee-students` executada; API, frontend e PostgreSQL saudáveis. Backups consistentes preservados em `output/backups/before-aee-20261025-191211` e `output/backups/before-aee-final-20261025-192500`, incluindo banco, anexos e manifesto SHA-256 no backup final.
