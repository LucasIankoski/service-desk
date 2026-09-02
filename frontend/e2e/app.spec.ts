import { expect, test } from "@playwright/test";
import axe from "axe-core";

test.beforeEach(async ({ page }) => {
  await page.route("**/api/v1/public/settings", async (route) => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        institutionName: "Prefeitura Modelo",
        timezoneName: "America/Sao_Paulo",
        theme: {
          primaryColor: "#246BCE",
          accentColor: "#158574",
          sidebarColor: "#11263D",
          canvasColor: "#F5F7FA"
        },
        loginBackgroundUrl: "/api/v1/public/settings/login-background",
        version: 1,
        updatedAt: new Date().toISOString()
      })
    });
  });
  await page.route("**/api/v1/public/settings/login-background", async (route) => {
    await route.fulfill({
      contentType: "image/svg+xml",
      body: '<svg xmlns="http://www.w3.org/2000/svg" width="1440" height="900"><rect width="1440" height="900" fill="#dbeafe"/></svg>'
    });
  });
  await page.route("**/api/v1/auth/me", async (route) => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        id: "00000000-0000-0000-0000-000000000001",
        email: "agente@example.test",
        displayName: "Agente Modelo",
        roles: ["AGENT", "ADMIN"],
        passwordChangeRequired: false
      })
    });
  });
  await page.route("**/api/v1/categories", async (route) => {
    await route.fulfill({ contentType: "application/json", body: JSON.stringify([]) });
  });
  await page.route("**/api/v1/users/assignees", async (route) => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify([{ id: "00000000-0000-0000-0000-000000000001", displayName: "Agente Modelo" }])
    });
  });
  await page.route("**/api/v1/notifications**", async (route) => {
    const isCount = route.request().url().includes("unread-count");
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify(isCount ? { count: 0 } : { content: [], number: 0, size: 20, totalElements: 0, totalPages: 0 })
    });
  });
  await page.route("**/api/v1/tickets**", async (route) => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        content: [
          {
            id: "11111111-1111-1111-1111-111111111111",
            publicNumber: "SD-2026-000001",
            subject: "Computador não liga",
            status: "IN_PROGRESS",
            priority: "HIGH",
            requesterId: "00000000-0000-0000-0000-000000000002",
            requesterName: "Maria Silva",
            assigneeId: "00000000-0000-0000-0000-000000000001",
            assigneeName: "Agente Modelo",
            categoryName: "Infraestrutura",
            dueAt: null,
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
            version: 0
          }
        ],
        number: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1
      })
    });
  });
  await page.route("**/api/v1/admin/users**", async (route) => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        content: [
          {
            id: "00000000-0000-0000-0000-000000000003",
            email: "administrativo@example.test",
            displayName: "Administrativo Modelo",
            roles: ["MANAGER"],
            active: true,
            passwordChangeRequired: false,
            anonymized: false,
            createdAt: new Date().toISOString()
          }
        ],
        number: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1
      })
    });
  });
});

test("ticket queue is responsive and accessible", async ({ page }, testInfo) => {
  await page.goto("/tickets");
  await expect(page.getByText("Computador não liga")).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath("ticket-queue.png"), fullPage: true });
  await page.addScriptTag({ content: axe.source });
  const violations = await page.evaluate(async () => {
    return await (window as unknown as { axe: typeof axe }).axe.run(document, {
      runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag22aa"] }
    });
  });
  expect(violations.violations.filter((violation) => ["critical", "serious"].includes(violation.impact ?? ""))).toEqual([]);
});

test("login keeps the background visible and places the form on the right on desktop", async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto("/login");

  const panel = page.getByRole("region", { name: "Prefeitura Modelo" });
  const visual = page.locator("main > [aria-hidden='true']");
  await expect(panel).toBeVisible();
  await expect(visual).toHaveCSS("background-image", /login-background/);
  await page.screenshot({ path: testInfo.outputPath("login-desktop.png"), fullPage: true });
  const bounds = await panel.boundingBox();
  const visualBounds = await visual.boundingBox();

  expect(bounds).not.toBeNull();
  expect(visualBounds).not.toBeNull();
  expect(visualBounds!.x + visualBounds!.width).toBeLessThanOrEqual(bounds!.x);
  expect(bounds!.x).toBeGreaterThan(1440 / 2);
});

test("manager role is presented as Administrativo", async ({ page }) => {
  await page.goto("/admin");

  await expect(page.getByText("Administrativo", { exact: true }).first()).toBeVisible();
  await expect(page.getByText("Gestor", { exact: true })).toHaveCount(0);
});
