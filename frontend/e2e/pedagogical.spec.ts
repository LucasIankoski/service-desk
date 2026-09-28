import { expect, test, type Page } from "@playwright/test";
import axe from "axe-core";
import type { Planning, SchoolClass } from "../src/api/pedagogical";

async function setup(page: Page, roles = ["MANAGER"]) {
  const teachers = [{ id: "a", displayName: "Cristine" }, { id: "b", displayName: "Magali" }];
  let classroom: SchoolClass = { id: "class", name: "BII", archived: false, teachers, version: 0 };
  let plan: Planning = {
    id: "plan", classId: "class", className: "BII", archived: false, weekStart: "2026-09-28", weekEnd: "2026-10-02",
    theme: "", status: "DRAFT", teachers, version: 0, createdAt: "2026-09-01T12:00:00Z", updatedAt: "2026-09-01T12:00:00Z",
    days: ["2026-09-28","2026-09-29","2026-09-30","2026-10-01","2026-10-02"].map(date => ({ date, noClass: false, reason: "", proposal: "", objectives: "", development: "", resources: "" }))
  };
  let conflict = false;
  await page.route("**/api/v1/**", async route => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    const method = route.request().method();
    const json = (body: unknown, status = 200) => route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });
    if (path.endsWith("/auth/me")) return json({ id: "a", displayName: "Cristine", roles, passwordChangeRequired: false });
    if (path.endsWith("/auth/csrf")) return json({ token: "test" });
    if (path.endsWith("/public/settings")) return json({ institutionName: "Escola Amor e Graça", timezoneName: "America/Sao_Paulo" });
    if (path.includes("/notifications")) return json(path.endsWith("/unread-count") ? { count: 0 } : { content: [] });
    if (path.endsWith("/teachers")) return json(teachers);
    if (path.endsWith("/classes")) {
      if (method === "POST") { classroom = { ...classroom, ...route.request().postDataJSON() }; return json(classroom, 201); }
      return json([classroom]);
    }
    if (path.endsWith("/classes/class")) {
      if (method === "PUT") classroom = { ...classroom, ...route.request().postDataJSON(), version: classroom.version + 1 };
      return json(classroom);
    }
    if (path.endsWith("/classes/class/plans")) return json(method === "POST" ? plan : [plan], method === "POST" ? 201 : 200);
    if (path.endsWith("/plans/plan/finalize")) { plan.status = "FINALIZED"; plan.version++; return json(plan); }
    if (path.endsWith("/plans/plan/reopen")) { plan.status = "DRAFT"; plan.version++; return json(plan); }
    if (path.endsWith("/plans/plan")) {
      if (method === "PUT") {
        if (conflict) { conflict = false; plan.theme = "Texto da colega"; plan.version++; return json({ detail: "Este registro mudou." }, 409); }
        const input = route.request().postDataJSON();
        plan = { ...plan, ...input, teachers: teachers.filter(t => input.teacherIds.includes(t.id)), version: plan.version + 1 };
      }
      return json(plan);
    }
    return json({});
  });
  return { conflict: () => { conflict = true; } };
}

test("cadastro, diretórios, preenchimento, finalização e impressão", async ({ page }, info) => {
  await setup(page);
  await page.goto("/pedagogico");
  await page.getByRole("button", { name: "Nova turma" }).click();
  await page.getByLabel("Nome da turma").fill("BII");
  await page.getByRole("checkbox", { name: "Cristine", exact: true }).check();
  await page.getByRole("checkbox", { name: "Magali", exact: true }).check();
  await page.getByRole("button", { name: "Salvar turma" }).click();
  await page.getByRole("link", { name: "BII", exact: true }).click();
  await page.getByLabel("Ano", { exact: true }).selectOption("2026");
  await page.getByLabel("Mês", { exact: true }).selectOption("10");
  await expect(page.getByRole("link", { name: /28\/09\/2026 a 02\/10\/2026/ })).toBeVisible();
  await page.getByRole("link", { name: /28\/09\/2026 a 02\/10\/2026/ }).click();
  await page.getByLabel("Tema / Projeto / Experiência da semana", { exact: true }).fill("Brincar e descobrir");
  const first = page.getByRole("group", { name: "segunda-feira · 28/09/2026", exact: true });
  await first.getByLabel("Proposta", { exact: true }).fill("Pintura dos chapéus");
  await first.getByLabel("Objetivos", { exact: true }).fill("Experimentar cores");
  await first.getByLabel("Desenvolvimento", { exact: true }).fill("Oferecer materiais e explorar juntos.");
  await first.getByLabel("Recursos", { exact: true }).fill("Tintas, papel e pincéis");
  for (const name of ["terça-feira · 29/09/2026", "quarta-feira · 30/09/2026", "quinta-feira · 01/10/2026", "sexta-feira · 02/10/2026"]) {
    const day = page.getByRole("group", { name, exact: true });
    await day.getByLabel("Sem aula", { exact: true }).check();
    await day.getByLabel("Motivo").fill("Recesso escolar");
  }
  await page.getByRole("button", { name: "Salvar rascunho" }).click();
  await expect(page.getByRole("status")).toHaveText("Rascunho salvo.");
  await page.getByRole("button", { name: "Finalizar", exact: true }).click();
  await expect(page.getByRole("status")).toHaveText("Planejamento finalizado.");
  await page.getByRole("button", { name: "Visualizar / Imprimir" }).click();
  await expect(page.getByRole("table")).toBeVisible();
  await expect(page.getByRole("table")).toContainText("Pintura dos chapéus");
  await page.screenshot({ path: info.outputPath("planejamento.png"), fullPage: true });
  await page.emulateMedia({ media: "print" });
  await expect(page.getByRole("table")).toBeVisible();
  await expect(page.getByRole("complementary", { name: "Navegação principal" })).toBeHidden();
  if (info.project.name === "chromium-desktop") await page.pdf({ path: info.outputPath("planejamento.pdf"), preferCSSPageSize: true });
  await page.emulateMedia({ media: "screen" });
  await page.getByRole("button", { name: "Reabrir", exact: true }).click();
  await expect(page.getByRole("button", { name: "Salvar rascunho" })).toBeVisible();
});

test("conflito preserva texto e navegação pede descarte", async ({ page }) => {
  const state = await setup(page);
  await page.goto("/pedagogico/planejamentos/plan");
  await page.getByLabel("Tema / Projeto / Experiência da semana", { exact: true }).fill("Meu texto");
  state.conflict();
  await page.getByRole("button", { name: "Salvar rascunho" }).click();
  await expect(page.getByRole("heading", { name: "Outra pessoa alterou este planejamento" })).toBeVisible();
  await expect(page.getByLabel("Tema / Projeto / Experiência da semana", { exact: true })).toHaveValue("Meu texto");
  await page.getByRole("link", { name: "Turmas", exact: true }).click();
  await expect(page.getByText("Existem alterações não salvas.", { exact: false })).toBeVisible();
  await page.getByRole("button", { name: "Continuar editando" }).click();
  await expect(page.getByLabel("Tema / Projeto / Experiência da semana", { exact: true })).toHaveValue("Meu texto");
});

test("professora não gerencia turmas e editor é acessível", async ({ page }) => {
  await setup(page, ["REQUESTER"]);
  await page.goto("/pedagogico");
  await expect(page.getByRole("link", { name: "BII", exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Nova turma" })).toHaveCount(0);
  await page.goto("/pedagogico/planejamentos/plan");
  await expect(page.getByLabel("Tema / Projeto / Experiência da semana", { exact: true })).toBeVisible();
  await page.evaluate(axe.source);
  const results = await page.evaluate(async () => window.axe.run(document.querySelector("main")!, { runOnly: { type: "tag", values: ["wcag2a", "wcag2aa"] } }));
  expect(results.violations).toEqual([]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
});

test("prévia longa em tablet e impressão sem corte de conteúdo", async ({ page }, info) => {
  await setup(page);
  await page.setViewportSize({ width: 768, height: 1024 });
  await page.goto("/pedagogico/planejamentos/plan");
  const first = page.getByRole("group", { name: "segunda-feira · 28/09/2026", exact: true });
  await first.getByLabel("Desenvolvimento", { exact: true }).fill("Experiência com cores e materiais. ".repeat(200) + "FIM DO CONTEÚDO");
  await page.getByRole("button", { name: "Visualizar / Imprimir" }).click();
  await expect(page.getByText("RASCUNHO · ALTERAÇÕES NÃO SALVAS", { exact: true })).toBeVisible();
  await page.emulateMedia({ media: "print" });
  await expect(page.getByRole("table")).toContainText("FIM DO CONTEÚDO");
  await expect(page.getByRole("banner")).toBeHidden();
  if (info.project.name === "chromium-desktop") await page.pdf({ path: info.outputPath("planejamento-longo.pdf"), preferCSSPageSize: true });
});
