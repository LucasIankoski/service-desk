import type { ProblemDetail } from "./types";

let csrfToken: string | null = null;
let csrfPromise: Promise<void> | null = null;

const mutatingMethods = new Set(["POST", "PUT", "PATCH", "DELETE"]);
const unitStorageKey = "service-desk.unit";
export const unitHeader = "X-Unit-Id";

// A unidade fica por aba (sessionStorage), para que duas abas em unidades diferentes não se misturem.
// O localStorage guarda só a última escolha, usada ao abrir uma aba nova.
let activeUnitId: string | null = readStoredUnit();

function readStoredUnit(): string | null {
  try {
    return window.sessionStorage.getItem(unitStorageKey) ?? window.localStorage.getItem(unitStorageKey);
  } catch {
    return null;
  }
}

export function getActiveUnit() {
  return activeUnitId;
}

export function setActiveUnit(unitId: string | null) {
  activeUnitId = unitId;
  try {
    if (unitId) {
      window.sessionStorage.setItem(unitStorageKey, unitId);
      window.localStorage.setItem(unitStorageKey, unitId);
    } else {
      window.sessionStorage.removeItem(unitStorageKey);
    }
  } catch {
    // Sem armazenamento disponível, a unidade vale apenas enquanto a página estiver aberta.
  }
}

/** Para links comuns (downloads), que não enviam cabeçalhos: leva a unidade na URL. */
export function withUnit(path: string) {
  if (!activeUnitId) {
    return path;
  }
  const separator = path.includes("?") ? "&" : "?";
  return `${path}${separator}unit=${encodeURIComponent(activeUnitId)}`;
}

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
  if (activeUnitId && !headers.has(unitHeader)) {
    headers.set(unitHeader, activeUnitId);
  }

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
