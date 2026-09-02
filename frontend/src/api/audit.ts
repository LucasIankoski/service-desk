import { apiFetch } from "./http";
import type { Page } from "./types";

export type AuditEvent = {
  id: string;
  actorId?: string | null;
  action: string;
  entityType: string;
  entityId?: string | null;
  safeDetails?: string | null;
  correlationId?: string | null;
  createdAt: string;
};

export function listAuditEvents(page = 0) {
  return apiFetch<Page<AuditEvent>>(`/api/v1/admin/audit?page=${page}&size=30`);
}
