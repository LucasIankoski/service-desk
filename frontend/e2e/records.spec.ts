import { expect, test, type Page } from "@playwright/test";
import axe from "axe-core";
import type { ActivityRecord } from "../src/api/records";
const png = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a3ioAAAAASUVORK5CYII=", "base64");
const file = { name: "proposta.png", mimeType: "image/png", buffer: png };
async function setup(page: Page, canEdit = true, archived = false) {
  let record: ActivityRecord = { id: "album", classId: "class", className: "BII", archived, title: "Descobrindo as cores", activityDate: "2026-09-27", authorId: "a", authorName: "Cristine", version: 0, canEdit: canEdit && !archived, createdAt: "2026-09-27T12:00:00Z", updatedAt: "2026-09-27T12:00:00Z", attachmentLimitMb: 5,
    photos: [1, 2, 3].map(i => ({ id: String(i), url: `/api/v1/pedagogical/records/album/photos/${i}`, thumbnailUrl: `/api/v1/pedagogical/records/album/photos/${i}/thumbnail`, width: i === 2 ? 400 : 800, height: i === 2 ? 800 : 400 })) };
  let failure = 0, removed = false;
  await page.route("**/api/v1/**", async route => {
    const url = new URL(route.request().url()), path = url.pathname, method = route.request().method();
    const json = (body: unknown, status = 200) => route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });
    if (path.endsWith("/auth/me")) return json({ id: "a", displayName: "Cristine", roles: ["REQUESTER"], passwordChangeRequired: false });
    if (path.endsWith("/auth/csrf")) return json({ token: "test" });
    if (path.endsWith("/public/settings")) return json({ institutionName: "Escola Amor e Graça", timezoneName: "America/Sao_Paulo" });
    if (path.includes("/notifications")) return json(path.endsWith("/unread-count") ? { count: 0 } : { content: [] });
    if (path.includes("/photos/")) return route.fulfill({ contentType: "image/svg+xml", body: `<svg xmlns="http://www.w3.org/2000/svg" width="800" height="500"><rect width="800" height="500" fill="#e4efe8"/><circle cx="230" cy="260" r="150" fill="#e9b653"/><circle cx="490" cy="220" r="130" fill="#659ba8"/><path d="M450 400L700 100L780 450Z" fill="#b36978"/></svg>` });
    if (path.endsWith("/classes/class")) return json({ id: "class", name: "BII", archived, teachers: [], version: 0 });
    if (path.endsWith("/classes/class/records") && method === "GET") return json({ items: removed ? [] : [{ ...record, cover: record.photos[0], photoCount: record.photos.length }], page: 0, totalPages: 1, totalElements: removed ? 0 : 1, attachmentLimitMb: 5 });
    if (path.endsWith("/records/album") && method === "DELETE") { removed = true; return route.fulfill({ status: 204 }); }
    if (path.endsWith("/records/album") && method === "GET") return json(record);
    if (method === "POST" || method === "PUT") {
      if (failure) { const status = failure; failure = 0; if (status === 409) record = { ...record, title: "Título da administração", version: record.version + 1 }; return json({ detail: status === 409 ? "Este registro mudou." : "Falha no envio. Tente novamente." }, status); }
      const raw = route.request().postDataBuffer()!.toString();
      // WebKit's protocol omits Blob bytes from intercepted multipart bodies.
      const part = raw.match(/name="metadata"[^]*?\r\n\r\n([^]*?)\r\n--/)?.[1];
      const metadata = part ? JSON.parse(part) : { title: await page.getByLabel("Título da proposta").inputValue(), activityDate: await page.getByLabel("Data da proposta").inputValue() };
      record = { ...record, title: metadata.title, activityDate: metadata.activityDate, version: record.version + 1 };
      return json(record, method === "POST" ? 201 : 200);
    }
    return json({});
  });
  return { fail: (status: number) => { failure = status; } };
}

test("cadastro com prévias, galeria acessível e navegação ampliada", async ({ page }, info) => {
  await setup(page);
  await page.goto("/pedagogico/turmas/class/registros");
  await page.getByRole("button", { name: "Novo registro" }).click();
  await page.getByLabel("Título da proposta").fill("Momentos de pintura");
  await page.getByLabel("Data da proposta").fill("2026-09-27");
  await page.getByLabel("Fotos da proposta").setInputFiles([file, { ...file, name: "outra.png" }]);
  await expect(page.getByRole("img", { name: "Prévia", exact: false })).toHaveCount(2);
  await page.getByRole("button", { name: "Remover nova foto 2" }).click();
  await page.getByRole("button", { name: "Salvar registro" }).click();
  await expect(page.getByRole("heading", { name: "Momentos de pintura" })).toBeVisible();
  await expect(page.getByRole("button", { name: /Ampliar foto/ })).toHaveCount(3);
  await page.screenshot({ path: info.outputPath("galeria-registros.png"), fullPage: true });
  await page.addScriptTag({ content: axe.source });
  expect(await page.evaluate(async () => (await window.axe.run()).violations)).toEqual([]);
  await page.getByRole("button", { name: /Ampliar foto 1/ }).click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.getByRole("button", { name: "Próxima foto" }).click();
  await expect(page.getByRole("dialog")).toContainText("Foto 2 de 3");
  await page.keyboard.press("ArrowRight");
  await expect(page.getByRole("dialog")).toContainText("Foto 3 de 3");
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).toBeHidden();
  await expect(page.getByRole("button", { name: /Ampliar foto 1/ })).toBeFocused();
});

test("falha e conflito preservam título e arquivos para revisão", async ({ page }) => {
  const api = await setup(page);
  await page.goto("/pedagogico/registros/album");
  await page.getByRole("button", { name: "Editar registro" }).click();
  await page.getByLabel("Título da proposta").fill("Meu título local");
  await page.getByLabel("Fotos da proposta").setInputFiles(file);
  api.fail(500);
  await page.getByRole("button", { name: "Salvar registro" }).click();
  await expect(page.getByRole("alert")).toContainText("Falha no envio");
  await expect(page.getByLabel("Título da proposta")).toHaveValue("Meu título local");
  await expect(page.getByRole("img", { name: /Prévia/ })).toBeVisible();
  api.fail(409);
  await page.getByRole("button", { name: "Salvar registro" }).click();
  await expect(page.getByText(/Versão atual: Título da administração/)).toBeVisible();
  await page.getByRole("button", { name: "Revisar minhas alterações sobre a versão atual" }).click();
  await expect(page.getByLabel("Título da proposta")).toHaveValue("Meu título local");
  await page.getByRole("button", { name: "Salvar registro" }).click();
  await expect(page.getByRole("heading", { name: "Meu título local" })).toBeVisible();
});

test("aviso de saída, limites de fotos e exclusão confirmada", async ({ page }) => {
  await setup(page);
  await page.goto("/pedagogico/registros/album");
  await page.getByRole("button", { name: "Editar registro" }).click();
  await page.getByLabel("Título da proposta").fill("Não perder");
  await page.getByRole("link", { name: "Registros", exact: true }).click();
  await expect(page.getByText("Existem alterações não salvas.")).toBeVisible();
  await page.getByRole("button", { name: "Continuar editando" }).click();
  await page.getByLabel("Fotos da proposta").setInputFiles({ ...file, name: "foto.heic" });
  await expect(page.getByRole("alert")).toContainText("JPG, PNG ou WebP");
  await page.getByLabel("Fotos da proposta").setInputFiles(Array.from({ length: 48 }, (_, i) => ({ ...file, name: `${i}.png` })));
  await expect(page.getByRole("alert")).toContainText("50 fotos");
  await page.getByRole("link", { name: "Registros", exact: true }).click();
  await page.getByRole("button", { name: "Descartar e sair" }).click();
  await page.goto("/pedagogico/registros/album");
  await page.getByRole("button", { name: "Excluir registro" }).click();
  await page.getByRole("button", { name: "Confirmar exclusão" }).click();
  await expect(page.getByRole("heading", { name: "Nenhum registro neste período" })).toBeVisible();
});

test("colega consulta sem editar e turma arquivada bloqueia cadastro", async ({ page }) => {
  await setup(page, false, true);
  await page.goto("/pedagogico/registros/album");
  await expect(page.getByRole("button", { name: "Editar registro" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Excluir registro" })).toHaveCount(0);
  await page.getByRole("link", { name: "Registros", exact: true }).click();
  await expect(page.getByRole("button", { name: "Novo registro" })).toHaveCount(0);
});

test("aceita cinquenta fotos contando as já salvas e bloqueia a próxima", async ({ page }) => {
  await setup(page);
  await page.goto("/pedagogico/registros/album");
  await page.getByRole("button", { name: "Editar registro" }).click();
  const input = page.getByLabel("Fotos da proposta");
  await input.setInputFiles(Array.from({ length: 47 }, (_, i) => ({ ...file, name: `${i}.png` })));
  await expect(page.getByRole("button", { name: /^Remover nova foto / })).toHaveCount(47);
  await expect(page.getByRole("alert")).toHaveCount(0);
  await input.setInputFiles(file);
  await expect(page.getByRole("alert")).toContainText("50 fotos");
  await expect(page.getByRole("button", { name: /^Remover nova foto / })).toHaveCount(47);
  await page.getByRole("button", { name: "Remover foto 1", exact: true }).click();
  await input.setInputFiles(file);
  await expect(page.getByRole("button", { name: /^Remover nova foto / })).toHaveCount(48);
  await expect(page.getByRole("alert")).toHaveCount(0);
  await page.getByRole("button", { name: "Salvar registro", exact: true }).click();
  await expect(page.getByRole("button", { name: "Editar registro", exact: true })).toBeVisible();
});
