import { expect, test, type Page } from "@playwright/test";
import type { User } from "../src/api/types";

function makeUser(number: number): User {
  return {
    id: `00000000-0000-0000-0000-${String(number).padStart(12, "0")}`,
    displayName: `Usuário ${String(number).padStart(3, "0")}`,
    email: `usuario${number}@example.test`,
    roles: ["REQUESTER"], active: true, anonymized: false,
    passwordChangeRequired: false, createdAt: "2026-10-01T12:00:00Z"
  };
}

async function setup(page: Page, count: number) {
  const state = {
    users: Array.from({ length: count }, (_, index) => makeUser(index + 1)),
    lists: [] as number[],
    mutations: [] as { method: string; path: string; body: Record<string, unknown> | null }[],
    failPage: -1,
    delayPage: -1,
    release: () => {}
  };
  let release!: () => void;
  const gate = new Promise<void>((resolve) => { release = resolve; });
  state.release = release;
  await page.route("**/api/v1/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();
    const json = (body: unknown, status = 200) => route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });
    if (path.endsWith("/auth/me")) return json({ ...makeUser(999), roles: ["ADMIN"] });
    if (path.endsWith("/auth/csrf")) return json({ token: "test-csrf" });
    if (path.endsWith("/public/settings")) return json({ institutionName: "Central", timezoneName: "America/Sao_Paulo" });
    if (path.includes("/notifications")) return json(path.endsWith("/unread-count") ? { count: 0 } : { content: [] });
    if (path === "/api/v1/admin/users" && method === "GET") {
      const current = Number(url.searchParams.get("page"));
      expect(url.searchParams.get("size")).toBe("20");
      state.lists.push(current);
      if (state.delayPage === current) await gate;
      if (state.failPage === current) return json({ detail: "Falha ao carregar usuários." }, 500);
      const sorted = [...state.users].sort((a, b) => a.displayName.localeCompare(b.displayName));
      return json({ content: sorted.slice(current * 20, (current + 1) * 20), number: current,
        size: 20, totalElements: sorted.length, totalPages: Math.ceil(sorted.length / 20) });
    }
    if (path.startsWith("/api/v1/admin/users") && method !== "GET") {
      const body = request.postData() ? request.postDataJSON() : null;
      state.mutations.push({ method, path, body });
      expect(request.headers()["x-xsrf-token"]).toBe("test-csrf");
      if (path.endsWith("/temporary-password")) return json({ temporaryPassword: "NovaSenhaTeste!" });
      if (path === "/api/v1/admin/users" && method === "POST") {
        const user = { ...makeUser(100), ...body };
        state.users.push(user);
        return json({ user, temporaryPassword: "SenhaCriacaoTeste!" });
      }
      const id = path.split("/")[5];
      const user = state.users.find((entry) => entry.id === id)!;
      if (path.endsWith("/anonymize")) {
        user.anonymized = true;
        user.displayName = "Conta anonimizada";
        return route.fulfill({ status: 204 });
      }
      Object.assign(user, body);
      return json(user);
    }
    return json({});
  });
  return state;
}

const navigation = (page: Page) => page.getByRole("navigation", { name: "Paginação de usuários" });
const row = (page: Page, number: number) => page.locator("form").filter({ has: page.getByText(`usuario${number}@example.test`, { exact: true }) });

for (const count of [0, 20, 21, 41]) {
  test(`${count} usuários: navegação e limites`, async ({ page }) => {
    const state = await setup(page, count);
    await page.goto("/admin");
    await expect(page.getByText(`Total de usuários: ${count}`, { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "Salvar", exact: true })).toHaveCount(Math.min(count, 20));
    if (count === 0) await expect(page.getByText("Nenhum usuário cadastrado.")).toBeVisible();
    if (count <= 20) {
      await expect(navigation(page)).toHaveCount(0);
      expect(state.lists).toEqual([0]);
      return;
    }
    const pages = Math.ceil(count / 20);
    await expect(navigation(page).getByRole("button", { name: "Anterior" })).toBeDisabled();
    for (let current = 1; current < pages; current++) {
      const next = navigation(page).getByRole("button", { name: "Próxima" });
      await next.focus();
      await page.keyboard.press("Enter");
      await expect(navigation(page)).toContainText(`Página ${current + 1} de ${pages}`);
      await expect(row(page, current * 20 + 1)).toBeVisible();
      await expect(row(page, 1)).toHaveCount(0);
    }
    await expect(navigation(page).getByRole("button", { name: "Próxima" })).toBeDisabled();
    const box = await navigation(page).boundingBox();
    expect(box!.x).toBeGreaterThanOrEqual(0);
    expect(box!.x + box!.width).toBeLessThanOrEqual(page.viewportSize()!.width);
    for (let current = pages - 2; current >= 0; current--) {
      await navigation(page).getByRole("button", { name: "Anterior" }).click();
      await expect(navigation(page)).toContainText(`Página ${current + 1} de ${pages}`);
    }
    await expect(row(page, 1)).toBeVisible();
    expect(state.lists).toEqual(Array.from({ length: pages }, (_, index) => index));
  });
}

test("ações na segunda página preservam a página e invalidam o cache", async ({ page }) => {
  const state = await setup(page, 21);
  await page.goto("/admin");
  await navigation(page).getByRole("button", { name: "Próxima" }).click();
  await row(page, 21).locator('input[name="displayName"]').fill("Usuário 021 editado");
  await row(page, 21).getByRole("button", { name: "Salvar", exact: true }).click();
  await expect.poll(() => state.lists.filter((value) => value === 1).length).toBe(2);
  await expect(row(page, 21).getByRole("button", { name: "Senha", exact: true })).toBeEnabled();
  expect(state.mutations[0]).toMatchObject({ method: "PATCH", path: `/api/v1/admin/users/${makeUser(21).id}`,
    body: { displayName: "Usuário 021 editado", roles: ["REQUESTER"], active: true } });
  await row(page, 21).getByRole("button", { name: "Senha", exact: true }).click();
  await expect(page.getByText("NovaSenhaTeste!")).toBeVisible();
  expect(state.mutations[1]).toMatchObject({ method: "POST", path: `/api/v1/admin/users/${makeUser(21).id}/temporary-password` });
  await page.getByLabel("Nome", { exact: true }).fill("Usuário 022");
  await page.getByLabel("E-mail", { exact: true }).fill("novo@example.test");
  await page.getByRole("button", { name: "Criar usuário" }).click();
  await expect(page.getByText("Total de usuários: 22", { exact: true })).toBeVisible();
  await expect(navigation(page)).toContainText("Página 2 de 2");
  await expect(page.getByText("novo@example.test", { exact: true })).toBeVisible();
  page.once("dialog", (dialog) => dialog.accept());
  await row(page, 21).getByRole("button", { name: "Anonimizar", exact: true }).click();
  await expect(row(page, 21)).toHaveCount(0);
  await expect(navigation(page)).toContainText("Página 2 de 2");
  expect(state.mutations[3]).toMatchObject({ method: "POST", path: `/api/v1/admin/users/${makeUser(21).id}/anonymize` });
  await navigation(page).getByRole("button", { name: "Anterior" }).click();
  await expect(row(page, 21)).toBeVisible();
  await expect(row(page, 21).getByRole("button", { name: "Anonimizar", exact: true })).toHaveCount(0);
  expect(state.lists.filter((value) => value === 0)).toHaveLength(2);
});

test("carregamento bloqueia navegação e não mostra registros de outra página", async ({ page }) => {
  const state = await setup(page, 21);
  state.delayPage = 1;
  await page.goto("/admin");
  await navigation(page).getByRole("button", { name: "Próxima" }).click();
  try {
    await expect(page.getByText("Carregando usuários.")).toBeVisible();
    await expect(row(page, 1)).toHaveCount(0);
    await expect(navigation(page).getByRole("button", { name: "Anterior" })).toBeDisabled();
    await expect(navigation(page).getByRole("button", { name: "Próxima" })).toBeDisabled();
  } finally { state.release(); }
  await expect(row(page, 21)).toBeVisible();
});

test("erro na página seguinte permite tentar novamente", async ({ page }) => {
  const state = await setup(page, 21);
  state.failPage = 1;
  await page.goto("/admin");
  await navigation(page).getByRole("button", { name: "Próxima" }).click();
  await expect(page.getByRole("alert")).toHaveText("Falha ao carregar usuários.", { timeout: 15_000 });
  await expect(row(page, 1)).toHaveCount(0);
  state.failPage = -1;
  await page.getByRole("button", { name: "Tentar novamente" }).click();
  await expect(row(page, 21)).toBeVisible();
  await expect(page.getByRole("alert")).toHaveCount(0);
  await expect(navigation(page)).toContainText("Página 2 de 2");
});

test("retorna à última página válida quando o total diminui", async ({ page }) => {
  const state = await setup(page, 21);
  await page.goto("/admin");
  await navigation(page).getByRole("button", { name: "Próxima" }).click();
  await expect(row(page, 21)).toBeVisible();
  state.users = state.users.slice(0, 20);
  await page.getByLabel("Nome", { exact: true }).fill("Usuário 022");
  await page.getByLabel("E-mail", { exact: true }).fill("novo@example.test");
  // Another administrator has removed accounts before this mutation refreshes the list.
  state.users = state.users.slice(0, 19);
  await page.getByRole("button", { name: "Criar usuário" }).click();
  await expect(page.getByText("Total de usuários: 20", { exact: true })).toBeVisible();
  await expect(navigation(page)).toHaveCount(0);
  await expect(row(page, 1)).toBeVisible();
  await expect.poll(() => state.lists.at(-1)).toBe(0);
});
