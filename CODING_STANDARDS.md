# Padrões de código

## Geral

- O contrato em `docs/specs` prevalece sobre conveniências de implementação.
- Segredos, dados pessoais, descrições e conteúdo de anexos nunca entram em logs.
- Mudanças públicas exigem atualização do OpenAPI e testes de autorização.
- Prefira módulos profundos com interfaces pequenas; novas abstrações precisam de pelo menos dois consumidores reais.

## Java

- Organize por módulo de domínio, não por tipo técnico global.
- Controllers validam e delegam; regras de permissão e transição permanecem na camada de aplicação/domínio.
- Use DTOs em interfaces HTTP; entidades JPA não são serializadas.
- Use `Instant` para persistência temporal e UUID para identidade interna.
- Consultas são parametrizadas por JPA; SQL nativo exige ADR e teste nos quatro bancos.
- Toda mutação relevante registra auditoria dentro da mesma transação.

## React e TypeScript

- TypeScript estrito, sem `any` implícito.
- Dados remotos pertencem ao TanStack Query; estado local permanece no componente mais próximo.
- Rotas pesadas são carregadas dinamicamente e imports evitam barrels.
- Não derive estado por `useEffect`; derive durante render ou no seletor da consulta.
- Todo controle possui nome acessível, foco visível e estado de erro acionável.
- Cores vêm de tokens CSS e precisam manter WCAG 2.2 AA.

## Testes

- Teste comportamento público e autorização, não detalhes privados.
- Cada correção inclui teste que falhava antes da mudança.
- Persistência portável exige pelo menos uma execução da matriz de bancos antes da release.
