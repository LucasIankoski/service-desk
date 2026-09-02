import type { Priority, TicketStatus } from "../api/types";
import { priorityLabels, statusLabels } from "../api/tickets";

export function statusLabel(status: TicketStatus) {
  return statusLabels[status];
}

export function priorityLabel(priority: Priority) {
  return priorityLabels[priority];
}

export function statusTone(status: TicketStatus) {
  if (status === "RESOLVED" || status === "CLOSED") return "teal";
  if (status === "WAITING_REQUESTER") return "amber";
  if (status === "CANCELED") return "neutral";
  return "blue";
}

export function priorityTone(priority: Priority) {
  if (priority === "CRITICAL") return "red";
  if (priority === "HIGH") return "amber";
  if (priority === "LOW") return "neutral";
  return "blue";
}

export function formatDateTime(value?: string | null) {
  if (!value) return "Sem prazo";
  return new Intl.DateTimeFormat("pt-BR", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit"
  }).format(new Date(value));
}
