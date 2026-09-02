import { apiFetch } from "./http";
import type { Assignee, Category, CommentVisibility, Page, Priority, TicketDetail, TicketStatus, TicketSummary } from "./types";

export type TicketFilters = {
  number?: string;
  subject?: string;
  status?: TicketStatus | "";
  priority?: Priority | "";
  categoryId?: string;
  assigneeId?: string;
  dueAfter?: string;
  dueBefore?: string;
};

export function listTickets(filters: TicketFilters, page = 0) {
  const params = new URLSearchParams({ page: String(page), size: "20", sort: "updatedAt,desc" });
  Object.entries(filters).forEach(([key, value]) => {
    if (value) {
      const normalized = key === "dueAfter" || key === "dueBefore"
        ? new Date(value).toISOString()
        : value;
      params.set(key, normalized);
    }
  });
  return apiFetch<Page<TicketSummary>>(`/api/v1/tickets?${params.toString()}`);
}

export function getTicket(id: string) {
  return apiFetch<TicketDetail>(`/api/v1/tickets/${id}`);
}

export function createTicket(input: { subject: string; description: string; categoryId?: string; files: File[] }) {
  const body = new FormData();
  body.append("metadata", new Blob([JSON.stringify({
    subject: input.subject,
    description: input.description,
    categoryId: input.categoryId || null
  })], { type: "application/json" }));
  input.files.forEach((file) => body.append("files", file));
  return apiFetch<TicketDetail>("/api/v1/tickets", { method: "POST", body });
}

export function assignTicket(id: string, assigneeId: string, version: number) {
  return apiFetch<TicketDetail>(`/api/v1/tickets/${id}/assignment`, {
    method: "PATCH",
    body: JSON.stringify({ assigneeId, version })
  });
}

export function classifyTicket(id: string, input: {
  categoryId: string;
  priority: Priority;
  dueAt?: string | null;
  version: number;
}) {
  return apiFetch<TicketDetail>(`/api/v1/tickets/${id}/classification`, {
    method: "PATCH",
    body: JSON.stringify(input)
  });
}

export function updateStatus(id: string, status: TicketStatus, version: number) {
  return apiFetch<TicketDetail>(`/api/v1/tickets/${id}/status`, {
    method: "PATCH",
    body: JSON.stringify({ status, version })
  });
}

export function addComment(id: string, input: { body: string; visibility: CommentVisibility; files: File[] }) {
  const body = new FormData();
  body.append("metadata", new Blob([JSON.stringify({
    body: input.body,
    visibility: input.visibility
  })], { type: "application/json" }));
  input.files.forEach((file) => body.append("files", file));
  return apiFetch<TicketDetail>(`/api/v1/tickets/${id}/comments`, { method: "POST", body });
}

export function listCategories() {
  return apiFetch<Category[]>("/api/v1/categories");
}

export function listAssignees() {
  return apiFetch<Assignee[]>("/api/v1/users/assignees");
}

export const statusLabels: Record<TicketStatus, string> = {
  OPEN: "Aberta",
  TRIAGE: "Em triagem",
  IN_PROGRESS: "Em atendimento",
  WAITING_REQUESTER: "Aguardando solicitante",
  RESOLVED: "Resolvida",
  CLOSED: "Fechada",
  CANCELED: "Cancelada"
};

export const priorityLabels: Record<Priority, string> = {
  LOW: "Baixa",
  NORMAL: "Normal",
  HIGH: "Alta",
  CRITICAL: "Crítica"
};
