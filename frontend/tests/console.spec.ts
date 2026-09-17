import { test, expect } from "@playwright/test";
test("customer creates and cancels a persisted order", async ({ page }) => {
  await page.goto("/");
  await page.getByLabel("Email address").fill("customer@fleetflow.demo");
  await page
    .getByLabel("Password", { exact: true })
    .fill(process.env.DEMO_PASSWORD!);
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await page.getByRole("link", { name: "Orders", exact: true }).click();
  await page.getByRole("button", { name: "Create order" }).click();
  const dialog = page.getByRole("dialog");
  const address = `Browser verification ${Date.now()}`;
  await dialog.getByLabel("Delivery address").fill(address);
  await dialog.getByLabel("Latitude", { exact: true }).fill("20.30");
  await dialog.getByLabel("Longitude", { exact: true }).fill("85.82");
  await expect(
    dialog.getByLabel("Product 1").locator("option"),
  ).not.toHaveCount(1);
  await dialog.getByLabel("Product 1").selectOption({ index: 1 });
  await dialog.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.getByRole("heading", { name: address })).toBeVisible();
  await expect(
    page.getByText("Warehouse assigned", { exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Cancelled", exact: true }).click();
  await expect(page.getByText("Cancelled", { exact: true })).toBeVisible();
  await expect(
    page.getByText("No actions available in this state."),
  ).toBeVisible();
});
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

test("multi-stop planner uses the real API and validates coordinates", async ({
  page,
}) => {
  await page.goto("/");
  await page.getByLabel("Email address").fill("admin@fleetflow.demo");
  await page
    .getByLabel("Password", { exact: true })
    .fill(process.env.DEMO_PASSWORD!);
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await page.getByRole("link", { name: /Routes/ }).click();
  await page.getByLabel("Start latitude").fill("20");
  await page.getByLabel("Start longitude").fill("85");
  await page
    .getByLabel("Stops (latitude, longitude per line)")
    .fill("20, 85\n20, 85");
  await page.getByRole("button", { name: "Calculate route" }).click();
  await expect(page.getByRole("status")).toContainText("Visit stops:");
  await expect(page.getByRole("status")).toContainText("0.00 km");
  await expect(page.getByRole("status")).toContainText("10 minutes");
  const icon = await page.locator(".map-label svg").boundingBox();
  expect(icon?.width).toBeLessThan(24);
  expect(icon?.height).toBeLessThan(24);
  await page.screenshot({
    path: "test-results/route-plan.png",
    fullPage: true,
  });
  await page.getByLabel("Stops (latitude, longitude per line)").fill("91, 85");
  await page.getByRole("button", { name: "Calculate route" }).click();
  await expect(page.getByRole("alert")).toContainText("Check request fields");
});
