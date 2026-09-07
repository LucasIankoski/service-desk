import { apiFetch } from "./http";
import type { AgendaShift, AgendaOccurrence, AgendaItem, AgendaItemKind, AgendaItemPriority, AgendaItemStatus, Assignee } from "./types";

export type AgendaItemInput = {
  shift?: AgendaShift | null;
  kind: AgendaItemKind;
  priority?: AgendaItemPriority | null;
  title: string;
  description?: string | null;
  location?: string | null;
  assigneeId?: string | null;
  assigneeIds?: string[];
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

export function agendaAssignees(item?: AgendaItem): Assignee[] {
  return item?.assignees ?? (item?.assigneeId ? [{ id: item.assigneeId, displayName: item.assigneeName ?? "Responsável indisponível" }] : []);
}

export function listAgendaOccurrences(start: string, end: string) {
  return apiFetch<AgendaOccurrence[]>(`/api/v1/agenda/occurrences?${new URLSearchParams({ start, end })}`);
}

export function createAgendaOccurrence(input: { date: string; body: string }) {
  return apiFetch<AgendaOccurrence>("/api/v1/agenda/occurrences", { method: "POST", body: JSON.stringify(input) });
}

export function updateAgendaOccurrence(id: string, input: { date: string; body: string; version: number }) {
  return apiFetch<AgendaOccurrence>(`/api/v1/agenda/occurrences/${id}`, { method: "PATCH", body: JSON.stringify(input) });
}

export function deleteAgendaOccurrence(id: string, version: number) {
  return apiFetch<void>(`/api/v1/agenda/occurrences/${id}?version=${version}`, { method: "DELETE" });
}
