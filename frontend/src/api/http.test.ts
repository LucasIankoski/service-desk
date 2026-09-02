import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { apiFetch, clearCsrf } from "./http";

describe("apiFetch", () => {
  beforeEach(() => {
    clearCsrf();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("loads a CSRF token once and sends it on mutations", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ token: "csrf-token" }))
      .mockResolvedValueOnce(jsonResponse({ saved: true }))
      .mockResolvedValueOnce(jsonResponse({ saved: true }));
    vi.stubGlobal("fetch", fetchMock);

    await apiFetch("/api/v1/example", { method: "POST", body: JSON.stringify({ value: 1 }) });
    await apiFetch("/api/v1/example", { method: "PATCH", body: JSON.stringify({ value: 2 }) });

    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls[0][0]).toBe("/api/v1/auth/csrf");
    const firstMutation = new Headers(fetchMock.mock.calls[1][1]?.headers);
    const secondMutation = new Headers(fetchMock.mock.calls[2][1]?.headers);
    expect(firstMutation.get("X-XSRF-TOKEN")).toBe("csrf-token");
    expect(firstMutation.get("Content-Type")).toBe("application/json");
    expect(secondMutation.get("X-XSRF-TOKEN")).toBe("csrf-token");
  });

  it("turns a problem response into an ApiError", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValueOnce(jsonResponse({
      status: 403,
      detail: "Acesso negado"
    }, 403)));

    await expect(apiFetch("/api/v1/admin/settings")).rejects.toMatchObject({
      status: 403,
      message: "Acesso negado"
    });
  });
});

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" }
  });
}
