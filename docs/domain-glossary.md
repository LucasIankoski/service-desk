# Linguagem do domínio

- **Solicitação**: registro aberto por um colaborador para pedir atendimento. Possui assunto, descrição, categoria, prioridade, responsável, prazo e uma trilha de atendimento.
- **Solicitante**: colaborador que abre e acompanha suas próprias solicitações.
- **Fila única**: conjunto de todas as solicitações disponíveis aos atendentes e administrativos; não há departamentos na v1.
- **Atendente**: colaborador que assume, classifica, comenta e altera o estado de solicitações.
- **Administrativo**: supervisor da fila única, com capacidade de redistribuir solicitações e administrar prazos.
- **Administrador**: responsável técnico por usuários, identidade visual, SMTP, parâmetros e auditoria. Administração não concede acesso operacional implicitamente.
- **Trilha de atendimento**: sequência imutável de eventos visíveis que explica a evolução de uma solicitação.
- **Nota interna**: comentário visível apenas para atendentes e administrativos.
- **Prazo**: data final opcional definida manualmente. Não é um SLA calculado.
- **Agenda**: calendário institucional que reúne eventos públicos e demandas internas do Administrativo.
- **Evento institucional**: compromisso da instituição publicado por um Administrativo e visível também aos Solicitantes.
- **Demanda interna**: atividade da Agenda compartilhada somente entre Administrativos, com responsável opcional e andamento pendente ou concluído.
- **Configuração pública**: subconjunto não sensível da identidade da instituição necessário antes da autenticação.
