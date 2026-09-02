package com.centralservicos.identity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_token")
class PasswordResetToken {

    @Id
    private UUID id;
    private UUID userId;
    private String tokenHash;
    private Instant expiresAt;
    private Instant usedAt;
    private Instant createdAt;

    protected PasswordResetToken() {
    }

    PasswordResetToken(UUID userId, String tokenHash) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = Instant.now().plusSeconds(900);
        this.createdAt = Instant.now();
    }

    UUID userId() { return userId; }
    boolean validNow() { return usedAt == null && expiresAt.isAfter(Instant.now()); }
    void use() { usedAt = Instant.now(); }
}
