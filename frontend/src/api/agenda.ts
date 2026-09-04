import { apiFetch } from "./http";
import type { AgendaItem, AgendaItemKind, AgendaItemStatus, Assignee } from "./types";

export type AgendaItemInput = {
  kind: AgendaItemKind;
  title: string;
  description?: string | null;
  location?: string | null;
  assigneeId?: string | null;
  startAt: string;
  endAt: string;
  allDay: boolean;
};

export type AgendaItemUpdate = Omit<AgendaItemInput, "kind"> & { version: number };

export function listAgendaItems(start: string, end: string) {
  const params = new URLSearchParams({ start, end });
  return apiFetch<AgendaItem[]>(`/api/v1/agenda/items?${params.toString()}`);
}

export function createAgendaItem(input: AgendaItemInput) {
  return apiFetch<AgendaItem>("/api/v1/agenda/items", {
    method: "POST",
    body: JSON.stringify(input)
  });
}

export function updateAgendaItem(id: string, input: AgendaItemUpdate) {
  return apiFetch<AgendaItem>(`/api/v1/agenda/items/${id}`, {
    method: "PATCH",
    body: JSON.stringify(input)
  });
}

export function changeAgendaItemStatus(id: string, status: AgendaItemStatus, version: number) {
  return apiFetch<AgendaItem>(`/api/v1/agenda/items/${id}/status`, {
    method: "PATCH",
    body: JSON.stringify({ status, version })
  });
}

export function deleteAgendaItem(id: string, version: number) {
  return apiFetch<void>(`/api/v1/agenda/items/${id}?version=${version}`, { method: "DELETE" });
}

export function listManagers() {
  return apiFetch<Assignee[]>("/api/v1/users/managers");
}
