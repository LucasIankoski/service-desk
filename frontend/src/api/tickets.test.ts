// @vitest-environment node
import { expect, test, vi } from "vitest";
import { apiFetch } from "./http";
import { createTicket, classifyTicket, listTickets } from "./tickets";

vi.mock("./http", () => ({ apiFetch: vi.fn().mockResolvedValue({}) }));

test("creation sends only category and description in multipart metadata", async () => {
  await createTicket({ categoryId: "category-id", description: "Descrição completa", files: [] });
  const [, options] = vi.mocked(apiFetch).mock.lastCall!;
  const metadata = (options!.body as FormData).get("metadata") as Blob;
  expect(JSON.parse(await metadata.text())).toEqual({ categoryId: "category-id", description: "Descrição completa" });
});

test("classification and filtering use the existing category contract", async () => {
  await classifyTicket("ticket-id", { categoryId: "category-id", version: 2 });
  expect(vi.mocked(apiFetch).mock.lastCall).toEqual([
    "/api/v1/tickets/ticket-id/classification",
    { method: "PATCH", body: JSON.stringify({ categoryId: "category-id", version: 2 }) }
  ]);
  await listTickets({ categoryId: "category-id" });
  const [url] = vi.mocked(apiFetch).mock.lastCall!;
  expect(new URL(url, "http://localhost").searchParams.get("categoryId")).toBe("category-id");
});
