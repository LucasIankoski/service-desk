# Operação

## Backup e restauração

O banco e o volume de anexos formam uma unidade lógica. Pause novas escritas, registre o instante, faça backup consistente do banco e depois copie o volume. Restaure ambos para o mesmo ponto e valide hashes de anexos antes de liberar tráfego.

## Atualização e rollback

1. Faça backup e execute `liquibase validate`.
2. Publique a imagem versionada e aguarde readiness.
3. Execute smoke tests de login, listagem e download.
4. Para rollback, use a imagem anterior somente quando a migration for retrocompatível; migrations destrutivas exigem restauração do backup.

## Execução com Compose

Crie `.env` a partir de `.env.example` e grave a master key em `secrets/app_encryption_key`.
O Compose principal expõe a aplicação em `https://localhost:8443` com certificado interno do Caddy.
Use `docker compose --profile postgres up --build` para um ambiente completo de teste.
Os perfis `mysql`, `sqlserver` e `oracle` existem para validação local; para Oracle, ajuste a imagem conforme a licença e o registry disponíveis na sua organização.

Para validar as migrations e o fluxo relacional minimo nos quatro bancos via Testcontainers, execute:

```bash
cd backend
./mvnw -Pdatabase-compatibility verify
```

Para validar um fornecedor por vez, informe `-Ddb.compatibility.vendor=postgresql`, `mysql`, `oracle` ou `sqlserver`.

Para MySQL, SQL Server ou Oracle, habilite o perfil correspondente e ajuste `DB_VENDOR`, `DB_URL`,
`DB_USERNAME` e `DB_PASSWORD`. Em produção, prefira banco gerenciado externo e mantenha backup do
banco sincronizado com o volume `attachments`. O perfil Oracle usa a imagem oficial do Oracle
Container Registry; faça login/aceite a licença no registry antes de usá-lo localmente.

## Secrets

Banco, bootstrap e master key entram por Docker Secrets ou variáveis protegidas. Para rotacionar a master key, mantenha a chave anterior disponível, regrave os campos protegidos com o novo identificador e só então remova a antiga.

## Incidente

Revogue sessões, desative a conta afetada, preserve logs/auditoria, troque secrets relacionados e valide integridade de banco e anexos. Nunca copie descrições ou arquivos para tickets externos de incidente.
