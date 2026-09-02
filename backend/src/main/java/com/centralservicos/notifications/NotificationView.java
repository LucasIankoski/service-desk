package com.centralservicos.notifications;

import java.time.Instant;
import java.util.UUID;

public record NotificationView(UUID id, UUID ticketId, NotificationType type, String title, String message,
                               boolean read, Instant createdAt) {
}
