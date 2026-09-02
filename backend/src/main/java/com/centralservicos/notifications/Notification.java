package com.centralservicos.notifications;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification")
class Notification {

    @Id
    private UUID id;
    private UUID userId;
    private UUID ticketId;
    @Enumerated(EnumType.STRING)
    private NotificationType typeName;
    private String title;
    private String message;
    private Instant readAt;
    private Instant createdAt;

    protected Notification() {
    }

    Notification(UUID userId, UUID ticketId, NotificationType type, String title, String message) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.ticketId = ticketId;
        this.typeName = type;
        this.title = title;
        this.message = message;
        this.createdAt = Instant.now();
    }

    UUID id() { return id; }
    UUID userId() { return userId; }

    void markRead() {
        if (readAt == null) {
            readAt = Instant.now();
        }
    }

    NotificationView toView() {
        return new NotificationView(id, ticketId, typeName, title, message, readAt != null, createdAt);
    }
}
