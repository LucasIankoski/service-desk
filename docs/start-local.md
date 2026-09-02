# Iniciando a aplicação manualmente

Este tutorial inicia o ambiente completo com Docker Compose: frontend, API, PostgreSQL e ClamAV.
A aplicação fica disponível em `https://localhost:8443`.

## Pré-requisitos

- Docker Engine 29 ou superior (ou uma versão atual do Docker Desktop), com o mecanismo Linux em execução.
- PowerShell aberto na raiz do repositório.

Confira o Docker antes de continuar:

```powershell
docker version
docker compose version
```

## Primeira inicialização

1. Entre na pasta do projeto:

```powershell
cd D:\Work\Pessoal\Projetos\service-desk
```

2. Crie o arquivo de configuração somente se ele ainda não existir:

```powershell
if (-not (Test-Path .env)) {
    Copy-Item .env.example .env
}
```

Abra `.env` e troque, no mínimo, `DB_PASSWORD`, `APP_BOOTSTRAP_ADMIN_EMAIL` e
`APP_BOOTSTRAP_ADMIN_PASSWORD`. A senha inicial do administrador precisa ter pelo menos 12 caracteres.
As variáveis de bootstrap são ignoradas quando o banco já possui um administrador.

3. Gere a chave de criptografia somente se o arquivo ainda não existir:

```powershell
New-Item -ItemType Directory -Force secrets | Out-Null
if (-not (Test-Path secrets/app_encryption_key)) {
    $keyBytes = [byte[]]::new(32)
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($keyBytes)
    [Convert]::ToBase64String($keyBytes) | Set-Content secrets/app_encryption_key -NoNewline
}
```

Não substitua uma chave existente: ela protege configurações sensíveis já gravadas.

4. Construa as imagens e inicie os serviços:

```powershell
docker compose --profile postgres up -d --build
```

5. Aguarde todos os serviços ficarem saudáveis:

```powershell
docker compose ps
```

As linhas de `frontend`, `api`, `postgres` e `clamav` devem mostrar `healthy`. Depois, acesse
`https://localhost:8443`. O Caddy usa um certificado local; o navegador pode pedir confirmação na
primeira abertura.

## Uso diário

Para iniciar sem reconstruir as imagens:

```powershell
docker compose --profile postgres up -d
```

Depois de alterar o código, reconstrua e recrie os contêineres:

```powershell
docker compose --profile postgres up -d --build
```

Para apenas reiniciar os contêineres atuais:

```powershell
docker compose restart
```

Para parar a aplicação preservando banco, anexos e configurações:

```powershell
docker compose stop
```

Para remover os contêineres e a rede, mas manter os volumes persistentes:

```powershell
docker compose down
```

Evite `docker compose down -v`, pois a opção `-v` remove os volumes e apaga os dados locais.

## Diagnóstico

Veja o estado dos serviços:

```powershell
docker compose ps
```

Acompanhe os logs de toda a aplicação:

```powershell
docker compose logs -f --tail 200
```

Ou consulte um serviço específico:

```powershell
docker compose logs -f --tail 200 frontend
docker compose logs -f --tail 200 api
docker compose logs -f --tail 200 postgres
docker compose logs -f --tail 200 clamav
```

Encerre a visualização dos logs com `Ctrl+C`; isso não para os contêineres.
