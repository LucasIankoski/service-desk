import { apiFetch, clearCsrf } from "./http";
import type { Me } from "./types";

export function getMe() {
  return apiFetch<Me>("/api/v1/auth/me");
}

export function login(email: string, password: string) {
  return apiFetch<Me>("/api/v1/auth/session", {
    method: "POST",
    body: JSON.stringify({ email, password })
  });
}

export async function logout() {
  await apiFetch<void>("/api/v1/auth/session", { method: "DELETE" });
  clearCsrf();
}

export function changePassword(password: string) {
  return apiFetch<void>("/api/v1/auth/password/change", {
    method: "POST",
    body: JSON.stringify({ password })
  });
}

export function forgotPassword(email: string) {
  return apiFetch<void>("/api/v1/auth/password/forgot", {
    method: "POST",
    body: JSON.stringify({ email })
  });
}

export function resetPassword(token: string, password: string) {
  return apiFetch<void>("/api/v1/auth/password/reset", {
    method: "POST",
    body: JSON.stringify({ token, password })
  });
}
