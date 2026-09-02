import { apiFetch } from "./http";
import type { Notification, Page } from "./types";

export function listNotifications() {
  return apiFetch<Page<Notification>>("/api/v1/notifications?page=0&size=20");
}

export function unreadNotificationCount() {
  return apiFetch<{ count: number }>("/api/v1/notifications/unread-count");
}

export function markNotificationRead(id: string) {
  return apiFetch<void>(`/api/v1/notifications/${id}/read`, { method: "PATCH" });
}

export function markAllNotificationsRead() {
  return apiFetch<void>("/api/v1/notifications/read", { method: "PATCH" });
}
