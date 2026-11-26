# ADR 0008 — Várias unidades escolares em uma instalação

**Status:** Aceito (piloto na branch `piloto-multi-tenant`)

## Contexto

O cliente vai abrir novas unidades da escola. Cada unidade deve ver apenas os próprios dados, exceto pelas pessoas a
quem o administrador der acesso a outras unidades. A instalação continua única: um domínio, um banco, um volume de
arquivos e uma chave de criptografia.

## Decisão

- **Banco compartilhado com discriminador.** As tabelas operacionais ganham `unit_id NOT NULL` com chave estrangeira
  para `school_unit`. As entidades marcadas com `@TenantId` são filtradas pelo Hibernate em JPQL, Specifications,
  consultas derivadas e `findById`, e recebem a unidade no insert. Schema ou banco por unidade foram descartados:
  quebram a portabilidade do ADR 0002 e não atendem usuários que veem várias unidades.
- **Usuário global, perfis por unidade.** O e-mail continua único na rede. `unit_membership(user_id, unit_id,
  role_name)` guarda REQUESTER, AGENT e MANAGER por unidade. **ADMIN é global**: fica em `user_role`, administra a
  rede e acessa todas as unidades ativas sem precisar de vínculo. Não existe administrador restrito a uma unidade.
- **Unidade por requisição.** A SPA envia `X-Unit-Id` em toda chamada; links de download usam `?unit=`. O
  `AccountStateFilter` valida o acesso, monta os perfis efetivos (perfis da unidade + ADMIN) e abre o
  `TenantContext` antes de qualquer transação. A unidade fica por aba (`sessionStorage`), para que abas em
  unidades diferentes não gravem na unidade errada. Sem cabeçalho vale a única unidade do usuário; com várias, as
  rotas de unidade respondem 400 até a escolha. Unidade sem acesso responde 403; recurso de outra unidade, 404.
- **Falha fechada.** Sem unidade definida, o identificador do Hibernate é um UUID que não existe: consultas voltam
  vazias e inserts falham na chave estrangeira. Rotinas do sistema, como a limpeza de fotos, rodam explicitamente
  com `TenantContext.asSystem()` (tenant raiz).
- **O que fica explícito, fora do `@TenantId`:** notificações (mostradas de todas as unidades acessíveis do
  usuário), auditoria (`unit_id` vazio para eventos da rede, como login) e o contador de chamados
  (`unit_ticket_counter`, chave `(unit_id, counter_year)`).
- **Numeração.** O número do chamado usa o código da unidade: `SD-2026-000123`. A unidade criada pela migração
  recebe o código `SD`, então os números atuais continuam iguais.
- **Configurações.** Tema, logo, imagem de login, SMTP, fuso e limites continuam valendo para a rede (`app_settings`).
  Cada unidade tem nome, código e contato próprios.
- **Arquivos.** Novos anexos, fotos e PDFs ficam sob `units/{unitId}/...`. Os arquivos antigos não são movidos; a
  autorização continua no banco.

## Consequências

- A migração `017-multi-unit` cria a unidade inicial com o nome da instituição, move os perfis não ADMIN para
  `unit_membership` e preenche `unit_id` em todas as tabelas. Faça backup antes, conforme `docs/operations.md`.
- Toda entidade nova precisa de `@TenantId` ou entrar, com justificativa, na lista de `UnitScopedEntitiesTests`.
- Operações que mudam de unidade precisam de uma transação nova, aberta depois de entrar no `TenantContext` da
  unidade de destino (por exemplo, `UnitCategorySeeder`).
- SQL nativo não passa pelo filtro do Hibernate; qualquer consulta nativa nova precisa filtrar `unit_id` à mão.
- Substitui a premissa de "uma única instituição" do ADR 0001.
