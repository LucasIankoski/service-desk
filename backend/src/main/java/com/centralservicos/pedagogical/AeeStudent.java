package com.centralservicos.pedagogical;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "aee_student")
class AeeStudent {
    @Id UUID id = UUID.randomUUID();
    UUID aeeClassId;
    UUID classId;
    String name;
    boolean archived;
    @Version Long rowVersion;
    Instant createdAt = Instant.now();
    Instant updatedAt = createdAt;
    protected AeeStudent() {}
}
