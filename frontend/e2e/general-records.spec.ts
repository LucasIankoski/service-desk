import { expect, test, type Page } from "@playwright/test";
import axe from "axe-core";
import type { GeneralRecord } from "../src/api/records";

const png = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a3ioAAAAASUVORK5CYII=", "base64");
const file = { name: "evento.png", mimeType: "image/png", buffer: png };
async function setup(page: Page, roles = ["REQUESTER"], canEdit = true) {
  let record: GeneralRecord = { id: "event", title: "Festa da escola", activityDate: "2026-10-25", authorId: "a", authorName: "Cristine",
    version: 0, canEdit, createdAt: "2026-10-25T12:00:00Z", updatedAt: "2026-10-25T12:00:00Z", attachmentLimitMb: 5, maxPhotos: 100,
    photos: [1, 2, 3].map(i => ({ id: String(i), url: `/api/v1/records/event/photos/${i}`, thumbnailUrl: `/api/v1/records/event/photos/${i}/thumbnail`, width: 800, height: 400 })) };
  let failure = 0, removed = false;
  await page.route("**/api/v1/**", async route => {
    const url = new URL(route.request().url()), path = url.pathname, method = route.request().method();
    const json = (body: unknown, status = 200) => route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });
    if (path.endsWith("/auth/me")) return json({ id: "a", displayName: "Cristine", roles, passwordChangeRequired: false });
    if (path.endsWith("/auth/csrf")) return json({ token: "test" });
    if (path.endsWith("/public/settings")) return json({ institutionName: "Escola Modelo", timezoneName: "America/Sao_Paulo" });
    if (path.includes("/notifications")) return json(path.endsWith("/unread-count") ? { count: 0 } : { content: [] });
    if (path.endsWith("/categories") || path.endsWith("/assignees")) return json([]);
    if (path.includes("/photos/")) return route.fulfill({ contentType: "image/svg+xml", body: '<svg xmlns="http://www.w3.org/2000/svg" width="800" height="400"><rect width="800" height="400" fill="#659ba8"/></svg>' });
    if (path === "/api/v1/records" && method === "GET") {
      const matches = !removed && (!url.searchParams.has("year") || record.activityDate.startsWith(url.searchParams.get("year")!))
        && (!url.searchParams.has("month") || Number(record.activityDate.slice(5, 7)) === Number(url.searchParams.get("month")));
      return json({ items: matches ? [{ ...record, photoCount: record.photos.length, cover: record.photos[0] }] : [], page: 0, totalPages: 1, totalElements: matches ? 1 : 0, attachmentLimitMb: 5, maxPhotos: 100, canCreate: true });
    }
    if (path === "/api/v1/records/event" && method === "GET") return json(record);
    if (path === "/api/v1/records/event" && method === "DELETE") {
      if (failure) { failure = 0; record.version++; return json({ detail: "Este registro mudou." }, 409); }
      expect(url.searchParams.get("version")).toBe(String(record.version));
      removed = true; return route.fulfill({ status: 204 });
    }
    if ((path === "/api/v1/records" || path === "/api/v1/records/event") && (method === "POST" || method === "PUT")) {
      if (failure) { const status = failure; failure = 0; if (status === 409) record = { ...record, title: "Versão da colega", version: record.version + 1 }; return json({ detail: status === 409 ? "Este registro mudou." : "Falha no envio. Tente novamente." }, status); }
      const raw = route.request().postDataBuffer()?.toString() ?? "";
      const part = raw.match(/name="metadata"[^]*?\r\n\r\n([^]*?)\r\n--/)?.[1];
      const input = part ? JSON.parse(part) : { title: await page.getByLabel("Título do evento").inputValue(), activityDate: await page.getByLabel("Data do evento").inputValue(), retainedPhotoIds: method === "PUT" ? record.photos.slice(0, await page.getByRole("button", { name: /^Remover foto / }).count()).map(p => p.id) : [] };
      const count = await page.getByRole("button", { name: /^Remover nova foto / }).count();
      const retained = record.photos.filter(p => input.retainedPhotoIds.includes(p.id));
      record = { ...record, title: input.title, activityDate: input.activityDate, version: record.version + 1,
        photos: [...retained, ...Array.from({ length: count }, (_, i) => ({ ...record.photos[0], id: `new-${record.version}-${i}` }))] };
      return json(record, method === "POST" ? 201 : 200);
    }
    return json({ content: [], totalElements: 0, totalPages: 0 });
  });
  return { fail: (status: number) => { failure = status; } };
}

test("módulo geral cria álbum sem turma, filtra e abre galeria acessível", async ({ page }, info) => {
  await setup(page);
  await page.goto("/registros");
  await expect(page.getByRole("link", { name: "Registros", exact: true }).filter({ visible: true })).toBeVisible();
  await page.getByLabel("Ano", { exact: true }).fill("2025");
  await expect(page.getByRole("heading", { name: "Nenhum registro neste período" })).toBeVisible();
  await page.getByLabel("Ano", { exact: true }).fill("2026");
  await expect(page).toHaveURL(/ano=2026/);
  await expect(page.getByLabel("Ano", { exact: true })).toHaveValue("2026");
  await expect(page.getByRole("link", { name: /Festa da escola/ })).toBeVisible();
  await page.getByRole("combobox", { name: "Mês", exact: true }).selectOption("10");
  await expect(page.getByRole("link", { name: /Festa da escola/ })).toBeVisible();
  await page.getByRole("button", { name: "Novo registro", exact: true }).click();
  await page.getByLabel("Título do evento").fill("Mostra cultural");
  await page.getByLabel("Data do evento").fill("2026-10-25");
  await page.getByLabel("Fotos do evento").setInputFiles([file, { ...file, name: "outra.png" }]);
  await page.getByRole("button", { name: "Salvar registro", exact: true }).click();
  await expect(page).toHaveURL(/\/registros\/event$/);
  await expect(page.getByRole("heading", { name: "Mostra cultural", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Ampliar foto 1 de Mostra cultural" }).click();
  await expect(page.getByRole("dialog")).toContainText("Foto 1 de 2");
  await page.keyboard.press("ArrowRight");
  await expect(page.getByRole("dialog")).toContainText("Foto 2 de 2");
  await page.getByRole("button", { name: "Fechar foto" }).click();
  await page.evaluate(axe.source);
  const results = await page.evaluate(async () => window.axe.run(document.querySelector("main")!, { runOnly: { type: "tag", values: ["wcag2a", "wcag2aa"] } }));
  expect(results.violations).toEqual([]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
  await page.screenshot({ path: info.outputPath("registros-gerais.png"), fullPage: true });
});

test("aceita cem fotos contando as existentes e bloqueia cento e uma", async ({ page }) => {
  await setup(page);
  await page.goto("/registros/event");
  await page.getByRole("button", { name: "Editar registro", exact: true }).click();
  const input = page.getByLabel("Fotos do evento");
  await input.setInputFiles(Array.from({ length: 97 }, (_, i) => ({ ...file, name: `${i}.png` })));
  await expect(page.getByRole("button", { name: /^Remover nova foto / })).toHaveCount(97);
  await input.setInputFiles(file);
  await expect(page.getByRole("alert")).toContainText("100 fotos");
  await page.getByRole("button", { name: "Remover foto 1", exact: true }).click();
  await input.setInputFiles(file);
  await expect(page.getByRole("button", { name: /^Remover nova foto / })).toHaveCount(98);
  await page.getByRole("button", { name: "Salvar registro", exact: true }).click();
  await expect(page.getByRole("button", { name: /^Ampliar foto / })).toHaveCount(100);
});

test("falha e conflito preservam título e arquivos antes de revisão", async ({ page }) => {
  const state = await setup(page);
  await page.goto("/registros/event");
  await page.getByRole("button", { name: "Editar registro", exact: true }).click();
  await page.getByLabel("Título do evento").fill("Meu evento");
  await page.getByLabel("Fotos do evento").setInputFiles(file);
  state.fail(503);
  await page.getByRole("button", { name: "Salvar registro", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Falha no envio");
  await expect(page.getByLabel("Título do evento")).toHaveValue("Meu evento");
  state.fail(409);
  await page.getByRole("button", { name: "Salvar registro", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Este registro foi alterado" })).toBeVisible();
  await expect(page.getByRole("button", { name: /^Remover nova foto / })).toHaveCount(1);
  await page.getByRole("button", { name: "Revisar minhas alterações sobre a versão atual" }).click();
  await page.getByRole("button", { name: "Salvar registro", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Meu evento", exact: true })).toBeVisible();
});

test("colega consulta sem editar, AGENT não acessa e exclusão confirma e volta à lista", async ({ page }) => {
  await setup(page, ["REQUESTER"], false);
  await page.goto("/registros/event");
  await expect(page.getByRole("button", { name: /Editar registro|Excluir registro/ })).toHaveCount(0);
  await setup(page, ["AGENT"]);
  await page.goto("/registros");
  await expect(page).toHaveURL(/\/tickets$/);
  await expect(page.getByRole("link", { name: "Registros", exact: true })).toHaveCount(0);
  await setup(page, ["ADMIN"]);
  await page.goto("/registros/event");
  await page.getByRole("button", { name: "Excluir registro", exact: true }).click();
  await page.getByRole("button", { name: "Cancelar", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Festa da escola" })).toBeVisible();
  await page.getByRole("button", { name: "Excluir registro", exact: true }).click();
  await page.getByRole("button", { name: "Confirmar exclusão", exact: true }).click();
  await expect(page).toHaveURL(/\/registros$/);
  await expect(page.getByRole("heading", { name: "Nenhum registro neste período" })).toBeVisible();
});
