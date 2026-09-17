import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./tests",
  timeout: 45000,
  use: {
    baseURL: process.env.BASE_URL || "http://localhost:5173",
    channel: process.env.CI ? "chromium" : "chrome",
    headless: true,
    viewport: { width: 1440, height: 1000 },
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
  },
  reporter: "list",
  workers: 1,
});
