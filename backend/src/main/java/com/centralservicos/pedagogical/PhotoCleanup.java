package com.centralservicos.pedagogical;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

@Entity
@Table(name = "ped_photo_cleanup")
class PhotoCleanup {
    @Id UUID id = UUID.randomUUID();
    @TenantId
    @Column(name = "unit_id", nullable = false, updatable = false)
    UUID unitId;
    String storageKey;
    Instant eligibleAt;
    protected PhotoCleanup() {}
    PhotoCleanup(String key, Instant eligibleAt) { this.storageKey = key; this.eligibleAt = eligibleAt; }
}
