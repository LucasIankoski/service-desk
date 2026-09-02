# ADR 0003 — Sessões e arquivos

**Status:** Aceito

## Decisão

Sessões ficam no banco por Spring Session JDBC e chegam ao navegador apenas em cookie seguro. Anexos ficam em volume privado através de `AttachmentStorage` e são entregues somente pela API após autorização.

## Consequência

A v1 suporta uma instância de aplicação. Escala horizontal exigirá filesystem compartilhado ou um segundo adaptador, como S3.
