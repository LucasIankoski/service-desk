package com.centralservicos.notifications;

import com.centralservicos.shared.DomainException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository repository;

    NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void notify(Collection<UUID> users, UUID ticketId, NotificationType type, String title, String message) {
        if (users == null || users.isEmpty()) {
            return;
        }
        new LinkedHashSet<>(users).stream()
                .filter(userId -> userId != null)
                .map(userId -> new Notification(userId, ticketId, type, truncate(title, 160), truncate(message, 500)))
                .forEach(repository::save);
    }

    @Transactional
    public void notify(UUID userId, UUID ticketId, NotificationType type, String title, String message) {
        notify(Set.of(userId), ticketId, type, title, message);
    }

    @Transactional(readOnly = true)
    public Page<NotificationView> list(UUID userId, Pageable pageable) {
        return repository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable).map(Notification::toView);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return repository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        var notification = repository.findById(notificationId)
                .orElseThrow(() -> DomainException.notFound("Notificação não encontrada."));
        if (!notification.userId().equals(userId)) {
            throw DomainException.forbidden("Notificação indisponível para este usuário.");
        }
        notification.markRead();
    }

    @Transactional
    public void markAllRead(UUID userId) {
        repository.findAllByUserIdAndReadAtIsNull(userId).forEach(Notification::markRead);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
