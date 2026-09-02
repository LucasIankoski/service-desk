import { apiFetch } from "./http";
import type { AdminSettings, PublicSettings, Theme } from "./types";

export function getPublicSettings() {
  return apiFetch<PublicSettings>("/api/v1/public/settings");
}

export function getAdminSettings() {
  return apiFetch<AdminSettings>("/api/v1/admin/settings");
}

export function updateGeneralSettings(input: {
  institutionName: string;
  supportEmail?: string;
  supportPhone?: string;
  timezoneName: string;
  attachmentLimitMb: number;
  reopenDays: number;
  deadlineWarningHours: number;
  version: number;
}) {
  return apiFetch<AdminSettings>("/api/v1/admin/settings/general", {
    method: "PATCH",
    body: JSON.stringify(input)
  });
}

export function previewTheme(theme: Theme) {
  return apiFetch<{ theme: Theme; warnings: string[]; valid: boolean }>("/api/v1/admin/settings/theme/preview", {
    method: "POST",
    body: JSON.stringify(theme)
  });
}

export function updateTheme(theme: Theme, version: number) {
  return apiFetch<AdminSettings>("/api/v1/admin/settings/theme", {
    method: "PATCH",
    body: JSON.stringify({ theme, version })
  });
}

export function updateLoginBackground(file: File) {
  const body = new FormData();
  body.append("file", file);
  return apiFetch<AdminSettings>("/api/v1/admin/settings/login-background", { method: "POST", body });
}

export function updateSmtp(input: {
  host?: string;
  port?: number;
  tls: boolean;
  fromName?: string;
  fromAddress?: string;
  username?: string;
  password?: string;
  version: number;
}) {
  return apiFetch<AdminSettings>("/api/v1/admin/settings/smtp", {
    method: "PATCH",
    body: JSON.stringify(input)
  });
}

export function testSmtp() {
  return apiFetch<void>("/api/v1/admin/settings/smtp/test", { method: "POST" });
}
