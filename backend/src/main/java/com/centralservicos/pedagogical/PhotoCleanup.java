package com.centralservicos.pedagogical;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ped_photo_cleanup")
class PhotoCleanup {
    @Id UUID id = UUID.randomUUID();
    String storageKey;
    Instant eligibleAt;
    protected PhotoCleanup() {}
    PhotoCleanup(String key, Instant eligibleAt) { this.storageKey = key; this.eligibleAt = eligibleAt; }
}
