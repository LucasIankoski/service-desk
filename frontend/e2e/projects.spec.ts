import { expect, test, type Page } from "@playwright/test";
import { readFileSync } from "node:fs";
import axe from "axe-core";
import { projectReference } from "./fixtures/project-reference";
import { plainRichText, projectInput, type Project } from "../src/api/projects";

async function setup(page: Page, overrides: Partial<Project> = {}, roles = ["MANAGER"]) {
  let project = structuredClone({ ...projectReference, ...overrides });
  let conflict = false, failure = false;
  const logo = `data:image/jpeg;base64,${readFileSync(new URL("./fixtures/project-reference-logo.jpg", import.meta.url)).toString("base64")}`;
  let deleted = false;
  await page.route("**/api/v1/**", async route => {
    const path = new URL(route.request().url()).pathname, method = route.request().method();
    const json = (body: unknown, status = 200) => route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });
    if (path.endsWith("/auth/me")) return json({ id: "a", displayName: "Cristine", roles, passwordChangeRequired: false });
    if (path.endsWith("/auth/csrf")) return json({ token: "test" });
    if (path.endsWith("/public/settings")) return json({ institutionName: "Escola Amor e Graça", schoolLogoUrl: logo, timezoneName: "America/Sao_Paulo" });
    if (path.includes("/notifications")) return json(path.endsWith("/unread-count") ? { count: 0 } : { content: [] });
    if (path.endsWith("/classes/class")) return json({ id: "class", name: "BII", archived: project.archived, teachers: project.teachers, version: 0 });
    if (path.endsWith("/classes/class/projects")) {
      if (method === "POST") project = { ...project, ...route.request().postDataJSON(), status: "DRAFT" };
      return json(method === "POST" ? project : deleted ? [] : [project], method === "POST" ? 201 : 200);
    }
    if (path.endsWith("/projects/project/finalize")) { project.status = "FINALIZED"; project.version++; return json(project); }
    if (path.endsWith("/projects/project/reopen")) { project.status = "DRAFT"; project.version++; return json(project); }
    if (path.endsWith("/projects/project")) {
      if (method === "DELETE") {
        if (conflict) { conflict = false; project.version++; return json({ detail: "Este registro mudou." }, 409); }
        expect(new URL(route.request().url()).searchParams.get("version")).toBe(String(project.version));
        deleted = true; return route.fulfill({ status: 204 });
      }
      if (deleted) return json({ detail: "Registro não encontrado." }, 404);
      if (method === "PUT") {
        if (failure) { failure = false; return json({ detail: "Falha temporária ao salvar." }, 500); }
        if (conflict) { conflict = false; project.title = "Texto da colega"; project.version++; return json({ detail: "Este registro mudou." }, 409); }
        const input = route.request().postDataJSON() as ReturnType<typeof projectInput>;
        project = { ...project, ...input, version: project.version + 1 };
        project.activities.sort((a, b) => (a.date ?? "9999").localeCompare(b.date ?? "9999"));
      }
      return json(project);
    }
    return json({});
  });
  return { conflict: (finalized = false) => { conflict = true; if (finalized) project.status = "FINALIZED"; }, fail: () => { failure = true; } };
}
test("projeto por turma, meses intermediários, edição e finalização", async ({ page }) => {
  await setup(page, { startDate: "2026-09-01", endDate: "2027-02-01" });
  await page.goto("/pedagogico/turmas/class/projetos");
  await page.getByRole("combobox", { name: "Ano", exact: true }).selectOption("2026");
  await page.getByRole("combobox", { name: "Mês", exact: true }).selectOption("12");
  await expect(page.getByRole("link", { name: /Amor e Graça no País/ })).toBeVisible();
  await page.getByLabel("Título", { exact: true }).fill("Projeto da turma");
  await page.getByLabel("Data inicial", { exact: true }).fill("2026-10-01");
  await page.getByLabel("Data final", { exact: true }).fill("2026-10-09");
  await page.getByRole("button", { name: "Criar projeto" }).click();
  await page.getByLabel("Faixa etária").fill("Crianças de 2 a 3 anos");
  await page.getByLabel("Objetivo geral", { exact: true }).fill("Proporcionar novas descobertas e experiências.");
  await page.getByLabel("Objetivos específicos", { exact: true }).fill("Explorar materiais\nParticipar das experiências\n");
  await page.getByLabel("Desenvolvimento", { exact: true }).fill("Brincar com materiais\n\nExplorar cores e texturas.");
  await expect(page.getByRole("button", { name: /Adicionar bloco|Negrito na seleção|Adicionar atividade/ })).toHaveCount(0);
  await expect(page.locator('input[type="date"]')).toHaveCount(2);
  await page.getByRole("button", { name: "Salvar rascunho", exact: true }).click();
  await expect(page.getByRole("status")).toHaveText("Rascunho salvo.");
  await page.reload();
  await expect(page.getByLabel("Desenvolvimento", { exact: true })).toHaveValue("Brincar com materiais\n\nExplorar cores e texturas.");
  await page.getByRole("button", { name: "Finalizar", exact: true }).click();
  await expect(page.getByRole("status")).toHaveText("Projeto finalizado.");
  await page.getByRole("button", { name: "Reabrir", exact: true }).click();
  await expect(page.getByLabel("Título", { exact: true })).toBeEnabled();
});
test("falhas e conflito preservam texto; navegação confirma descarte", async ({ page }) => {
  const state = await setup(page, { status: "DRAFT" });
  await page.goto("/pedagogico/projetos/project");
  await page.getByLabel("Título", { exact: true }).fill("Meu projeto");
  state.fail(); await page.getByRole("button", { name: "Salvar rascunho" }).click();
  await expect(page.getByRole("alert")).toContainText("Falha temporária");
  await expect(page.getByLabel("Título", { exact: true })).toHaveValue("Meu projeto");
  state.conflict(); await page.getByRole("button", { name: "Salvar rascunho" }).click();
  await expect(page.getByRole("heading", { name: "Outra pessoa alterou este projeto" })).toBeVisible();
  await expect(page.getByLabel("Título", { exact: true })).toHaveValue("Meu projeto");
  await page.getByRole("button", { name: "Manter meu texto para revisão" }).click();
  await page.getByRole("link", { name: "Projetos", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Existem alterações não salvas");
  await page.getByRole("button", { name: "Continuar editando" }).click();
  await page.getByRole("button", { name: "Salvar rascunho" }).click();
  await expect(page.getByRole("status")).toHaveText("Rascunho salvo.");
});
test("documento de referência e impressão A4 vertical", async ({ page }, info) => {
  await setup(page);
  await page.goto("/pedagogico/projetos/project");
  await page.getByRole("button", { name: "Visualizar / Imprimir" }).click();
  const doc = page.locator("[data-project-document]");
  await expect(doc).toContainText("CONCLUSÃO:");
  await expect(doc.getByRole("img")).toBeVisible();
  await page.emulateMedia({ media: "print" });
  await expect(page.getByRole("complementary", { name: "Navegação principal" })).toBeHidden();
  await expect(page.getByRole("banner")).toBeHidden();
  await expect(page.getByRole("button", { name: "Reabrir" })).toBeHidden();
  if (info.project.name === "chromium-desktop") await page.pdf({ path: info.outputPath("projeto-referencia.pdf"), preferCSSPageSize: true });
});
test("objetivo geral mantém o título inteiro com texto sem espaços", async ({ page }, info) => {
  await setup(page, { generalObjective: plainRichText("Descobertas".repeat(40)) });
  await page.goto("/pedagogico/projetos/project");
  await page.getByRole("button", { name: "Visualizar / Imprimir" }).click();
  const heading = page.locator("[data-project-document]").getByRole("heading", { name: "OBJETIVO GERAL:", exact: true });
  for (const media of ["screen", "print"] as const) {
    await page.emulateMedia({ media });
    const layout = await heading.evaluate(element => {
      const range = document.createRange();
      range.selectNodeContents(element);
      const titleLines = [...range.getClientRects()].map(rect => Math.round(rect.top));
      const section = element.parentElement!;
      const bounds = section.getBoundingClientRect();
      range.selectNodeContents(section.querySelector("p")!);
      const contentFits = [...range.getClientRects()].every(rect => rect.right <= bounds.right + 1);
      return { titleLines: [...new Set(titleLines)], contentFits };
    });
    expect(layout.titleLines).toHaveLength(1);
    expect(layout.contentFits).toBe(true);
    if (info.project.name === "chromium-desktop") await heading.locator("..").screenshot({ path: info.outputPath(`objetivo-geral-${media}.png`) });
  }
});
test("reabrir após conflito com finalização preserva o texto local", async ({ page }) => {
  const state = await setup(page, { status: "DRAFT" });
  await page.goto("/pedagogico/projetos/project");
  await page.getByLabel("Título", { exact: true }).fill("Meu texto preservado");
  state.conflict(true);
  await page.getByRole("button", { name: "Salvar rascunho" }).click();
  await page.getByRole("button", { name: "Manter meu texto para revisão" }).click();
  await page.getByRole("button", { name: "Reabrir", exact: true }).click();
  await expect(page.getByLabel("Título", { exact: true })).toHaveValue("Meu texto preservado");
  await expect(page.getByRole("status")).toContainText("ainda precisa ser salvo");
  await page.getByRole("button", { name: "Salvar rascunho" }).click();
  await page.reload();
  await expect(page.getByLabel("Título", { exact: true })).toHaveValue("Meu texto preservado");
});
test("acessibilidade, celular e documento longo", async ({ page }, info) => {
  await setup(page, { status: "DRAFT" });
  await page.goto("/pedagogico/projetos/project");
  await expect(page.getByLabel("Título", { exact: true })).toBeVisible();
  await page.evaluate(axe.source);
  const result = await page.evaluate(() => window.axe.run(document.querySelector("main")!, { runOnly: { type: "tag", values: ["wcag2a", "wcag2aa"] } }));
  expect(result.violations).toEqual([]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: info.outputPath("formulario-projeto.png"), fullPage: true });
  await page.getByLabel("Desenvolvimento", { exact: true }).fill("Experiência com cores e materiais. ".repeat(230) + "FIM DA ATIVIDADE LONGA");
  await page.getByRole("button", { name: "Visualizar / Imprimir" }).click();
  await expect(page.getByText("RASCUNHO · ALTERAÇÕES NÃO SALVAS", { exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.emulateMedia({ media: "print" });
  await expect(page.locator("[data-project-document]")).toContainText("FIM DA ATIVIDADE LONGA");
  if (info.project.name === "chromium-desktop") await page.pdf({ path: info.outputPath("projeto-longo.pdf"), preferCSSPageSize: true });
});
test("turma arquivada mantém consulta e bloqueia formulário", async ({ page }) => {
  await setup(page, { archived: true, status: "DRAFT" });
  await page.goto("/pedagogico/turmas/class/projetos");
  await expect(page.getByRole("button", { name: "Criar projeto" })).toHaveCount(0);
  await page.getByRole("link", { name: /Amor e Graça no País/ }).click();
  await expect(page.getByLabel("Título", { exact: true })).toBeDisabled();
  await expect(page.getByRole("button", { name: "Salvar rascunho" })).toHaveCount(0);
  await page.getByRole("button", { name: "Visualizar / Imprimir" }).click();
  await expect(page.getByRole("button", { name: "Imprimir / Salvar PDF" })).toBeVisible();
});

 test("exclusão administrativa confirma descarte e volta à listagem", async ({ page }) => {
  await setup(page, { status: "DRAFT" });
  await page.goto("/pedagogico/projetos/project");
  await page.getByLabel("Título", { exact: true }).fill("Texto não salvo");
  await page.getByRole("button", { name: "Excluir projeto", exact: true }).click();
  await expect(page.getByRole("dialog")).toContainText("alterações não salvas também serão descartadas");
  await page.getByRole("button", { name: "Cancelar", exact: true }).click();
  await expect(page.getByLabel("Título", { exact: true })).toHaveValue("Texto não salvo");
  await page.getByRole("button", { name: "Excluir projeto", exact: true }).click();
  await page.getByRole("button", { name: "Confirmar exclusão", exact: true }).click();
  await expect(page).toHaveURL("/pedagogico/turmas/class/projetos");
  await expect(page.getByText("Existem alterações não salvas.", { exact: false })).toHaveCount(0);
  await expect(page.locator('a[href="/pedagogico/projetos/project"]')).toHaveCount(0);
 });
 test("conflito na exclusão preserva preenchimento", async ({ page }) => {
  const state = await setup(page, { status: "DRAFT" });
  await page.goto("/pedagogico/projetos/project");
  await page.getByLabel("Título", { exact: true }).fill("Meu texto local");
  state.conflict();
  await page.getByRole("button", { name: "Excluir projeto", exact: true }).click();
  await page.getByRole("button", { name: "Confirmar exclusão", exact: true }).click();
  await expect(page.getByRole("dialog").getByRole("alert")).toContainText("Seu preenchimento foi preservado");
  await expect(page.getByRole("button", { name: "Confirmar exclusão", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Cancelar", exact: true }).click();
  await expect(page.getByLabel("Título", { exact: true })).toHaveValue("Meu texto local");
  await expect(page.getByRole("heading", { name: "Outra pessoa alterou este projeto" })).toBeVisible();
 });

test("professora não exclui projeto e administrativo exclui finalizado", async ({ page }) => {
  await setup(page, {}, ["REQUESTER"]);
  await page.goto("/pedagogico/projetos/project");
  await expect(page.getByRole("button", { name: "Reabrir", exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Excluir projeto", exact: true })).toHaveCount(0);
  await page.unrouteAll();
  await setup(page, {}, ["ADMIN"]);
  await page.reload();
  await page.getByRole("button", { name: "Excluir projeto", exact: true }).click();
  await page.getByRole("button", { name: "Confirmar exclusão", exact: true }).click();
  await expect(page).toHaveURL("/pedagogico/turmas/class/projetos");
});
