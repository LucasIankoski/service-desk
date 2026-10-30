import { test, expect, type Page } from "@playwright/test";
import { readFileSync } from "node:fs";
import axe from "axe-core";

const root = "/pedagogico/turmas/aee/turmas/regular/alunos";
const studentRoot = `${root}/student`;
const api = "/api/v1/pedagogical/aee/aee/classes/regular/students";
const pdf = readFileSync(new URL("./fixtures/aee-multipage.pdf", import.meta.url));
const encrypted = readFileSync(new URL("./fixtures/aee-protected.pdf", import.meta.url));
async function setup(page: Page, admin = false, editable = true) {
  const teacher = { id: "teacher", displayName: "Professora Ana" };
  let students = [{ id: "student", aeeClassId: "aee", classId: "regular", name: "Maria da Silva", archived: false, version: 0, canManage: editable }];
  const regular = { id: "regular", name: "Turma regular", archived: false, canView: true, canCreateStudents: editable };
  let documents = ["documento.pdf", "protegido.pdf", "invalido.pdf"].map((filename, i) => ({ id: `doc${i}`, filename, size: pdf.length, authorId: "teacher", authorName: "Professora Ana", createdAt: "2026-10-25T12:00:00Z", version: 0, canDelete: editable }));
  let fail = false;
  const classroom = { id: "aee", name: "Atendimento Especial", aee: true, archived: false, teachers: [teacher], version: 0, contentVisibility: "ALLOCATED_ONLY", canCreate: false, canEdit: false, canManage: admin };
  const plan = { id: "plan", classId: "regular", className: "AEE: Atendimento Especial · Turma: Turma regular · Aluno: Maria da Silva", archived: false, weekStart: "2026-10-26", weekEnd: "2026-10-30", theme: "Experiências individuais", teachers: [teacher], status: "DRAFT", version: 0,
    days: Array.from({ length: 5 }, (_, i) => ({ date: `2026-10-${26 + i}`, noClass: false, reason: null, proposal: null, objectives: null, development: null, resources: null })) };
  await page.route("**/api/v1/**", async route => {
    const req = route.request(), url = new URL(req.url()), path = url.pathname, method = req.method();
    const json = (data: unknown, status = 200) => route.fulfill({ contentType: "application/json", status, body: JSON.stringify(data) });
    if (path.endsWith("/auth/me")) return json({ id: "teacher", displayName: "Professora Ana", roles: [admin ? "ADMIN" : "REQUESTER"], passwordChangeRequired: false });
    if (path.endsWith("/auth/csrf")) return json({ token: "test" });
    if (path.endsWith("/public/settings")) return json({ institutionName: "Escola Modelo", timezoneName: "America/Sao_Paulo" });
    if (path.includes("/notifications")) return json(path.endsWith("/unread-count") ? { count: 0 } : { content: [] });
    if (path === "/api/v1/pedagogical/classes" && method === "POST") { expect(req.postDataJSON().aee).toBe(true); return json({ ...classroom, ...req.postDataJSON() }, 201); }
    if (path === "/api/v1/pedagogical/classes") return json([classroom]);
    if (path === "/api/v1/pedagogical/teachers") return json([teacher]);
    if (path === "/api/v1/pedagogical/classes/aee") return json(classroom);
    if (path === "/api/v1/pedagogical/classes/aee/plans") return json([{ ...plan, id: "legacy-plan", classId: "aee", className: classroom.name }]);
    if (path === "/api/v1/pedagogical/classes/aee/projects") return json([]);
    if (path === "/api/v1/pedagogical/aee/aee/classes") return json([regular, { ...regular, id: "restricted", name: "Outra turma", canView: false, canCreateStudents: false }, ...(url.searchParams.get("archived") === "true" ? [{ ...regular, id: "archived", name: "Turma arquivada", archived: true, canCreateStudents: false }] : [])]);
    if (path === api && method === "GET") return json(students);
    if (path === api && method === "POST") { const data = req.postDataJSON(); const s = { ...students[0], id: "student2", ...data, version: 0 }; students.push(s); return json(s, 201); }
    if (path.startsWith(api) && method === "PUT") {
      if (fail) { fail = false; return json({ detail: "Este aluno mudou. Consulte a versão atual." }, 409); }
      const id = path.split("/").pop(); students = students.map(s => s.id === id ? { ...s, ...req.postDataJSON(), version: s.version + 1 } : s);
      return json(students.find(s => s.id === id));
    }
    if (path === `${api}/student`) return json({ aeeId: "aee", aeeName: classroom.name, classId: "regular", className: regular.name, student: students[0], archived: students[0].archived, canEdit: editable && !students[0].archived, canManage: admin, teachers: [teacher] });
    if (path === `${api}/student/plans`) return json(method === "POST" ? plan : [plan], method === "POST" ? 201 : 200);
    if (path === `${api}/student/plans/plan`) return json(plan);
    if (path === `${api}/student/projects`) return json([]);
    if (path === `${api}/student/records`) return json({ items: [], page: 0, totalPages: 0, totalElements: 0, attachmentLimitMb: 5 });
    if (path === `${api}/student/documents` && method === "GET") return json({ items: documents, canUpload: editable, attachmentLimitMb: 5 });
    if (path === `${api}/student/documents` && method === "POST") { const d = { ...documents[0], id: "added", filename: "novo.pdf" }; documents.push(d); return json(d, 201); }
    if (path.startsWith(`${api}/student/documents/`)) {
      const id = path.split("/").pop();
      if (method === "DELETE") { expect(url.searchParams.get("version")).toBe("0"); documents = documents.filter(d => d.id !== id); return route.fulfill({ status: 204 }); }
      return route.fulfill({ contentType: "application/pdf", headers: { "Cache-Control": "no-store" }, body: id === "doc1" ? encrypted : id === "doc2" ? Buffer.from("invalid PDF") : pdf });
    }
    if (path.includes("/pedagogical/")) throw new Error(`Unexpected unscoped pedagogical request: ${method} ${path}`);
    return json({ content: [], totalElements: 0, totalPages: 0 });
  });
  return { conflict: () => { fail = true; } };
}

async function accessible(page: Page) {
  await page.evaluate(axe.source);
  const result = await page.evaluate(() => window.axe.run(document.querySelector("main")!, { runOnly: { type: "tag", values: ["wcag2a", "wcag2aa"] } }));
  expect(result.violations).toEqual([]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
}

test("cadastro permite marcar AEE e a entrada exibe turmas restritas sem alunos", async ({ page }, info) => {
  await setup(page, true);
  await page.goto("/pedagogico");
  await page.getByRole("button", { name: "Nova turma", exact: true }).click();
  await expect(page.getByLabel("Atendimento Educacional Especializado", { exact: true })).not.toBeChecked();
  await page.getByLabel("Nome da turma", { exact: true }).fill("AEE novo");
  await page.getByLabel("Atendimento Educacional Especializado", { exact: true }).check();
  await page.getByRole("button", { name: "Salvar turma", exact: true }).click();
  await expect(page.getByRole("button", { name: "Salvar turma", exact: true })).toHaveCount(0);
  await page.getByRole("link", { name: "Atendimento Especial", exact: true }).click();
  await expect(page.getByRole("link", { name: "Turma regular", exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "Outra turma", exact: true })).toHaveCount(0);
  await expect(page.getByText("Acesso restrito", { exact: true })).toBeVisible();
  await expect(page.getByText("Maria da Silva", { exact: true })).toHaveCount(0);
  await page.getByLabel("Incluir turmas arquivadas").check();
  await expect(page.getByRole("link", { name: "Turma arquivada", exact: true })).toBeVisible();
  await accessible(page);
  await page.screenshot({ path: info.outputPath("aee-turmas.png"), fullPage: true });
});

test("alunos homônimos permanecem distintos, edição preserva conflito e arquivamento permite reativar", async ({ page }) => {
  const state = await setup(page);
  await page.goto(root);
  await page.getByRole("button", { name: "Cadastrar aluno", exact: true }).click();
  await page.getByLabel("Nome completo").fill("Maria da Silva");
  await page.getByRole("button", { name: "Salvar aluno", exact: true }).click();
  await expect(page.getByRole("link", { name: "Maria da Silva", exact: true })).toHaveCount(2);
  await page.getByRole("button", { name: "Gerenciar aluno", exact: true }).first().click();
  await page.getByLabel("Nome completo").fill("Maria Renomeada"); state.conflict();
  await page.getByRole("button", { name: "Salvar aluno", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("mudou");
  await expect(page.getByLabel("Nome completo")).toHaveValue("Maria Renomeada");
  await page.getByLabel("Aluno arquivado (somente consulta)").check();
  await page.getByRole("button", { name: "Salvar aluno", exact: true }).click();
  await expect(page.getByRole("link", { name: "Maria Renomeada", exact: true })).toHaveCount(0);
  await page.getByLabel("Incluir alunos arquivados").check();
  await page.getByRole("button", { name: "Gerenciar aluno", exact: true }).first().click();
  await page.getByLabel("Aluno arquivado (somente consulta)").uncheck();
  await page.getByRole("button", { name: "Salvar aluno", exact: true }).click();
  await expect(page.getByText("Aluno ativo", { exact: true })).toHaveCount(2);
  await accessible(page);
});

test("aluno reutiliza as quatro áreas e planejamento imprime o contexto completo", async ({ page }) => {
  await setup(page);
  await page.goto(studentRoot);
  for (const area of ["Planejamento Pedagógico", "Projetos", "Registros", "Documentos"]) await expect(page.getByRole("navigation", { name: "Áreas da turma" }).getByRole("link", { name: area, exact: true })).toBeVisible();
  await page.getByLabel("Nova semana (segunda-feira)").fill("2026-10-26");
  await page.getByRole("button", { name: "Criar planejamento", exact: true }).click();
  await expect(page).toHaveURL(new RegExp(studentRoot + "/planejamentos/plan$"));
  await page.getByRole("button", { name: "Visualizar / Imprimir", exact: true }).click();
  await expect(page.locator("[data-pedagogical-document]")).toContainText("Aluno: Maria da Silva");
  await page.goto(`${studentRoot}/projetos`);
  await expect(page.getByRole("heading", { name: "Novo projeto", exact: true })).toBeVisible();
  await page.getByRole("navigation", { name: "Áreas da turma" }).getByRole("link", { name: "Registros", exact: true }).click();
  await expect(page.getByRole("button", { name: "Novo registro", exact: true })).toBeVisible();
  await page.getByRole("link", { name: "Documentos", exact: true }).click();
  await expect(page.getByRole("button", { name: "Anexar documento", exact: true })).toBeVisible();
});

test("PDF local exibe texto, imagem e páginas com zoom, upload, download e exclusão confirmada", async ({ page }, info) => {
  await setup(page);
  await page.goto(`${studentRoot}/documentos`);
  await page.getByRole("button", { name: "Visualizar documento.pdf", exact: true }).click();
  await expect(page.getByText("Página 1 de 2", { exact: true })).toBeVisible();
  await expect(page.locator(".textLayer")).toContainText("Documento AEE pagina 1");
  await expect.poll(() => page.locator("canvas[aria-label]").evaluate((c: HTMLCanvasElement) => c.getContext("2d")!.getImageData(Math.floor(c.width * .2), Math.floor(c.height * .4), 1, 1).data[0])).toBeLessThan(100);
  await page.getByRole("button", { name: "Próxima página", exact: true }).click();
  await expect(page.locator(".textLayer")).toContainText("Documento AEE pagina 2");
  await page.getByLabel("Zoom", { exact: true }).selectOption("1.5");
  await expect.poll(() => page.locator("canvas[aria-label]").evaluate((c: HTMLCanvasElement) => c.style.width)).toBe("600px");
  await page.getByLabel("Zoom", { exact: true }).selectOption("fit");
  await expect(page.getByRole("link", { name: "Baixar documento.pdf", exact: true })).toHaveAttribute("href", /download=true$/);
  await accessible(page);
  await page.screenshot({ path: info.outputPath("aee-pdf.png"), fullPage: true });
  await page.getByRole("button", { name: "Fechar visualização", exact: true }).click();
  await page.getByLabel(/Anexar PDF/).setInputFiles({ name: "novo.pdf", mimeType: "application/pdf", buffer: pdf });
  await page.getByRole("button", { name: "Anexar documento", exact: true }).click();
  await expect(page.getByRole("heading", { name: "novo.pdf", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Excluir novo.pdf", exact: true }).click();
  await page.getByRole("button", { name: "Cancelar", exact: true }).click();
  await expect(page.getByRole("heading", { name: "novo.pdf", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Excluir novo.pdf", exact: true }).click();
  await page.getByRole("button", { name: "Confirmar exclusão", exact: true }).click();
  await expect(page.getByRole("heading", { name: "novo.pdf", exact: true })).toHaveCount(0);
});

test("PDF protegido solicita senha apenas no navegador e arquivo inválido apresenta erro", async ({ page }) => {
  await setup(page);
  await page.goto(`${studentRoot}/documentos`);
  await page.getByRole("button", { name: "Visualizar protegido.pdf", exact: true }).click();
  await page.getByLabel("Senha do PDF").fill("incorreta");
  await page.getByRole("button", { name: "Abrir PDF", exact: true }).click();
  await expect(page.getByText("Senha incorreta. Tente novamente.", { exact: true })).toBeVisible();
  await page.getByLabel("Senha do PDF").fill("senha-teste");
  await page.getByRole("button", { name: "Abrir PDF", exact: true }).click();
  await expect(page.locator(".textLayer")).toContainText("Documento AEE pagina 1");
  await expect(page.getByLabel("Senha do PDF")).toHaveCount(0);
  await page.getByRole("button", { name: "Fechar visualização", exact: true }).click();
  await page.getByRole("button", { name: "Visualizar invalido.pdf", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Não foi possível abrir este PDF");
});

test("consulta compartilhada oculta operações de escrita e cadastro de alunos", async ({ page }) => {
  await setup(page, false, false);
  await page.goto(root);
  await expect(page.getByRole("link", { name: "Maria da Silva", exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: /Cadastrar aluno|Gerenciar aluno/ })).toHaveCount(0);
  await page.goto(`${studentRoot}/documentos`);
  await expect(page.getByRole("button", { name: "Visualizar documento.pdf", exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: /Anexar documento|Excluir documento/ })).toHaveCount(0);
});

test("histórico anterior da AEE permanece em consulta ao filtrar ano e alternar áreas", async ({ page }) => {
  await setup(page, true);
  await page.goto("/pedagogico/turmas/aee");
  await page.getByRole("link", { name: "Documentos anteriores da turma · consulta e impressão" }).click();
  await expect(page.getByText("Documentos anteriores do AEE. Disponíveis somente para consulta e impressão.")).toBeVisible();
  await page.getByLabel("Ano", { exact: true }).selectOption("2026");
  await expect(page).toHaveURL(/historico=1.*ano=2026/);
  await expect(page.getByRole("link", { name: /Experiências individuais/ })).toBeVisible();
  await expect(page.getByRole("button", { name: "Criar planejamento", exact: true })).toHaveCount(0);
  await page.getByRole("link", { name: "Projetos", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Novo projeto", exact: true })).toHaveCount(0);
  await page.getByRole("link", { name: "Planejamento Pedagógico", exact: true }).click();
  await expect(page).toHaveURL(/historico=1/);
  await expect(page.getByRole("link", { name: /Experiências individuais/ })).toBeVisible();
});
