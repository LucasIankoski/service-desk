import { spawn, spawnSync } from "node:child_process";
import process from "node:process";
import { setTimeout as delay } from "node:timers/promises";

const port = process.env.E2E_PORT ?? "5174";
const baseUrl = `http://127.0.0.1:${port}`;
let vite;

try {
  vite = spawn(process.execPath, [
    "./node_modules/vite/bin/vite.js",
    "--host",
    "127.0.0.1",
    "--port",
    port
  ], {
    stdio: ["ignore", "inherit", "inherit"],
    detached: process.platform !== "win32"
  });

  await waitForServer(baseUrl);

  const code = await runPlaywright();
  cleanup();
  process.exit(code);
} catch (error) {
  cleanup();
  console.error(error instanceof Error ? error.message : error);
  process.exit(1);
}

function runPlaywright() {
  return new Promise((resolve) => {
    const child = spawn(process.execPath, ["./node_modules/@playwright/test/cli.js", "test"], {
      stdio: "inherit",
      env: {
        ...process.env,
        E2E_EXTERNAL_SERVER: "1"
      }
    });
    child.on("close", (code) => resolve(code ?? 1));
  });
}

async function waitForServer(url) {
  const deadline = Date.now() + 120_000;
  while (Date.now() < deadline) {
    try {
      const response = await fetch(url);
      if (response.ok) {
        return;
      }
    } catch {
      // Keep polling while Vite boots.
    }
    await delay(250);
  }
  throw new Error(`Servidor E2E não respondeu em ${url}.`);
}

function cleanup() {
  if (!vite?.pid || vite.killed) {
    return;
  }
  if (process.platform === "win32") {
    spawnSync("taskkill", ["/pid", String(vite.pid), "/T", "/F"], { stdio: "ignore" });
  } else {
    try {
      process.kill(-vite.pid, "SIGTERM");
    } catch {
      vite.kill("SIGTERM");
    }
  }
}

for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => {
    cleanup();
    process.exit(130);
  });
}
