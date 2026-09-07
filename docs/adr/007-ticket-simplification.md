# Simplificação de solicitações e preservação de texto longo

Status: aceito.

A remoção do assunto exige preservar o conteúdo nas descrições CLOB/text dos quatro bancos suportados. A migration 007 usa concatenação SQL específica por fornecedor: `||` com `CHR` em PostgreSQL, H2 e Oracle; `CONCAT` com `CHAR` em MySQL; concatenação iniciada por `nvarchar(max)` em SQL Server para evitar truncamento em 8000 bytes. Não há SQL nativo novo no domínio da aplicação.

A atualização é feita antes da exclusão das colunas, uma única vez sob controle do Liquibase. A categoria permanece anulável no banco para registros legados; a API exige categoria ativa para novas solicitações. Os testes de migração verificam descrição longa, acentos, categoria nula e repetição da atualização em H2 e na matriz dos quatro fornecedores. Rollback requer backup, pois prioridade e prazo são removidos definitivamente.
