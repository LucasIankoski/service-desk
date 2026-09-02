# ADR 0002 — Portabilidade entre bancos

**Status:** Aceito

## Decisão

JPA/Hibernate e Liquibase declarativo são a interface de persistência. O fornecedor é fixado no deploy por configuração externa.

## Restrições

- Não usar funções SQL proprietárias em regras de negócio.
- Identidades são UUID gerados pela aplicação.
- Texto extenso usa LOB e datas usam UTC.
- Mudanças específicas exigem changeset `dbms` e teste equivalente nos quatro fornecedores.
