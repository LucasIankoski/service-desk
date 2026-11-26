package com.centralservicos.pedagogical;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

@Entity @Table(name="aee_document")
class AeeDocument {
    @Id UUID id=UUID.randomUUID();
    @TenantId
    @Column(name = "unit_id", nullable = false, updatable = false)
    UUID unitId;
    UUID studentId;
    String filename;
    String storageKey;
    long size;
    UUID authorId;
    String authorName;
    @Version Long rowVersion;
    Instant createdAt=Instant.now();
    protected AeeDocument() {}
}
