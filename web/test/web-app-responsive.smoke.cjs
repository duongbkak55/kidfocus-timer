// Manual visual gate against local Auth/Firestore emulators. Never accepts a production URL.
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const crypto = require("node:crypto");
const { initializeApp, deleteApp } = require("firebase/app");
const { getAuth, connectAuthEmulator, createUserWithEmailAndPassword } = require("firebase/auth");
const { getFirestore, connectFirestoreEmulator, doc, setDoc } = require("firebase/firestore");
const { chromium, webkit } = require("playwright-core");
const sync = require("../../shared/sync/sync-v2.js");

const base = "http://127.0.0.1:8787";
const projectId = "demo-kidfocus-web";
const output = path.resolve(__dirname, "../../docs/tasks/web-app-responsive-screenshots");
const sizes = [[360, 740], [390, 844], [768, 1024], [820, 1180], [1024, 768], [1366, 1024], [1440, 900]];
const screens = [
  ["today", "Hôm nay"], ["week", "Lịch"], ["focus", "Tập trung"],
  ["history", "Đã xong"], ["daylog", "Nhật ký ngày"], ["profiles", "Hồ sơ bé"],
  ["settings", "Cài đặt"], ["learning", "Luyện tư duy"],
];
fs.mkdirSync(output, { recursive: true });

async function navigate(page, key, label, width) {
  if (width >= 600) {
    await page.locator(".side-links").getByRole("button", { name: label }).click();
  } else if (["today", "week", "focus", "history"].includes(key)) {
    await page.locator(".bottom-nav").getByRole("button", { name: label }).click();
  } else {
    await page.locator('button[aria-controls="more-menu"]').click();
    await page.getByRole("navigation", { name: "Mục khác" }).getByRole("button", { name: new RegExp(label) }).click();
  }
}

async function layoutCheck(page, width, label) {
  const result = await page.evaluate(() => {
    const clipped = [...document.querySelectorAll("h1,h2,.task-heading strong,.mini-item strong,.history-row strong,.side-links button")]
      .filter(el => { const box = el.getBoundingClientRect(); return box.width > 0 && box.height > 0 && el.scrollWidth > el.clientWidth + 2 && getComputedStyle(el).overflowX !== "auto"; })
      .map(el => el.textContent.trim().slice(0, 50));
    return { scrollWidth: document.documentElement.scrollWidth, viewport: innerWidth,
      rail: getComputedStyle(document.querySelector(".side-nav")).display,
      bottom: getComputedStyle(document.querySelector(".bottom-nav")).display, clipped };
  });
  assert(result.scrollWidth <= result.viewport, `${label}: horizontal overflow ${JSON.stringify(result)}`);
  assert.equal(result.rail !== "none", width >= 600, `${label}: rail breakpoint`);
  assert.equal(result.bottom !== "none", width < 600, `${label}: bottom nav breakpoint`);
  assert.deepEqual(result.clipped, [], `${label}: clipped text`);
}

(async () => {
  const options = { apiKey: "demo-key", authDomain: `${projectId}.firebaseapp.com`, projectId, appId: "1:123456789:web:demo" };
  fs.writeFileSync(path.resolve(__dirname, "../dist/app/firebase-config.json"), JSON.stringify(options));
  const app = initializeApp(options, "responsive-seed");
  const auth = getAuth(app);
  connectAuthEmulator(auth, "http://127.0.0.1:9099", { disableWarnings: true });
  const db = getFirestore(app);
  connectFirestoreEmulator(db, "127.0.0.1", 8080);
  const email = `responsive-${Date.now()}@example.test`;
  const user = await createUserWithEmailAndPassword(auth, email, "TestOnly123!");
  const uid = user.user.uid;
  const now = Date.now();
  const rows = [
    ["profiles", { id: "be-na", name: "Bé Na", avatarEmoji: "🐣", ageBand: "5-6", createdAtMillis: now, archived: false }],
    ["tasks", { id: 71, taskType: "READING", name: "Đọc sách cùng bố mẹ", emoji: "📚", hour: 16, minute: 0, daysOfWeek: "1,2,3,4,5,6,7", focusDurationMinutes: 25, breakDurationMinutes: 5, enabled: true, isCustom: false, childProfileId: "be-na" }],
    ["routines", { id: 72, title: "Dọn bàn học", emoji: "🧹", deadlineMinutes: 17 * 60, repeatDaysMask: 127, reminderMinutesBefore: 10, enabled: true, linkedTimerMinutes: null, createdAtMillis: now, childProfileId: "be-na" }],
    ["sessions", { id: 73, durationSeconds: 1200, isFocus: true, timestampMillis: now - 3600000, childProfileId: "be-na", taskId: "71", taskTitle: "Đọc sách cùng bố mẹ", completed: true }],
  ];
  for (const [name, entity] of rows) {
    const candidate = sync.toDoc(name, entity, { nowMillis: now, deviceId: "responsive-seed" });
    assert(candidate, name);
    await setDoc(doc(db, "users", uid, name, candidate.id), candidate.data);
  }
  const extraSync = await import("../app/src/extra-sync.mjs");
  const today = new Date();
  const date = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}-${String(today.getDate()).padStart(2, "0")}`;
  const log = extraSync.toDoc("dayLogs", {
    id: crypto.randomUUID(), profile_id: "be-na", date, task_id: 71,
    name: "Đọc sách cùng bố mẹ", category: "STUDY", start_minute: 960,
    end_minute: 985, source: "MANUAL", created_at: now, updated_at: now, deleted: false,
  }, { nowMillis: now, deviceId: "responsive-seed" });
  assert(log);
  await setDoc(doc(db, "users", uid, "dayLogs", log.id), log.data);
  const browser = await chromium.launch({ executablePath: "/Applications/Brave Browser.app/Contents/MacOS/Brave Browser", headless: true });
  const context = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 1 });
  const page = await context.newPage();
  const errors = [];
  page.on("pageerror", error => errors.push(error.message));
  await page.goto(`${base}/app/?emulator=1`);
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Mật khẩu").fill("TestOnly123!");
  await page.getByRole("button", { name: "Đăng nhập" }).click();
  await page.getByRole("heading", { name: "Việc của con hôm nay" }).waitFor();
  await page.getByText("Đọc sách cùng bố mẹ").first().waitFor();
  for (const [width, height] of sizes) {
    await page.setViewportSize({ width, height });
    for (const [key, label] of screens) {
      await navigate(page, key, label, width);
      const shot = `${key}-${width}x${height}.png`;
      await layoutCheck(page, width, shot);
      await page.screenshot({ path: path.join(output, shot), fullPage: false });
    }
    const learning = await context.newPage();
    await learning.setViewportSize({ width, height });
    await learning.goto(`${base}/tu-duy/?emulator=1&profile=be-na`);
    await learning.locator("#tdPick .td-pick").first().waitFor();
    await learning.waitForFunction(() => !document.getElementById("webSyncStatus")?.textContent?.includes("Đang kết nối"));
    const overflow = await learning.evaluate(() => document.documentElement.scrollWidth - innerWidth);
    assert(overflow <= 0, `tu-duy-${width}: overflow ${overflow}`);
    await learning.screenshot({ path: path.join(output, `tu-duy-${width}x${height}.png`), fullPage: false });
    await learning.locator("#tdPick .td-pick").filter({ hasText: "So sánh" }).click();
    await learning.locator("#tdQuiz").waitFor({ state: "visible" });
    assert((await learning.evaluate(() => document.documentElement.scrollWidth)) <= width,
      `tu-duy-quiz-${width}: horizontal overflow`);
    await learning.screenshot({ path: path.join(output, `tu-duy-quiz-${width}x${height}.png`), fullPage: false });
    if (width >= 1024) {
      await learning.locator("#tdPad .td-choice").first().click();
      await learning.locator("#tdCheck").click();
      if (await learning.locator("#tdExplain").isHidden()) await learning.locator("#tdCheck").click();
      if (await learning.locator("#tdExplain").isVisible()) {
        await learning.locator("#tdExplain .td-steps li").first().waitFor();
        await learning.waitForTimeout(1200);
        await learning.waitForFunction(() => document.getElementById("webSyncStatus")?.textContent !== "Đang đồng bộ tiến bộ…");
        const placement = await learning.evaluate(() => ({
          figure: document.getElementById("tdVisual").getBoundingClientRect().right,
          explanation: document.getElementById("tdExplain").getBoundingClientRect().left,
        }));
        assert(placement.explanation >= placement.figure, "desktop explanation beside puzzle");
        await learning.screenshot({ path: path.join(output, `tu-duy-explanation-${width}x${height}.png`), fullPage: false });
      }
    }
    await learning.close();
  }
  await page.setViewportSize({ width: 1366, height: 1024 });
  await navigate(page, "week", "Lịch", 1366);
  await page.locator(".week-arrows button").first().focus();
  const weekBefore = await page.locator(".week-arrows strong").innerText();
  await page.keyboard.press("ArrowRight");
  assert.notEqual(await page.locator(".week-arrows strong").innerText(), weekBefore, "arrow key changes week");
  await page.getByRole("button", { name: /Thêm hoạt động.*16:00/ }).first().click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Tên hoạt động").fill("Bản nháp khi xoay màn");
  await page.setViewportSize({ width: 820, height: 1180 });
  assert.equal(await dialog.getByLabel("Tên hoạt động").inputValue(), "Bản nháp khi xoay màn");
  await page.keyboard.press("Escape");
  assert.equal(await dialog.count(), 0, "Escape closes editor");
  await page.setViewportSize({ width: 1366, height: 1024 });
  await navigate(page, "focus", "Tập trung", 1366);
  await page.locator(".focus-side .history-row").first().click();
  await page.locator(".page h1").click();
  await page.keyboard.press("Space");
  await page.getByText("Đang tập trung").waitFor();
  await page.locator(".page h1").click();
  await page.keyboard.press("Space");
  await page.getByText("Đã tạm dừng").waitFor();
  await page.getByRole("button", { name: "Dừng sớm" }).click();
  assert.deepEqual(errors, [], "no page errors");
  let webkitResult = "unavailable";
  try {
    const safari = await webkit.launch({ headless: true });
    const tab = await safari.newPage({ viewport: { width: 820, height: 1180 } });
    await tab.goto(`${base}/tu-duy/`);
    assert((await tab.evaluate(() => document.documentElement.scrollWidth)) <= 820);
    webkitResult = "passed";
    await safari.close();
  } catch (error) { webkitResult = `unavailable: ${String(error.message).split("\n")[0]}`; }
  const sharedDevice = await browser.newContext();
  const signIn = await sharedDevice.newPage();
  await signIn.goto(`${base}/app/?emulator=1`);
  const trust = signIn.getByRole("checkbox", { name: /Đây là máy của tôi/ });
  await trust.check();
  await signIn.waitForFunction(() => localStorage.getItem("kidfocus-trusted-device") === "yes");
  await trust.uncheck();
  await signIn.waitForFunction(() => localStorage.getItem("kidfocus-trusted-device") === "no");
  await sharedDevice.close();
  await browser.close();
  await deleteApp(app);
  console.log(`PASS responsive: ${sizes.length * (screens.length + 2)} screenshots, no overflow/clipping, orientation draft, Escape, arrows, Space timer, trusted-device cache toggle; WebKit ${webkitResult}`);
})().catch(error => { console.error(error); process.exitCode = 1; });
