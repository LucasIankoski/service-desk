import { expect, test, type Page } from "@playwright/test";
import axe from "axe-core";
import type { AgendaItem, AgendaOccurrence } from "../src/api/types";

const manager = { id: "manager", displayName: "Administrativo" };
const colleague = { id: "colleague", displayName: "Tatiana" };
const seed: AgendaItem[] = [
  { id: "demand", kind: "INTERNAL_DEMAND", title: "Preparar documentos", description: "Imprimir para reunião", assigneeId: manager.id, assigneeName: manager.displayName, priority: "HIGH", status: "PENDING", startAt: "2026-09-05T03:00:00Z", endAt: "2026-09-06T03:00:00Z", allDay: true, version: 0, createdAt: "2026-09-01T03:00:00Z", updatedAt: "2026-09-01T03:00:00Z" },
  { id: "cross-month", kind: "INTERNAL_DEMAND", title: "Conferir materiais", description: null, priority: "LOW", status: "COMPLETED", startAt: "2026-08-31T03:00:00Z", endAt: "2026-09-02T03:00:00Z", allDay: true, version: 0, createdAt: "2026-08-01T03:00:00Z", updatedAt: "2026-08-01T03:00:00Z" },
  { id: "event", kind: "INSTITUTION_EVENT", title: "Evento público", priority: null, status: null, startAt: "2026-09-05T12:00:00Z", endAt: "2026-09-05T13:00:00Z", allDay: false, version: 0, createdAt: "2026-09-01T03:00:00Z", updatedAt: "2026-09-01T03:00:00Z" }
];

async function setup(page: Page, roles = ["MANAGER"]) {
  await page.clock.setFixedTime(new Date("2026-09-05T15:00:00Z"));
  let items = structuredClone(seed);
  let conflict = false;
  let occurrenceConflict = false;
  let notes: AgendaOccurrence[] = [{ id: "note", date: "2026-09-05", body: "Apresentação da equipe no período da manhã.", createdById: manager.id, createdByName: manager.displayName, version: 0, createdAt: "2026-09-05T12:00:00Z", updatedAt: "2026-09-05T12:00:00Z" }];
  await page.route("**/api/v1/**", async (route) => {
    const url = new URL(route.request().url());
    const method = route.request().method();
    const json = (data: unknown, status = 200) => route.fulfill({ status, contentType: "application/json", body: JSON.stringify(data) });
    if (url.pathname.endsWith("/public/settings")) return json({ institutionName: "Escola Modelo", timezoneName: "America/Sao_Paulo" });
    if (url.pathname.endsWith("/auth/me")) return json({ ...manager, email: "manager@example.test", roles, passwordChangeRequired: false });
    if (url.pathname.endsWith("/auth/csrf")) return json({ token: "test" });
    if (url.pathname.endsWith("/users/managers")) return json([manager, colleague]);
    if (url.pathname.includes("/agenda/occurrences")) {
      const id = url.pathname.split("/")[5];
      if (method === "GET") return json(notes.filter((note) => note.date >= url.searchParams.get("start")! && note.date < url.searchParams.get("end")!));
      if (method === "POST") {
        const note = { ...route.request().postDataJSON(), id: `note-${notes.length}`, createdById: manager.id, createdByName: manager.displayName, version: 0, createdAt: "2026-09-05T15:00:00Z", updatedAt: "2026-09-05T15:00:00Z" };
        notes.push(note);
        return json(note, 201);
      }
      const note = notes.find((candidate) => candidate.id === id)!;
      if (occurrenceConflict) {
        occurrenceConflict = false;
        note.version++;
        note.body = "Anotação atualizada por colega";
        return json({ detail: "Esta ocorrência mudou. Feche a edição e abra novamente." }, 409);
      }
      if (method === "DELETE") { notes = notes.filter((candidate) => candidate.id !== id); return route.fulfill({ status: 204 }); }
      const input = route.request().postDataJSON();
      if (input.version !== note.version) return json({ detail: "Esta ocorrência mudou." }, 409);
      Object.assign(note, input, { version: note.version + 1 });
      return json(note);
    }
    if (url.pathname.includes("/agenda/items")) {
      const id = url.pathname.split("/")[5];
      if (method === "GET") return json(items.filter((item) => item.startAt < url.searchParams.get("end")! && item.endAt > url.searchParams.get("start")!));
      if (method === "POST") {
        const input = route.request().postDataJSON();
        const item = { ...input, assignees: [manager, colleague].filter((person) => input.assigneeIds?.includes(person.id)), id: `created-${items.length}`, status: "PENDING", version: 0, assigneeName: input.assigneeId ? manager.displayName : null };
        items.push(item);
        return json(item, 201);
      }
      const item = items.find((candidate) => candidate.id === id)!;
      if (conflict) {
        conflict = false;
        item.version += 1;
        item.title = "Alterada por outro usuário";
        return json({ detail: "Este item da agenda mudou. Recarregue antes de salvar." }, 409);
      }
      if (method === "DELETE") { items = items.filter((candidate) => candidate.id !== id); return route.fulfill({ status: 204 }); }
      const input = route.request().postDataJSON();
      if (input.version !== item.version) return json({ detail: "Este item da agenda mudou. Recarregue antes de salvar." }, 409);
      Object.assign(item, input, { version: item.version + 1 });
      if (input.assigneeIds) item.assignees = [manager, colleague].filter((person) => input.assigneeIds.includes(person.id));
      return json(item);
    }
    if (url.pathname.endsWith("unread-count")) return json({ count: 0 });
    if (url.pathname.endsWith("categories") || url.pathname.endsWith("assignees")) return json([]);
    return json({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 });
  });
  return { items: () => items, notes: () => notes, conflict: () => { conflict = true; }, occurrenceConflict: () => { occurrenceConflict = true; } };
}

async function navigate(page: Page, label: string) {
  await page.getByRole("link", { name: label, exact: true }).filter({ visible: true }).click();
  await expect(page.getByRole("heading", { name: label, exact: true })).toBeVisible();
  if (label === "Tarefas") await page.getByRole("combobox", { name: "Visualização", exact: true }).selectOption("MONTH");
}

test("monthly tasks, filters, navigation and accessibility", async ({ page }, testInfo) => {
  await setup(page, ["MANAGER", "ADMIN"]);
  await page.goto("/tarefas");
  await page.getByRole("combobox", { name: "Visualização", exact: true }).selectOption("MONTH");
  await expect(page.getByRole("heading", { name: "Tarefas", exact: true })).toBeVisible();
  await expect(page.getByRole("status")).toHaveText("2 de 2 tarefas");
  await expect(page.getByText("Evento público")).toHaveCount(0);
  await expect(page.getByRole("region", { name: "Totais do mês" })).toContainText("2");
  await page.getByLabel("Buscar tarefa").fill("imprimir");
  await expect(page.getByRole("status")).toHaveText("1 de 2 tarefas");
  await expect(page.getByRole("region", { name: "Totais do mês" })).toContainText("2");
  await page.getByRole("button", { name: "Limpar filtros" }).click();
  await page.getByRole("combobox", { name: "Prioridade", exact: true }).selectOption("LOW");
  await expect(page.getByRole("button", { name: "Conferir materiais", exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Preparar documentos", exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "Limpar filtros" }).click();
  await page.getByRole("combobox", { name: "Responsável", exact: true }).selectOption("unassigned");
  await expect(page.getByRole("status")).toHaveText("1 de 2 tarefas");
  await page.getByRole("button", { name: "Limpar filtros" }).click();
  await page.getByRole("combobox", { name: "Status", exact: true }).selectOption("PENDING");
  await expect(page.getByRole("button", { name: "Preparar documentos", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Limpar filtros" }).click();
  await page.getByRole("button", { name: "Próximo mês" }).click();
  await expect(page.getByText("Nenhuma tarefa neste mês.", { exact: false })).toBeVisible();
  await page.getByRole("button", { name: "Nova tarefa" }).click();
  await expect(page.getByLabel("Data inicial")).toHaveValue("2026-10-01");
  await page.getByRole("button", { name: "Cancelar" }).click();
  await page.getByRole("button", { name: "Mês atual", exact: true }).click();
  await expect(page.getByRole("status")).toHaveText("2 de 2 tarefas");
  await page.getByRole("region", { name: "Tabela de tarefas" }).focus();
  await expect(page.getByRole("region", { name: "Tabela de tarefas" })).toBeFocused();
  await page.evaluate(() => window.scrollTo(0, 0));
  const occurrenceToggle = page.getByRole("button", { name: "Ocorrências do dia", exact: true });
  if (await occurrenceToggle.getAttribute("aria-expanded") === "false") await occurrenceToggle.click();
  await page.getByLabel("Data das ocorrências").fill("2026-09-05");
  await expect(page.getByText("Apresentação da equipe no período da manhã.")).toBeVisible();
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({ path: testInfo.outputPath("tasks.png"), fullPage: true });
  await page.addScriptTag({ content: axe.source });
  const results = await page.evaluate(async () => (window as unknown as { axe: typeof axe }).axe.run(document, { runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag22aa"] } }));
  expect(results.violations).toEqual([]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});

test("create in Tasks, edit and reopen in Agenda, delete in Tasks", async ({ page }) => {
  const store = await setup(page);
  await page.goto("/tarefas");
  await page.getByRole("combobox", { name: "Visualização", exact: true }).selectOption("MONTH");
  await page.getByRole("button", { name: "Nova tarefa" }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Título", { exact: true }).fill("Organizar reunião");
  await dialog.getByRole("combobox", { name: "Prioridade", exact: true }).selectOption("HIGH");
  await dialog.getByRole("checkbox", { name: "Administrativo", exact: true }).check();
  await dialog.getByRole("checkbox", { name: "Tatiana", exact: true }).check();
  await dialog.getByRole("button", { name: "Criar tarefa" }).click();
  await expect(dialog).toHaveCount(0);
  await page.getByRole("button", { name: "Concluir Organizar reunião" }).click();
  await expect(page.getByRole("button", { name: "Reabrir Organizar reunião" })).toBeVisible();
  await navigate(page, "Agenda");
  await page.getByText("Organizar reunião", { exact: true }).click();
  await expect(dialog).toContainText("Responsáveis: Administrativo, Tatiana");
  await dialog.getByRole("button", { name: "Reabrir", exact: true }).click();
  await expect(dialog.getByRole("button", { name: "Concluir", exact: true })).toBeVisible();
  await dialog.getByRole("button", { name: "Editar", exact: true }).click();
  await dialog.getByLabel("Título", { exact: true }).fill("Organizar reunião final");
  await dialog.getByRole("button", { name: "Salvar alterações" }).click();
  await expect(dialog).toHaveCount(0);
  await navigate(page, "Tarefas");
  await page.getByRole("button", { name: "Organizar reunião final", exact: true }).click();
  page.once("dialog", (confirmation) => confirmation.accept());
  await dialog.getByRole("button", { name: "Excluir", exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Organizar reunião final", exact: true })).toHaveCount(0);
  expect(store.items().filter((item) => item.id.startsWith("created"))).toHaveLength(0);
});

test("create in Agenda, edit in Tasks and delete in Agenda", async ({ page }) => {
  const store = await setup(page);
  await page.goto("/agenda");
  await page.getByRole("button", { name: "Novo item" }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByRole("radio", { name: "Demanda interna" }).check();
  await dialog.getByLabel("Título", { exact: true }).fill("Demanda da Agenda");
  await dialog.getByRole("button", { name: "Adicionar à agenda" }).click();
  await expect(dialog).toHaveCount(0);
  await navigate(page, "Tarefas");
  await page.getByRole("button", { name: "Editar Demanda da Agenda" }).click();
  await dialog.getByRole("combobox", { name: "Prioridade", exact: true }).selectOption("LOW");
  await dialog.getByLabel("Descrição").fill("Atualizada em Tarefas");
  await dialog.getByRole("button", { name: "Salvar alterações" }).click();
  await expect(dialog).toHaveCount(0);
  await navigate(page, "Agenda");
  await page.getByText("Demanda da Agenda", { exact: true }).click();
  await expect(dialog).toContainText("Prioridade: Baixa");
  await expect(dialog).toContainText("Atualizada em Tarefas");
  page.once("dialog", (confirmation) => confirmation.accept());
  await dialog.getByRole("button", { name: "Excluir", exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await navigate(page, "Tarefas");
  await expect(page.getByRole("button", { name: "Demanda da Agenda", exact: true })).toHaveCount(0);
  expect(store.items()).toHaveLength(seed.length);
});

test("conflicts refresh the list without replacing typed form content", async ({ page }) => {
  const store = await setup(page);
  await page.goto("/tarefas");
  await page.getByRole("combobox", { name: "Visualização", exact: true }).selectOption("MONTH");
  await page.getByRole("button", { name: "Editar Preparar documentos" }).click();
  await page.getByRole("dialog").getByLabel("Título", { exact: true }).fill("Minha edição");
  store.conflict();
  await page.getByRole("button", { name: "Salvar alterações" }).click();
  await expect(page.getByRole("alert")).toContainText("mudou");
  await expect(page.getByRole("dialog").getByLabel("Título", { exact: true })).toHaveValue("Minha edição");
  await page.getByRole("button", { name: "Cancelar" }).click();
  await expect(page.getByRole("button", { name: "Alterada por outro usuário", exact: true })).toBeVisible();
});

for (const role of ["REQUESTER", "AGENT", "ADMIN"]) {
  test(`${role} cannot open Tasks`, async ({ page }) => {
    await setup(page, [role]);
    await page.goto("/tarefas");
    await expect(page).toHaveURL(/\/tickets$/);
    await expect(page.getByRole("link", { name: "Tarefas", exact: true })).toHaveCount(0);
  });
}

test("daily occurrences can be created edited and deleted without a task", async ({ page }, testInfo) => {
  const store = await setup(page);
  await page.goto("/tarefas");
  await page.getByRole("combobox", { name: "Visualização", exact: true }).selectOption("MONTH");
  const toggle = page.getByRole("button", { name: "Ocorrências do dia", exact: true });
  if (await toggle.getAttribute("aria-expanded") === "false") await toggle.click();
  const panel = page.getByRole("complementary", { name: "Ocorrências do dia" });
  await panel.getByLabel("Data das ocorrências").fill("2026-09-08");
  await expect(panel.getByText("Nenhuma ocorrência neste dia.")).toBeVisible();
  await panel.getByRole("button", { name: "Nova ocorrência" }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog.getByRole("button", { name: "Salvar ocorrência" })).toBeDisabled();
  await dialog.getByRole("textbox", { name: "Anotação", exact: true }).fill("Reunião com as famílias\nPreparar sala para amanhã");
  await expect(dialog.getByRole("textbox", { name: "Anotação", exact: true })).toBeFocused();
  await page.screenshot({ path: testInfo.outputPath("occurrence-editor.png"), fullPage: true });
  await page.addScriptTag({ content: axe.source });
  const violations = await page.evaluate(async () => (window as unknown as { axe: typeof axe }).axe.run(document, { runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag22aa"] } }));
  expect(violations.violations).toEqual([]);
  await dialog.getByRole("button", { name: "Salvar ocorrência" }).click();
  await expect(dialog).toHaveCount(0);
  await expect(panel).toContainText("Reunião com as famílias");
  expect(store.items()).toHaveLength(seed.length);
  await page.reload();
  if (await toggle.getAttribute("aria-expanded") === "false") await toggle.click();
  await panel.getByRole("button", { name: "Ver ocorrências de 08/09/2026" }).click();
  await panel.getByRole("button", { name: "Editar ocorrência 1" }).click();
  await dialog.getByRole("textbox", { name: "Anotação", exact: true }).fill("Reunião adiada");
  await dialog.getByRole("button", { name: "Salvar ocorrência" }).click();
  await expect(dialog).toHaveCount(0);
  await expect(panel.getByText("Reunião adiada", { exact: true })).toBeVisible();
  await page.getByLabel("Buscar tarefa").fill("Sem resultado");
  await expect(panel.getByText("Reunião adiada", { exact: true })).toBeVisible();
  page.once("dialog", (confirmation) => confirmation.accept());
  await panel.getByRole("button", { name: "Excluir ocorrência 1" }).click();
  await expect(panel.getByText("Nenhuma ocorrência neste dia.")).toBeVisible();
  expect(store.notes().filter((note) => note.date === "2026-09-08")).toHaveLength(0);
});

test("occurrence conflict preserves draft and cancel never saves", async ({ page }) => {
  const store = await setup(page);
  await page.goto("/tarefas");
  await page.getByRole("combobox", { name: "Visualização", exact: true }).selectOption("MONTH");
  const toggle = page.getByRole("button", { name: "Ocorrências do dia", exact: true });
  if (await toggle.getAttribute("aria-expanded") === "false") await toggle.click();
  await page.getByLabel("Data das ocorrências").fill("2026-09-05");
  await page.getByRole("button", { name: "Editar ocorrência 1" }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByRole("textbox", { name: "Anotação", exact: true }).fill("Meu texto em edição");
  store.occurrenceConflict();
  await dialog.getByRole("button", { name: "Salvar ocorrência" }).click();
  await expect(dialog.getByRole("alert")).toContainText("mudou");
  await expect(dialog.getByRole("textbox", { name: "Anotação", exact: true })).toHaveValue("Meu texto em edição");
  await dialog.getByRole("button", { name: "Cancelar" }).click();
  await expect(dialog).toHaveCount(0);
  await expect(page.getByText("Anotação atualizada por colega", { exact: true })).toBeVisible();
  expect(store.notes()[0].body).toBe("Anotação atualizada por colega");
});

test("filter by any assignee and clear assignments in either view", async ({ page }, testInfo) => {
  await setup(page);
  await page.goto("/tarefas");
  await page.getByRole("combobox", { name: "Visualização", exact: true }).selectOption("MONTH");
  await page.getByRole("button", { name: "Editar Preparar documentos" }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog.getByRole("checkbox", { name: "Administrativo", exact: true })).toBeChecked();
  await dialog.getByLabel("Buscar responsável").fill("Tatiana");
  await dialog.getByRole("checkbox", { name: "Tatiana", exact: true }).check();
  await expect(dialog.getByText("2 selecionado(s): Administrativo, Tatiana", { exact: true })).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath("multiple-assignees.png"), fullPage: true });
  await dialog.getByRole("button", { name: "Salvar alterações" }).click();
  await expect(dialog).toHaveCount(0);
  await page.getByRole("combobox", { name: "Responsável", exact: true }).selectOption(colleague.id);
  await expect(page.getByRole("button", { name: "Preparar documentos", exact: true })).toBeVisible();
  await expect(page.getByRole("status")).toHaveText("1 de 2 tarefas");
  await navigate(page, "Agenda");
  await page.getByText("Preparar documentos", { exact: true }).click();
  await expect(dialog).toContainText("Responsáveis: Administrativo, Tatiana");
  await dialog.getByRole("button", { name: "Editar", exact: true }).click();
  await dialog.getByRole("checkbox", { name: "Administrativo", exact: true }).uncheck();
  await dialog.getByRole("checkbox", { name: "Tatiana", exact: true }).uncheck();
  await dialog.getByRole("button", { name: "Salvar alterações" }).click();
  await expect(dialog).toHaveCount(0);
  await navigate(page, "Tarefas");
  await page.getByRole("combobox", { name: "Responsável", exact: true }).selectOption("unassigned");
  await expect(page.getByRole("status")).toHaveText("2 de 2 tarefas");
});

for (const [shift, label] of [["MORNING", "Manhã"], ["AFTERNOON", "Tarde"], ["NIGHT", "Noite"]]) {
  test(`shift ${shift} saves without hours and retains dates when editing`, async ({ page }, testInfo) => {
    const store = await setup(page);
    await page.goto("/tarefas");
  await page.getByRole("combobox", { name: "Visualização", exact: true }).selectOption("MONTH");
    await page.getByRole("button", { name: "Nova tarefa" }).click();
    const dialog = page.getByRole("dialog");
    await dialog.getByLabel("Título", { exact: true }).fill(`Tarefa ${label}`);
    await dialog.getByLabel("Dia inteiro", { exact: true }).uncheck();
    await expect(dialog.getByRole("combobox", { name: "Período", exact: true })).toHaveValue("");
    await dialog.getByRole("combobox", { name: "Período", exact: true }).selectOption(shift);
    await dialog.getByLabel("Data inicial", { exact: true }).fill("2026-09-05");
    await dialog.getByLabel("Data final", { exact: true }).fill("2026-09-07");
    await expect(dialog.getByLabel("Início", { exact: true })).toHaveCount(0);
    await dialog.getByRole("combobox", { name: "Período", exact: true }).selectOption("CUSTOM");
    await dialog.getByLabel("Início", { exact: true }).fill("2026-09-05T10:15");
    await dialog.getByLabel("Término", { exact: true }).fill("2026-09-07T11:30");
    await dialog.getByRole("combobox", { name: "Período", exact: true }).selectOption(shift);
    await dialog.getByLabel("Dia inteiro", { exact: true }).check();
    await dialog.getByLabel("Dia inteiro", { exact: true }).uncheck();
    await expect(dialog.getByLabel("Data final", { exact: true })).toHaveValue("2026-09-07");
    await dialog.getByRole("combobox", { name: "Período", exact: true }).selectOption("CUSTOM");
    await expect(dialog.getByLabel("Início", { exact: true })).toHaveValue("2026-09-05T10:15");
    await expect(dialog.getByLabel("Término", { exact: true })).toHaveValue("2026-09-07T11:30");
    await dialog.getByRole("combobox", { name: "Período", exact: true }).selectOption(shift);
    await page.screenshot({ path: testInfo.outputPath(`shift-${shift}-editor.png`), fullPage: true });
    await dialog.getByRole("button", { name: "Criar tarefa" }).click();
    await expect(dialog).toHaveCount(0);
    expect(store.items().find((item) => item.title === `Tarefa ${label}`)?.shift).toBe(shift);
    await page.getByRole("button", { name: `Editar Tarefa ${label}`, exact: true }).click();
    await expect(dialog.getByRole("combobox", { name: "Período", exact: true })).toHaveValue(shift);
    await expect(dialog.getByLabel("Data final", { exact: true })).toHaveValue("2026-09-07");
    await dialog.getByRole("button", { name: "Cancelar" }).click();
    await navigate(page, "Agenda");
    // Use month view also on mobile, whose default is a list.
    await page.getByRole("tab", { name: "Visualizar o mês", exact: true }).click();
    await expect(page.getByText(`${label} · Tarefa ${label}`, { exact: true })).toHaveCount(3);
    await page.screenshot({ path: testInfo.outputPath(`shift-${shift}-month.png`), fullPage: true });
    await page.getByText(`${label} · Tarefa ${label}`, { exact: true }).last().click();
    await expect(dialog).toContainText(`05/09/2026 a 07/09/2026 · ${label}`);
    await dialog.getByRole("button", { name: "Concluir", exact: true }).click();
    await expect(dialog.getByRole("button", { name: "Reabrir", exact: true })).toBeVisible();
    await dialog.getByRole("button", { name: "Fechar", exact: true }).click();
    for (const view of ["semana", "dia", "lista"]) {
      await page.getByRole("tab", { name: `Visualizar o ${view}`, exact: true }).click();
      await expect(page.getByText(`${label} · Tarefa ${label}`, { exact: true }).first()).toBeVisible();
      await page.screenshot({ path: testInfo.outputPath(`shift-${shift}-${view}.png`), fullPage: true });
    }
    await page.getByText(`${label} · Tarefa ${label}`, { exact: true }).first().click();
    await dialog.getByRole("button", { name: "Editar", exact: true }).click();
    await dialog.getByRole("combobox", { name: "Período", exact: true }).selectOption("CUSTOM");
    await dialog.getByRole("button", { name: "Salvar alterações" }).click();
    await expect(dialog).toHaveCount(0);
    expect(store.items().find((item) => item.title === `Tarefa ${label}`)?.shift).toBeNull();
  });
}

test("tasks open on today with daily totals, navigation and optional month view", async ({ page }, testInfo) => {
  await setup(page);
  await page.goto("/tarefas");
  const view = page.getByRole("combobox", { name: "Visualização", exact: true });
  await expect(view).toHaveValue("DAY");
  await expect(page.getByLabel("Data das tarefas", { exact: true })).toHaveValue("2026-09-05");
  await expect(page.getByRole("status")).toHaveText("1 de 1 tarefas");
  await expect(page.getByRole("region", { name: "Totais do dia" })).toContainText("1");
  await expect(page.getByRole("button", { name: "Conferir materiais", exact: true })).toHaveCount(0);
  await page.getByLabel("Buscar tarefa").fill("sem resultado");
  await expect(page.getByRole("status")).toHaveText("0 de 1 tarefas");
  await page.getByRole("button", { name: "Limpar filtros" }).click();
  await expect(view).toHaveValue("DAY");
  await page.screenshot({ path: testInfo.outputPath("tasks-daily.png"), fullPage: true });
  await page.getByRole("button", { name: "Próximo dia", exact: true }).click();
  await expect(page.getByText("Nenhuma tarefa neste dia.", { exact: false })).toBeVisible();
  await page.getByRole("button", { name: "Nova tarefa", exact: true }).click();
  await expect(page.getByLabel("Data inicial", { exact: true })).toHaveValue("2026-09-06");
  await page.getByRole("button", { name: "Cancelar", exact: true }).click();
  await page.getByRole("button", { name: "Dia anterior", exact: true }).click();
  await expect(page.getByRole("status")).toHaveText("1 de 1 tarefas");
  await view.selectOption("MONTH");
  await expect(page.getByRole("status")).toHaveText("2 de 2 tarefas");
  await expect(page.getByRole("region", { name: "Totais do mês" })).toContainText("2");
  await view.selectOption("DAY");
  await expect(page.getByLabel("Data das tarefas", { exact: true })).toHaveValue("2026-09-05");
  await page.getByLabel("Data das tarefas", { exact: true }).fill("2026-08-31");
  await page.getByRole("button", { name: "Próximo dia", exact: true }).click();
  await expect(page.getByLabel("Data das tarefas", { exact: true })).toHaveValue("2026-09-01");
  await expect(page.getByRole("button", { name: "Conferir materiais", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Próximo dia", exact: true }).click();
  await expect(page.getByRole("button", { name: "Conferir materiais", exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "Hoje", exact: true }).click();
  await expect(page.getByLabel("Data das tarefas", { exact: true })).toHaveValue("2026-09-05");
  await page.reload();
  await expect(view).toHaveValue("DAY");
});

test("daily tasks respect institution timezone and multi-day nighttime boundaries", async ({ page }) => {
  const store = await setup(page);
  await page.clock.setFixedTime(new Date("2026-09-06T01:00:00Z"));
  store.items().push({ ...seed[0], id: "night", title: "Turno noturno", allDay: false, shift: "NIGHT",
    startAt: "2026-09-04T21:00:00Z", endAt: "2026-09-06T03:00:00Z" });
  await page.goto("/tarefas");
  await expect(page.getByLabel("Data das tarefas", { exact: true })).toHaveValue("2026-09-05");
  await expect(page.getByRole("status")).toHaveText("2 de 2 tarefas");
  await expect(page.getByRole("button", { name: "Turno noturno", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Próximo dia", exact: true }).click();
  await expect(page.getByRole("button", { name: "Turno noturno", exact: true })).toHaveCount(0);
});
