/// <reference types="vitest" />
import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";
import { cpSync, mkdirSync } from "node:fs";
import { fileURLToPath } from "node:url";

// The viewer never depends on a CDN, including fonts, CMaps and image decoders.
for (const directory of ["cmaps", "standard_fonts", "wasm"]) {
  const target = fileURLToPath(new URL(`./public/pdfjs/${directory}`, import.meta.url));
  mkdirSync(target, { recursive: true });
  cpSync(fileURLToPath(new URL(`./node_modules/pdfjs-dist/${directory}`, import.meta.url)), target, { recursive: true });
}

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: false
      }
    }
  },
  test: {
    environment: "jsdom",
    setupFiles: "./vitest.setup.ts",
    globals: false,
    exclude: ["e2e/**", "node_modules/**", "dist/**"]
  }
});
