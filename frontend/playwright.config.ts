import { defineConfig, devices } from "@playwright/test";

const externalServer = process.env.E2E_EXTERNAL_SERVER === "1";

export default defineConfig({
  testDir: "./e2e",
  timeout: 30_000,
  expect: { timeout: 5_000 },
  webServer: externalServer ? undefined : {
    command: "node ./node_modules/vite/bin/vite.js --host 127.0.0.1 --port 5174",
    url: "http://127.0.0.1:5174",
    reuseExistingServer: false,
    timeout: 120_000
  },
  use: {
    baseURL: "http://127.0.0.1:5174",
    trace: "retain-on-failure"
  },
  projects: [
    { name: "chromium-desktop", use: { ...devices["Desktop Chrome"], viewport: { width: 1440, height: 920 } } },
    { name: "firefox-tablet", use: { ...devices["Desktop Firefox"], viewport: { width: 768, height: 1024 } } },
    { name: "webkit-mobile", use: { ...devices["iPhone 13"], viewport: { width: 360, height: 780 } } }
  ]
});
