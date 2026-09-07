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

## Anexos no MVP

O MVP não inicia nem utiliza ClamAV. Novos anexos são registrados como NOT_SCANNED, sem garantia de ausência de malware. As validações de arquivos e regras de acesso permanecem ativas.

Ao atualizar uma instalação anterior, use docker compose --profile postgres up -d --build --remove-orphans para remover o container antigo. Variáveis CLAMAV_* antigas não têm efeito e podem ser removidas do ambiente. O volume antigo de assinaturas pode permanecer sem afetar a aplicação.

Status históricos são preservados: CLEAN em versões anteriores não comprova varredura, pois também era gravado quando o scanner estava desativado.

## Atualização: simplificação das solicitações

Publique frontend e backend juntos: a API de solicitações deixa de aceitar assunto, prioridade e vencimento no contrato e passa a exigir categoria ativa na abertura. Classificação altera somente categoria; as rotas de prioridade e prazo deixam de existir.

Interrompa escritas e faça backup antes da migration 007. Ela preserva o assunto como `Assunto anterior: {assunto}`, seguido de uma linha em branco e da descrição integral, inclusive acima de 8000 caracteres. Depois remove os campos antigos, os controles de alerta e a configuração de antecedência. Registros antigos sem categoria permanecem sem classificação; notificações e auditorias históricas são preservadas. A migração não altera a janela de reabertura nem Agenda/Tarefas.

A migration contém SQL específico por fornecedor para concatenar texto longo sem truncamento. Valide a atualização nos quatro bancos com `./mvnw -Pdatabase-compatibility verify`. A reversão desta migration destrutiva exige restauração do backup e publicação conjunta das versões anteriores.

No Docker 29, o Testcontainers 1.21 pode exigir `-Dapi.version=1.44` na execução Maven. A instalação inicial em SQL Server tem um bloqueio anterior à migration 007: a constraint `fk_attach_comment` da migration 001 cria múltiplos caminhos de exclusão em cascata. O teste `existingTicketUpgradeWorksOnSqlServer` valida separadamente a migration 007 sobre as tabelas legadas envolvidas; ele não substitui a validação da instalação completa.
