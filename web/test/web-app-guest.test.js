const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const http = require("node:http");
const path = require("node:path");

function storage() {
  const values = new Map();
  return {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: (key) => values.delete(key),
  };
}

test("guest keeps profiles, schedule, focus history and day log on this browser only", async () => {
  const { GuestStore, GUEST_DATA_KEY } = await import("../app/src/guest-store.mjs");
  const local = storage();
  const otherBrowser = storage();
  let current;
  const guest = new GuestStore(local, () => 1000);
  guest.start((state) => { current = state; });
  assert.equal(current.sync, "Chỉ trên máy");
  assert.equal(current.profiles[0].entity.name, "Bé của tôi");
  const profileId = current.profiles[0].id;
  await guest.write("tasks", {
    id: 17, taskType: "READING", name: "Đọc sách", emoji: "📚",
    hour: 16, minute: 0, daysOfWeek: "2,3,4,5,6",
    focusDurationMinutes: 25, breakDurationMinutes: 5,
    enabled: true, isCustom: false, childProfileId: profileId,
  });
  await guest.write("sessions", {
    id: 18, taskId: "17", taskTitle: "Đọc sách", durationSeconds: 90,
    timestampMillis: 1000, isFocus: true, completed: false,
    childProfileId: profileId,
  });
  await guest.writeExtra("dayLogs", {
    id: "00000000-0000-4000-8000-000000000001", profile_id: profileId,
    date: "2026-10-17", task_id: 17, name: "Đọc sách", category: "STUDY",
    start_minute: 960, end_minute: 990, source: "MANUAL", created_at: 1000,
  });
  await guest.writeExtra("settings", { focusDurationMinutes: 30 });
  assert.equal(current.tasks[0].entity.name, "Đọc sách");
  assert.equal(current.sessions[0].entity.taskId, "17");
  assert.equal(current.dayLogs[0].entity.name, "Đọc sách");
  assert.equal(current.settings.focusDurationMinutes, 30);
  assert.equal(new GuestStore(otherBrowser, () => 1000).state().tasks.length, 0);
  assert.equal(JSON.parse(local.getItem(GUEST_DATA_KEY)).version, 1);
  guest.stop();
  const reopened = new GuestStore(local, () => 2000);
  assert.equal(reopened.state().tasks[0].entity.name, "Đọc sách");
  assert.equal(reopened.state().sessions.length, 1);
  assert.equal(reopened.state().dayLogs.length, 1);
  await reopened.write("tasks", reopened.state().tasks[0].entity, true);
  assert.equal(reopened.state().tasks.length, 0);
  assert.equal(new GuestStore(local, () => 3000).state().tasks.length, 0);
});

test("guest rejects invalid writes and failed storage without claiming a save", async () => {
  const { GuestStore, GUEST_DATA_KEY } = await import("../app/src/guest-store.mjs");
  const local = storage();
  let current;
  const guest = new GuestStore(local, () => 1000);
  guest.start((state) => { current = state; });
  const before = local.getItem(GUEST_DATA_KEY);
  await assert.rejects(guest.write("tasks", { id: 1, name: "" }), /không hợp lệ/);
  assert.equal(local.getItem(GUEST_DATA_KEY), before);
  local.setItem = () => { throw new Error("storage full"); };
  await assert.rejects(guest.write("profiles", {
    id: "another", name: "Bé Bin", ageBand: "5-6", createdAtMillis: 1000,
  }), /storage full/);
  assert.equal(current.profiles.length, 1);
  assert.equal(current.sync, "Chỉ trên máy");
});

test("corrupt guest data is not overwritten", async () => {
  const { GuestStore, GUEST_DATA_KEY } = await import("../app/src/guest-store.mjs");
  const local = storage();
  local.setItem(GUEST_DATA_KEY, "{broken");
  assert.throws(() => new GuestStore(local), /chưa ghi đè/);
  assert.equal(local.getItem(GUEST_DATA_KEY), "{broken");
});

test("guest can create a schedule offline and resume it after reopening", async (t) => {
  const executablePath = process.env.CHROMIUM_PATH || "/Applications/Brave Browser.app/Contents/MacOS/Brave Browser";
  if (!fs.existsSync(executablePath)) return t.skip("Set CHROMIUM_PATH to Chromium");
  const { chromium } = require("playwright-core");
  const dist = path.resolve(__dirname, "../dist");
  let serveConfig = false;
  const server = http.createServer((request, response) => {
    const route = new URL(request.url, "http://127.0.0.1").pathname;
    // Do not provide Firebase config: this test must never contact production.
    if (route === "/app/firebase-config.json") {
      if (!serveConfig) { response.writeHead(404).end(); return; }
      response.setHeader("Content-Type", "application/json");
      response.end(JSON.stringify({
        apiKey: "demo-key", authDomain: "demo-kidfocus.firebaseapp.com",
        projectId: "demo-kidfocus", appId: "1:123456789:web:demo",
      }));
      return;
    }
    const file = path.resolve(dist, "." + (route.endsWith("/") ? route + "index.html" : route));
    if (!file.startsWith(dist + path.sep)) { response.writeHead(403).end(); return; }
    fs.readFile(file, (error, bytes) => {
      if (error) { response.writeHead(404).end(); return; }
      const extension = path.extname(file);
      response.setHeader("Content-Type", ({ ".html": "text/html", ".js": "text/javascript", ".css": "text/css" })[extension] || "application/octet-stream");
      response.end(bytes);
    });
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  t.after(() => server.close());
  const browser = await chromium.launch({ executablePath, headless: true, args: ["--no-sandbox"] });
  t.after(() => browser.close());
  const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
  await page.addInitScript(() => {
    window.__analyticsEvents = [];
    window.kidfocusTrack = (name) => window.__analyticsEvents.push(name);
  });
  const origin = `http://127.0.0.1:${server.address().port}`;
  const errors = [];
  const external = [];
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("request", (request) => {
    if (!request.url().startsWith(origin + "/")) external.push(request.url());
  });
  await page.goto(origin + "/app/");
  await page.getByRole("button", { name: "Dùng thử không cần đăng nhập" }).click();
  await page.getByText("Chế độ khách.").waitFor();
  assert.deepEqual(await page.evaluate(() => window.__analyticsEvents), ["guest_start"]);
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
  await page.locator(".bottom-nav").getByRole("button", { name: "Lịch" }).click();
  await page.getByRole("button", { name: "+ Hoạt động" }).click();
  await page.getByRole("dialog").getByLabel("Tên hoạt động").fill("Đọc sách thử");
  await page.getByRole("button", { name: "Lưu hoạt động" }).click();
  await page.getByText("Đọc sách thử").first().waitFor();
  await page.locator(".bottom-nav").getByRole("button", { name: "Tập trung" }).click();
  await page.getByLabel("Hoạt động").selectOption({ label: "⭐ Đọc sách thử" });
  await page.getByRole("button", { name: "Bắt đầu" }).click();
  await page.getByRole("button", { name: "Tạm dừng" }).waitFor();
  await page.waitForTimeout(1100);
  await page.getByRole("button", { name: "Dừng sớm" }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Lưu và kết thúc phiên" }).click();
  await page.getByRole("heading", { name: "Đã hoàn thành", exact: true }).waitFor();
  await page.getByText(/phút · Dừng sớm/).waitFor();
  assert.deepEqual(await page.evaluate(() => {
    const saved = JSON.parse(localStorage.getItem("kidfocus-web-guest-data-v1"));
    return [Object.keys(saved.docs.tasks).length, Object.keys(saved.docs.sessions).length];
  }), [1, 1]);
  await page.reload();
  await page.getByText("Chế độ khách.").waitFor();
  await page.locator(".bottom-nav").getByRole("button", { name: "Lịch" }).click();
  await page.getByText("Đọc sách thử").first().waitFor();
  await page.locator(".guest-banner").getByRole("button", { name: "Đăng nhập" }).click();
  assert.equal(await page.evaluate(() => localStorage.getItem("kidfocus-web-guest-mode")), null);
  assert.notEqual(await page.evaluate(() => localStorage.getItem("kidfocus-web-guest-data-v1")), null);
  assert.deepEqual(external, []);
  assert.deepEqual(errors, []);

  // Also verify the normal signed-out screen offers the same guest entry.
  serveConfig = true;
  const signedOut = await browser.newPage({ viewport: { width: 390, height: 844 } });
  await signedOut.route("**/*", (route) => route.request().url().startsWith(origin + "/")
    ? route.continue() : route.abort());
  await signedOut.goto(origin + "/app/");
  await signedOut.getByRole("heading", { name: "Lịch sinh hoạt rõ ràng hơn mỗi ngày" }).waitFor();
  await signedOut.getByRole("button", { name: "Dùng thử không cần đăng nhập" }).click();
  await signedOut.getByText("Chế độ khách.").waitFor();
});
