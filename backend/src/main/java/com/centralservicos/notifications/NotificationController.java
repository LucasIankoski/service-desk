package com.centralservicos.notifications;

import com.centralservicos.identity.CurrentUser;
import com.centralservicos.shared.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController {

    private final NotificationService notifications;
    private final CurrentUser currentUser;

    NotificationController(NotificationService notifications, CurrentUser currentUser) {
        this.notifications = notifications;
        this.currentUser = currentUser;
    }

    @GetMapping
    PageResponse<NotificationView> list(Pageable pageable) {
        return PageResponse.from(notifications.list(currentUser.id(), pageable));
    }

    @GetMapping("/unread-count")
    Map<String, Long> unreadCount() {
        return Map.of("count", notifications.unreadCount(currentUser.id()));
    }

    @PatchMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markRead(@PathVariable UUID id) {
        notifications.markRead(currentUser.id(), id);
    }

    @PatchMapping("/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markAllRead() {
        notifications.markAllRead(currentUser.id());
    }
}
