import type { TicketStatus } from "../api/types";
import { statusLabels } from "../api/tickets";

export function statusLabel(status: TicketStatus) {
  return statusLabels[status];
}

export function statusTone(status: TicketStatus) {
  if (status === "RESOLVED") return "teal";
  if (status === "WAITING_REQUESTER") return "amber";
  return "blue";
}

export function formatDateTime(value?: string | null) {
  if (!value) return "Não informado";
  return new Intl.DateTimeFormat("pt-BR", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit"
  }).format(new Date(value));
}
