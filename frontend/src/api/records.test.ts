import { afterEach, describe, expect, it, vi } from "vitest";
import { clearCsrf } from "./http";
import { listRecords, saveRecord, deleteRecord } from "./records";

afterEach(() => { vi.unstubAllGlobals(); clearCsrf(); });
function mockFetch() {
  const fetcher = vi.fn(async (path: string) => ({ ok: true, status: path.includes("version=") ? 204 : 200, headers: new Headers({ "content-type": "application/json" }), json: async () => path.endsWith("csrf") ? { token: "token" } : {} }));
  vi.stubGlobal("fetch", fetcher);
  return fetcher;
}
describe("contratos dos registros", () => {
  it("envia metadados JSON e fotos em multipart com proteção CSRF", async () => {
    const fetcher = mockFetch();
    const photo = new File(["image"], "foto.png", { type: "image/png" });
    await saveRecord("class", { title: "Pintura", activityDate: "2026-09-27", version: 3, retainedPhotoIds: ["old"] }, [photo], "album");
    const [url, options] = fetcher.mock.calls.at(-1)! as unknown as [string, RequestInit];
    expect(url).toBe("/api/v1/pedagogical/records/album");
    expect(options.method).toBe("PUT");
    expect(new Headers(options.headers).get("X-XSRF-TOKEN")).toBe("token");
    expect(new Headers(options.headers).has("Content-Type")).toBe(false);
    const body = options.body as FormData;
    const metadata = body.get("metadata") as Blob;
    expect(metadata.type).toBe("application/json");
    const text = await new Promise<string>(resolve => { const reader = new FileReader(); reader.onload = () => resolve(String(reader.result)); reader.readAsText(metadata); });
    expect(JSON.parse(text)).toEqual({ title: "Pintura", activityDate: "2026-09-27", version: 3, retainedPhotoIds: ["old"] });
    expect(body.getAll("files")).toHaveLength(1);
  });
  it("inclui versão na exclusão e período na listagem", async () => {
    const fetcher = mockFetch();
    await listRecords("class", "2026", "9", 2);
    expect(fetcher.mock.calls[0][0]).toBe("/api/v1/pedagogical/classes/class/records?page=2&year=2026&month=9");
    await deleteRecord("album", 7);
    expect(fetcher.mock.calls.at(-1)![0]).toBe("/api/v1/pedagogical/records/album?version=7");
  });
});
