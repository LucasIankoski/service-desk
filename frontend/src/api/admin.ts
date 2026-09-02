import { apiFetch } from "./http";
import type { Category, Page, Role, User } from "./types";

export function listUsers(page = 0) {
  return apiFetch<Page<User>>(`/api/v1/admin/users?page=${page}&size=20`);
}

export function createUser(input: { email: string; displayName: string; roles: Role[] }) {
  return apiFetch<{ user: User; temporaryPassword: string }>("/api/v1/admin/users", {
    method: "POST",
    body: JSON.stringify(input)
  });
}

export function updateUser(id: string, input: { displayName: string; roles: Role[]; active: boolean }) {
  return apiFetch<User>(`/api/v1/admin/users/${id}`, {
    method: "PATCH",
    body: JSON.stringify(input)
  });
}

export function resetTemporaryPassword(id: string) {
  return apiFetch<{ temporaryPassword: string }>(`/api/v1/admin/users/${id}/temporary-password`, {
    method: "POST"
  });
}

export function anonymizeUser(id: string) {
  return apiFetch<void>(`/api/v1/admin/users/${id}/anonymize`, { method: "POST" });
}

export function listAdminCategories() {
  return apiFetch<Category[]>("/api/v1/admin/categories");
}

export function createCategory(input: { name: string; active: boolean }) {
  return apiFetch<Category>("/api/v1/admin/categories", {
    method: "POST",
    body: JSON.stringify(input)
  });
}

export function updateCategory(id: string, input: { name: string; active: boolean }) {
  return apiFetch<Category>(`/api/v1/admin/categories/${id}`, {
    method: "PATCH",
    body: JSON.stringify(input)
  });
}
