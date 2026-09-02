package com.centralservicos.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditView(UUID id, UUID actorId, String action, String entityType, String entityId,
                        String details, String correlationId, Instant createdAt) {
}
