# ADR 0001 — Monólito modular

**Status:** Aceito

## Decisão

Usar um único deploy Spring Boot organizado em módulos funcionais verificados pelo Spring Modulith.

## Motivo

A v1 possui uma única instituição, fila e banco. Separar processos agora aumentaria autenticação distribuída, observabilidade e consistência sem criar uma seam real. Interfaces de anexos, malware e criptografia permanecem substituíveis.
