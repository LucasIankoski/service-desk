# Deploy Amor e Graça — 07/09/2026

> Histórico da instalação inicial. O endereço atual é
> https://www.amor-e-graca.com, com túnel permanente. A configuração provisória
> descrita abaixo foi substituída. Veja `deploy-20260927.md` e
> `deploy-20260927-r2.md` para as atualizações.

Aplicação instalada no Ubuntu do cliente, em `192.168.1.10`.

- URL pública inicial: https://compensation-bat-hearing-peace.trycloudflare.com
- Rede interna: https://192.168.1.10:8443
- Diretório da instalação: `/home/vidalettipam/service-desk/releases/20260908`
- Backups: `/home/vidalettipam/service-desk/backups/20260908`
- Cópia local da migração: `data/deploy-20260908/` (ignorada pelo Git).

## Endereço gratuito

O acesso público utiliza um Cloudflare Quick Tunnel, com certificado HTTPS válido.
Esse serviço fornece um nome aleatório, temporário, sem garantia de disponibilidade;
o nome muda quando o processo do túnel reinicia. Não foi registrado um domínio.
Quick Tunnels são destinados a testes, têm limite de 200 requisições simultâneas e
não suportam SSE: https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/do-more-with-tunnels/trycloudflare/

O serviço `central-servicos-tunnel.service` inicia no boot e reinicia em caso de
falha. O script `quick-tunnel.py` atualiza `PUBLIC_BASE_URL` e recria a API para que
links gerados pela aplicação acompanhem o endereço. O endereço atual pode ser lido
no servidor:

```bash
sudo cat /home/vidalettipam/service-desk/public-url.txt
```

Para endereço fixo com o nome da instituição, ainda será necessário configurar um
domínio/subdomínio sob controle do cliente e um serviço de publicação estável.
Não trate a URL provisória como um endereço definitivo de produção.

O acesso pela rede interna usa a autoridade certificadora local do Caddy. O
certificado público dessa autoridade está em
`/home/vidalettipam/service-desk/central-servicos-root.crt`; os dispositivos precisam
confiar nele para acessar o IP sem aviso de certificado. A URL pública não exige
essa instalação.

## Dados preservados

As escritas locais foram interrompidas antes de `pg_dump`. O dump foi restaurado
em um banco novo, preservando usuários, hashes de senha e configurações. A chave de
criptografia original e todo o volume de arquivos foram copiados com autorização.

Contagens e hashes de linhas de todas as tabelas de negócio foram comparados e
coincidiram, incluindo:

- 17 usuários e 24 vínculos de perfis;
- 3 categorias;
- 80 itens de agenda, 68 vínculos de responsáveis e 21 ocorrências;
- 221 eventos de auditoria no instante da migração;
- 7 arquivos físicos, todos validados com SHA-256.

Não havia solicitações ou registros de anexos na tabela `attachment`; os arquivos
físicos existentes, inclusive identidade visual e arquivos antigos, foram mantidos.
As evidências locais estão em `source-fingerprints.txt`, `attachments.sha256` e
`fingerprints.sql`, dentro da pasta de backup local. O relatório do destino está
em `server-fingerprints.txt`, dentro da pasta de backup do servidor.

A migration `007-simplify-tickets` estava registrada com um autor diferente de
`service-desk-team`, declarado no código atual. O arquivo compilado local e o arquivo
fonte foram comparados: eram idênticos exceto pelo autor. As colunas removidas e o
índice novo também foram conferidos no banco restaurado. Somente o autor dessa
linha de `databasechangelog` foi corrigido no destino, preservando o checksum;
nenhuma migration foi reaplicada e nenhum registro de negócio foi alterado nessa
correção. O dump original foi preservado, e `postdeploy.dump` contém o banco após
a correção e os testes operacionais.

A aplicação web local permanece parada para evitar duas instalações recebendo
alterações independentes. O PostgreSQL local foi mantido e seus dados preservados.
A migração é uma cópia pontual, sem sincronização contínua entre os dois bancos.

## Operação

```bash
cd /home/vidalettipam/service-desk/releases/20260908
sudo docker compose ps
sudo docker compose logs --tail=100 api frontend
sudo docker compose up -d
sudo systemctl status central-servicos-tunnel.service --no-pager
sudo journalctl -u central-servicos-tunnel.service -n 50 --no-pager
```

PostgreSQL e API não publicam portas diretamente no host. O proxy do túnel escuta
apenas em `127.0.0.1:8088`; HTTPS interno usa a porta 8443. Os três containers têm
reinício automático e rotação de logs. O banco usa uma senha nova no destino;
usuários da aplicação continuam com suas senhas anteriores.

Antes de atualizar ou restaurar, interrompa API/frontend e faça novo backup
consistente do banco, volume de arquivos e chave, conforme `docs/operations.md`.
Não restaure o snapshot inicial por cima de registros criados após o deploy.
Os backups desta migração são pontuais; não foi configurada uma rotina periódica.

## Validação

- Imagens construídas do código local e transferidas com SHA-256 conferido.
- Banco, API e frontend saudáveis; readiness da API `UP`.
- Página pública, configurações públicas e imagem de login: HTTP 200.
- API autenticada sem sessão: HTTP 401; `/actuator/health` público: HTTP 404.
- HTTPS interno por IP: HTTP 200.
- Serviço do túnel ativo e habilitado no boot.
- A senha antiga de bootstrap retornou HTTP 401 em teste pela rede interna.
  Nenhuma conta foi redefinida; o login com uma credencial atual do cliente ainda
  precisa ser confirmado. Essa tentativa gerou um evento normal de auditoria após
  a comparação da migração.

Durante a extração, o Docker Desktop local travou. Após autorização, seus processos
foram encerrados e os diretórios de sockets temporários foram preservados como
`run-stale-20260908` e `docker-secrets-engine-stale-20260908` no AppData local.
O Docker voltou a funcionar; imagens, volumes e bancos não foram removidos.
