import { test, expect } from "@playwright/test";
test("admin console renders persisted operations and tracking", async ({
  page,
}) => {
  const errors: string[] = [];
  page.on("pageerror", (e) => errors.push(e.message));
  await page.goto("/");
  await page.getByLabel("Email address").fill("admin@fleetflow.demo");
  await page
    .getByLabel("Password", { exact: true })
    .fill(process.env.DEMO_PASSWORD!);
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Operations overview." }),
  ).toBeVisible();
  await expect(
    page.getByText("Bhubaneswar Central", { exact: true }).first(),
  ).toBeVisible();
  await page.screenshot({
    path: process.env.SCREENSHOT_PATH || "test-results/overview.png",
    fullPage: true,
  });
  await page.getByRole("link", { name: "Orders", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Order management." }),
  ).toBeVisible();
  await page.getByRole("link", { name: "Live tracking", exact: true }).click();
  const select = page.getByLabel("Select shipment");
  await expect(select.locator("option")).not.toHaveCount(1);
  await select.selectOption({ index: 1 });
  await expect(page.getByText("Every handoff, recorded")).toBeVisible();
  await expect(page.getByText("Live", { exact: true })).toBeVisible({
    timeout: 15000,
  });
  expect(errors).toEqual([]);
});
test("customer access and mobile layout", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/");
  await page.getByLabel("Email address").fill("customer@fleetflow.demo");
  await page
    .getByLabel("Password", { exact: true })
    .fill(process.env.DEMO_PASSWORD!);
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Your deliveries." }),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBeTruthy();
  await page.getByRole("button", { name: "Toggle navigation" }).click();
  await expect(
    page.getByRole("link", { name: "Inventory", exact: true }),
  ).toHaveCount(0);
  await page.getByRole("link", { name: "Orders", exact: true }).click();
  await expect(page.getByRole("heading", { name: "My orders." })).toBeVisible();
  await page.getByRole("button", { name: "Create order" }).click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.getByRole("button", { name: "Cancel", exact: true }).click();
});
