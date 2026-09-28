# Registros de atividades — validação

## Entrega

Menu Registros dentro da turma, álbuns por data, cadastro com título/data/fotos, edição pela autora ou administração, exclusão confirmada e galeria com ampliação. A API controla acesso também aos arquivos e miniaturas. Migração aditiva 009; volumes existentes preservados.

## Resultados em 27/09/2026

| Verificação | Resultado |
| --- | --- |
| Backend Java 25, Maven clean verify | 59 testes passaram |
| Frontend Vitest | 19 testes passaram |
| Build TypeScript/Vite | Passou |
| E2E Chromium e WebKit | 62 cenários passaram, incluindo 8 de Registros |
| PostgreSQL 18 | Changelog completo e 5 testes de Registros passaram |
| MySQL 8.4 | Changelog completo e 5 testes de Registros passaram |
| Oracle XE 21 | Changelog completo e 5 testes de Registros passaram |
| SQL Server 2022 | Migrações 008/009 sobre a base isolada e 5 testes passaram |
| OpenAPI | YAML e referências locais validados |

Os testes backend verificam autorização, autoria, arquivamento, remoção de alocação, desativação/perda de perfil, URLs privadas, CSRF, versão, filtros, validação de imagens, atomicidade de lotes inválidos e exclusão. Os testes de imagens verificam WebP real, orientação EXIF e repetição da limpeza após falha de disco. O teste de limpeza usa vencimento explícito para não depender da precisão de timestamps de cada banco.

Os testes E2E usam APIs simuladas; os contratos e persistência reais são exercitados pelos testes de integração Java. No WebKit, a interceptação Playwright omite bytes de partes Blob; o mock obtém os metadados do formulário nesse caso. O contrato multipart é verificado separadamente pelo teste da API frontend e pelo MockMvc no backend.

## Limitações do ambiente

- Firefox/Playwright não inicia: `browserType.launch: spawn UNKNOWN`, antes de acessar a aplicação. Uma tentativa foi realizada nesta entrega; os demais cenários Firefox não rodaram.
- A instalação completa em SQL Server vazio continua bloqueada pelo problema preexistente de caminhos de cascata da migração 001 (`fk_attach_comment`). A migração nova e o comportamento de Registros foram validados sobre a fixture de compatibilidade.

## Reproduzir a matriz

`ActivityRecordDatabaseIT` herda os mesmos cinco testes de serviço/HTTP usados em H2. Use somente bancos descartáveis. Defina `PED_TEST_JDBC_URL`, `PED_TEST_JDBC_USER`, `PED_TEST_JDBC_PASSWORD` e `PED_TEST_JDBC_DRIVER`; execute `./mvnw -Dtest=ActivityRecordDatabaseIT test`. Para SQL Server, acrescente `PED_TEST_SCHEMA=isolated`.

Evidências locais: `output/records-qa/*-summary.txt`, logs no mesmo diretório e relatórios Maven em `backend/target/surefire-reports`. Os containers e a rede temporários da matriz foram removidos.

## Armazenamento e recuperação

Os originais e miniaturas ficam no volume de anexos, sob `pedagogical/`. Arquivos em operações desfeitas ficam registrados na fila `ped_photo_cleanup`; após uma hora, o coletor só remove os que não possuem referência. Fotos excluídas entram na fila na transação da exclusão e são elegíveis imediatamente. A fila é processada a cada minuto; falhas de disco são repetidas após cinco minutos.

Backup antes da atualização local: `output/backups/pre-registros-20260927.dump`. A atualização local usa `docker compose --profile postgres up -d --build --wait --wait-timeout 240`.

## Atualização local concluída

Em 27/09/2026, API e frontend foram reconstruídos e atualizados em `https://localhost:8443`. A migração `009-pedagogical-records` consta como `EXECUTED`. A página pedagógica e o novo arquivo JavaScript responderam HTTP 200; a API de registros sem autenticação respondeu HTTP 401. O banco PostgreSQL e os volumes existentes foram mantidos.

Para validar: **Pedagógico → turma → Registros → Novo registro**. Informe título, data e fotos; salve, abra a galeria e amplie uma imagem. A autora ou a administração pode editar e excluir o registro.
