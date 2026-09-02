package com.centralservicos.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_event")
class AuditEvent {

    @Id
    private UUID id;
    private UUID actorId;
    private String actionName;
    private String entityType;
    private String entityId;
    private String detailsJson;
    private String correlationId;
    private Instant createdAt;

    protected AuditEvent() {
    }

    AuditEvent(UUID actorId, String actionName, String entityType, String entityId, String detailsJson,
               String correlationId) {
        this.id = UUID.randomUUID();
        this.actorId = actorId;
        this.actionName = actionName;
        this.entityType = entityType;
        this.entityId = entityId;
        this.detailsJson = detailsJson;
        this.correlationId = correlationId;
        this.createdAt = Instant.now();
    }

    AuditView toView() {
        return new AuditView(id, actorId, actionName, entityType, entityId, detailsJson, correlationId, createdAt);
    }
}
