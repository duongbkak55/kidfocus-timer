const { test } = require("node:test");
const assert = require("node:assert/strict");
const http = require("node:http");
const fs = require("node:fs");
const path = require("node:path");
const { chromium } = require("playwright-core");

// Browser regression tests use GuestStore only; every non-local request is blocked.
async function fixture(t, width = 390) {
  const executablePath = process.env.CHROMIUM_PATH || "/Applications/Brave Browser.app/Contents/MacOS/Brave Browser";
  assert(fs.existsSync(executablePath), "CHROMIUM_PATH must point to installed Chromium; these tests must run, not skip");
  const { GuestStore, GUEST_DATA_KEY, GUEST_MODE_KEY } = await import("../app/src/guest-store.mjs");
  const values = new Map();
  const storage = { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value) };
  const store = new GuestStore(storage);
  for (const [id, name] of [[101, "QA Reading"], [102, "QA Drawing"]]) {
    await store.write("tasks", { id, taskType: "CUSTOM", name, emoji: "⭐", hour: 16,
      minute: 0, daysOfWeek: "1,2,3,4,5,6,7", focusDurationMinutes: 5,
      breakDurationMinutes: 5, enabled: true, isCustom: true, childProfileId: "guest-child" });
  }
  const dist = path.resolve(__dirname, "../dist");
  const server = http.createServer((request, response) => {
    const route = new URL(request.url, "http://127.0.0.1").pathname;
    if (route === "/app/firebase-config.json") { response.writeHead(404).end(); return; }
    const file = path.resolve(dist, "." + (route.endsWith("/") ? route + "index.html" : route));
    if (!file.startsWith(dist + path.sep)) { response.writeHead(403).end(); return; }
    fs.readFile(file, (error, bytes) => {
      if (error) { response.writeHead(404).end(); return; }
      response.setHeader("Content-Type", ({ ".html": "text/html", ".js": "text/javascript", ".css": "text/css" })[path.extname(file)] || "application/octet-stream");
      response.end(bytes);
    });
  });
  await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
  t.after(() => server.close());
  const browser = await chromium.launch({ executablePath, headless: true });
  t.after(() => browser.close());
  const page = await browser.newPage({ viewport: { width, height: 844 } });
  page.setDefaultTimeout(15000);
  const origin = `http://127.0.0.1:${server.address().port}`;
  await page.route("**/*", route => route.request().url().startsWith(origin + "/") ? route.continue() : route.abort());
  await page.addInitScript(({ key, mode, data }) => {
    localStorage.setItem(key, data);
    localStorage.setItem(mode, "yes");
    const realNow = Date.now;
    window.__qaOffset = 0;
    Date.now = () => realNow() + window.__qaOffset;
  }, { key: GUEST_DATA_KEY, mode: GUEST_MODE_KEY, data: storage.getItem(GUEST_DATA_KEY) });
  const errors = [];
  page.on("pageerror", error => errors.push(error.message));
  await page.goto(origin + "/app/");
  await page.getByRole("heading", { name: "Việc của con hôm nay" }).waitFor();
  const nav = async name => page.locator(width >= 600 ? ".side-links" : ".bottom-nav").getByRole("button", { name, exact: true }).click();
  const start = async name => page.locator(".timeline-item").filter({ hasText: name }).getByRole("button").click();
  const advance = async seconds => {
    await page.evaluate(value => { window.__qaOffset += value * 1000; }, seconds);
    await page.waitForFunction(() => !document.querySelector(".timer") || !document.querySelector(".timer").textContent.includes("05:00"));
  };
  const sessions = async () => page.evaluate(key => Object.values(JSON.parse(localStorage.getItem(key)).docs.sessions), GUEST_DATA_KEY);
  t.after(() => assert.deepEqual(errors, [], "no uncaught UI errors"));
  return { page, nav, start, advance, sessions, key: GUEST_DATA_KEY };
}

for (const width of [390, 1280]) test(`active focus returns without resetting and switching saves the old session (${width}px)`, async t => {
  const { page, nav, start, advance, sessions } = await fixture(t, width);
  await start("QA Reading");
  await advance(45);
  await nav("Hôm nay");
  await page.getByRole("region", { name: "Phiên tập trung đang chạy" }).getByRole("button", { name: "Quay lại phiên tập trung" }).waitFor();
  if (process.env.UX_SCREENSHOT_DIR) {
    fs.mkdirSync(process.env.UX_SCREENSHOT_DIR, { recursive: true });
    await page.screenshot({ path: path.join(process.env.UX_SCREENSHOT_DIR, `web-active-return-${width}.png`) });
  }
  await start("QA Reading");
  await page.getByRole("heading", { name: "QA Reading", exact: true }).waitFor();
  assert.match(await page.locator(".timer").innerText(), /^04:/, "same task retains elapsed time");
  assert.equal((await sessions()).length, 0);
  await nav("Hôm nay");
  await start("QA Drawing");
  const dialog = page.getByRole("dialog", { name: "Kết thúc phiên đang chạy?" });
  if (process.env.UX_SCREENSHOT_DIR) await page.screenshot({ path: path.join(process.env.UX_SCREENSHOT_DIR, `web-confirm-switch-${width}.png`) });
  await dialog.getByRole("button", { name: "Giữ phiên hiện tại" }).click();
  await page.getByRole("heading", { name: "QA Reading", exact: true }).waitFor();
  assert.equal((await sessions()).length, 0, "cancel does not end the old session");
  await nav("Hôm nay");
  await start("QA Drawing");
  await dialog.getByRole("button", { name: "Lưu và kết thúc phiên" }).click();
  await page.getByRole("heading", { name: "QA Drawing", exact: true }).waitFor();
  const old = (await sessions())[0];
  assert.equal(old.taskId, "101"); assert.equal(old.taskTitle, "QA Reading");
  assert.equal(old.completed, false); assert(old.durationSeconds >= 45);
  assert.equal(old.childProfileId, "guest-child");
  await page.getByRole("button", { name: "Dừng sớm", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Lưu và kết thúc phiên" }).click();
  await page.getByRole("heading", { name: "Đã hoàn thành", exact: true }).waitFor();
  assert.equal((await sessions()).length, 2, "both early stops are retained exactly once");
  assert((await sessions()).every(row => row.completed === false));
});

test("storage failure retains a paused retryable session and prevents losing it during a switch", async t => {
  const { page, start, nav, advance, sessions, key } = await fixture(t);
  await start("QA Reading"); await advance(40);
  await nav("Hôm nay"); await start("QA Drawing");
  await page.evaluate(key => {
    const original = Storage.prototype.setItem;
    window.__qaFailSave = true;
    Storage.prototype.setItem = function (name, value) {
      if (name === key && window.__qaFailSave) throw new Error("QA storage unavailable");
      return original.call(this, name, value);
    };
  }, key);
  const confirm = page.getByRole("dialog").getByRole("button", { name: "Lưu và kết thúc phiên" });
  await confirm.click();
  await page.getByRole("alert").getByText("Chưa lưu được phiên. Phiên đã tạm dừng; hãy thử lại.", { exact: true }).waitFor();
  assert.equal((await sessions()).length, 0);
  await page.getByRole("heading", { name: "QA Reading", exact: true }).waitFor();
  await page.evaluate(() => { window.__qaFailSave = false; });
  await confirm.click();
  await page.getByRole("heading", { name: "QA Drawing", exact: true }).waitFor();
  assert.equal((await sessions()).length, 1);
  assert((await sessions())[0].durationSeconds >= 40);
});

test("leaving Guest asks first and saves early stop before leaving the account", async t => {
  const { page, start, advance, sessions } = await fixture(t);
  await start("QA Reading"); await advance(35);
  await page.locator(".header-actions").getByRole("button", { name: "Đăng nhập", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByRole("button", { name: "Giữ phiên hiện tại" }).click();
  await page.getByRole("heading", { name: "QA Reading", exact: true }).waitFor();
  await page.locator(".header-actions").getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await dialog.getByRole("button", { name: "Lưu và kết thúc phiên" }).click();
  await page.locator(".guest-banner").waitFor({ state: "detached" });
  const saved = await sessions();
  assert.equal(saved.length, 1); assert.equal(saved[0].completed, false);
  assert(saved[0].durationSeconds >= 35);
});

test("natural finish still saves exactly one completed session", async t => {
  const { page, start, advance, sessions } = await fixture(t);
  await start("QA Reading"); await advance(301);
  await page.getByRole("heading", { name: "Đã hoàn thành", exact: true }).waitFor();
  const saved = await sessions();
  assert.equal(saved.length, 1); assert.equal(saved[0].completed, true);
  assert.equal(saved[0].durationSeconds, 300);
});


test("paused time is excluded when explicitly ending the session", async t => {
  const { page, start, advance, sessions } = await fixture(t);
  await start("QA Reading"); await advance(40);
  await page.getByRole("button", { name: "Tạm dừng", exact: true }).click();
  await advance(120);
  await page.getByRole("button", { name: "Dừng sớm", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Lưu và kết thúc phiên" }).click();
  await page.getByRole("heading", { name: "Đã hoàn thành", exact: true }).waitFor();
  const saved = await sessions();
  assert.equal(saved.length, 1); assert.equal(saved[0].completed, false);
  assert(saved[0].durationSeconds >= 40 && saved[0].durationSeconds < 45, "the two-minute pause is not focus time");
});

test("browser navigation warns before abandoning a running focus", async t => {
  const { page, start, sessions } = await fixture(t);
  await start("QA Reading");
  const warning = page.waitForEvent("dialog");
  const click = page.locator(".header-actions .install-pill").click();
  const dialog = await warning;
  assert.equal(dialog.type(), "beforeunload");
  await dialog.dismiss(); await click;
  await page.getByRole("heading", { name: "QA Reading", exact: true }).waitFor();
  assert.equal((await sessions()).length, 0);
});


test("natural completion while switch confirmation is open dismisses the stale action", async t => {
  const { page, start, nav, advance, sessions } = await fixture(t);
  await start("QA Reading"); await advance(290);
  await nav("Hôm nay"); await start("QA Drawing");
  await page.getByRole("dialog").waitFor();
  await advance(15);
  await page.getByRole("heading", { name: "Đã hoàn thành", exact: true }).waitFor();
  assert.equal(await page.getByRole("dialog").count(), 0);
  const saved = await sessions();
  assert.equal(saved.length, 1); assert.equal(saved[0].completed, true);
  assert.equal(saved[0].taskId, "101");
});
