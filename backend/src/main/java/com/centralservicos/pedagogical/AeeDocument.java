package com.centralservicos.pedagogical;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="aee_document")
class AeeDocument {
    @Id UUID id=UUID.randomUUID();
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
