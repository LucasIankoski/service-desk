# Central de Serviços

Central de Serviços interna para abertura, triagem e acompanhamento de solicitações.

## Stack

- Java 25, Spring Boot 4.1 e Spring Modulith
- React 19, TypeScript e Vite 8
- PostgreSQL, MySQL, Oracle ou SQL Server selecionado por implantação
- Docker Compose, Caddy e ClamAV

## Estrutura

```text
backend/   API REST e domínio modular
frontend/  SPA responsiva
infra/     containers e proxy reverso
docs/      especificação, ADRs, segurança e operação
```

## Desenvolvimento rápido

Pré-requisitos: Docker 29+, Node 24 LTS e JDK 25. O Maven Wrapper e o Corepack mantêm as ferramentas reproduzíveis.

```powershell
New-Item -ItemType Directory -Force secrets
$bytes = [byte[]]::new(32)
[System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
[Convert]::ToBase64String($bytes) | Set-Content secrets/app_encryption_key -NoNewline
Copy-Item .env.example .env
docker compose --profile postgres up -d postgres clamav
cd backend
./mvnw spring-boot:run
cd ../frontend
npm install
npm run dev
```

Copie [`.env.example`](.env.example) para `.env` e troque todos os secrets antes de executar fora do ambiente local.

Para iniciar, reiniciar, parar e diagnosticar o ambiente completo com Docker Compose, siga o
[tutorial de inicialização manual](docs/start-local.md).

## Contas iniciais

Em banco vazio, defina `APP_BOOTSTRAP_ADMIN_EMAIL` e `APP_BOOTSTRAP_ADMIN_PASSWORD`. A senha deve ter ao menos 12 caracteres e será marcada para troca obrigatória. As variáveis são ignoradas depois que existir um administrador.

## Qualidade

```powershell
cd backend
./mvnw verify
cd ../frontend
npm run test
npm run build
npm run test:e2e
```

Contrato REST: [`docs/openapi/openapi.yaml`](docs/openapi/openapi.yaml).

Consulte [`docs/specs/service-desk-v1.md`](docs/specs/service-desk-v1.md), [`CODING_STANDARDS.md`](CODING_STANDARDS.md) e [`docs/operations.md`](docs/operations.md) antes de alterar comportamento público.
