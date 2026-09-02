import type { ProblemDetail } from "./types";

let csrfToken: string | null = null;
let csrfPromise: Promise<void> | null = null;

const mutatingMethods = new Set(["POST", "PUT", "PATCH", "DELETE"]);

export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetail;

  constructor(status: number, problem: ProblemDetail) {
    super(problem.detail ?? "Não foi possível concluir a operação.");
    this.status = status;
    this.problem = problem;
  }
}

export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? "GET").toUpperCase();
  const headers = new Headers(init.headers);

  if (mutatingMethods.has(method)) {
    await ensureCsrf();
    if (csrfToken) {
      headers.set("X-XSRF-TOKEN", csrfToken);
    }
  }

  if (init.body && !(init.body instanceof FormData) && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const response = await fetch(path, {
    ...init,
    method,
    headers,
    credentials: "include"
  });

  if (response.status === 204) {
    return undefined as T;
  }
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}));
    throw new ApiError(response.status, problem);
  }
  const contentType = response.headers.get("content-type") ?? "";
  if (!contentType.includes("application/json")) {
    return response.blob() as T;
  }
  return response.json() as Promise<T>;
}

async function ensureCsrf() {
  if (csrfToken) {
    return;
  }
  csrfPromise ??= fetch("/api/v1/auth/csrf", { credentials: "include" })
    .then((response) => response.json())
    .then((payload: { token: string }) => {
      csrfToken = payload.token;
    })
    .finally(() => {
      csrfPromise = null;
    });
  await csrfPromise;
}

export function clearCsrf() {
  csrfToken = null;
}
