# Especificação — Central de Serviços v1

## Objetivo

Permitir que colaboradores de uma única instituição abram solicitações, acompanhem a trilha de atendimento e colaborem com uma fila interna segura e responsiva.

## Perfis

- REQUESTER abre e visualiza apenas solicitações próprias.
- AGENT visualiza a fila única e opera solicitações.
- MANAGER possui as capacidades de AGENT e também redistribui e supervisiona prazos.
- ADMIN administra a instalação; precisa acumular AGENT ou MANAGER para acessar conteúdo operacional.

## Solicitações

- Assunto e descrição são obrigatórios; anexos são opcionais.
- Categoria é opcional na abertura e obrigatória antes de `IN_PROGRESS`.
- Estados: OPEN, TRIAGE, IN_PROGRESS, WAITING_REQUESTER, RESOLVED, CLOSED e CANCELED.
- Comentários e anexos são imutáveis; notas internas não aparecem ao solicitante.
- RESOLVED pode voltar para IN_PROGRESS pelo solicitante durante a janela configurada, inicialmente 7 dias.
- Prioridades: LOW, NORMAL, HIGH e CRITICAL.
- Prazo é opcional, manual e não representa SLA.

## Administração

- Contas são criadas por ADMIN com senha temporária de exibição única.
- Cores e fundo do login podem mudar sem rebuild, respeitando contraste AA.
- SMTP envia apenas recuperação de senha e mensagens de teste.
- Configuração sensível nunca é devolvida ao navegador.

## Fora de escopo

Multi-tenancy, departamentos, catálogo, base de conhecimento, aprovações, SSO, MFA, workflow configurável, e-mail operacional, S3 e alta disponibilidade.
