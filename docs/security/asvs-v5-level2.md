# Matriz de segurança — ASVS 5.0 nível 2

| Área | Controle da v1 | Evidência |
|---|---|---|
| Encoding e injeção | Bean Validation, DTOs e consultas JPA parametrizadas | testes de API e CodeQL |
| Arquivos | allowlist, assinatura, tamanho, nome aleatório e ClamAV | testes de upload e EICAR |
| Autenticação | Argon2id, rate limit, senha temporária e reset de uso único | testes de identidade |
| Sessão | cookie HttpOnly/Secure/SameSite, CSRF, idle e duração absoluta | testes Spring Security |
| Autorização | RBAC cumulativo, ownership e filtragem de demandas da Agenda no banco | matriz de autorização e testes de Agenda |
| Criptografia | AES-256-GCM para SMTP e secrets externos | testes de round-trip e rotação |
| Logs | correlação sem conteúdo de chamado ou secrets | inspeção automatizada/manual |
| Configuração | headers, TLS, containers sem root e healthchecks | teste Compose/ZAP |

Claims formais de conformidade dependem de verificação independente; esta matriz é a baseline de engenharia.
