// Run only against local Auth + Firestore emulators and a local server for web/dist.
// This test intentionally never accepts a production project ID.
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { initializeApp, deleteApp } = require("firebase/app");
const {
  getAuth,
  connectAuthEmulator,
  createUserWithEmailAndPassword,
} = require("firebase/auth");
const {
  getFirestore,
  connectFirestoreEmulator,
  doc,
  setDoc,
  getDoc,
} = require("firebase/firestore");
const { chromium } = require("playwright-core");
const sync = require("../../shared/sync/sync-v2.js");

const PROJECT = "demo-kidfocus-web";
const BASE =
  process.env.KIDFOCUS_SMOKE_BASE || "http://127.0.0.1:8787/app/?emulator=1";
const SCREENSHOTS = path.resolve(
  __dirname,
  "../../docs/tasks/web-app-screenshots",
);
if (!BASE.startsWith("http://127.0.0.1:") || !BASE.includes("emulator=1"))
  throw new Error("Local emulator URL required");
fs.mkdirSync(SCREENSHOTS, { recursive: true });

(async () => {
  const app = initializeApp(
    {
      apiKey: "demo-key",
      authDomain: `${PROJECT}.firebaseapp.com`,
      projectId: PROJECT,
      appId: "1:123456789:web:demo",
    },
    "web-smoke",
  );
  const auth = getAuth(app);
  connectAuthEmulator(auth, "http://127.0.0.1:9099", { disableWarnings: true });
  const db = getFirestore(app);
  connectFirestoreEmulator(db, "127.0.0.1", 8080);
  const testEmail = `web-smoke-${Date.now()}@example.test`;
  const credential = await createUserWithEmailAndPassword(
    auth,
    testEmail,
    "TestOnly123!",
  );
  const uid = credential.user.uid;
  const profile = sync.toDoc(
    "profiles",
    {
      id: "be-na",
      name: "Bé Na",
      avatarEmoji: "🐣",
      ageBand: "5-6",
      createdAtMillis: Date.now(),
      archived: false,
    },
    { nowMillis: Date.now(), deviceId: "smoke-seed" },
  );
  await setDoc(doc(db, "users", uid, "profiles", profile.id), profile.data);
  await assert.rejects(
    getDoc(doc(db, "users", "someone-else", "profiles", "be-na")),
    /permission/i,
    "Firestore must deny another uid",
  );

  const browser = await chromium.launch({
    executablePath:
      "/Applications/Brave Browser.app/Contents/MacOS/Brave Browser",
    headless: true,
  });
  const page = await browser.newPage({
    viewport: { width: 390, height: 844 },
    deviceScaleFactor: 1,
  });
  const pageErrors = [];
  page.on("pageerror", (error) => pageErrors.push(error.message));
  await page.goto(BASE);
  await page.getByLabel("Email").fill(testEmail);
  await page.getByLabel("Mật khẩu").fill("TestOnly123!");
  await page.getByRole("button", { name: "Đăng nhập" }).click();
  await page.getByRole("heading", { name: "Việc của con hôm nay" }).waitFor();
  await page.waitForFunction(
    () => document.querySelector(".profile-picker select")?.value === "be-na",
  );

  await page
    .locator(".bottom-nav")
    .getByRole("button", { name: /Lịch/ })
    .click();
  await page.getByRole("button", { name: "+ Hoạt động" }).click();
  let dialog = page.getByRole("dialog");
  await dialog.getByLabel("Tên hoạt động").fill("Đọc sách cùng bố mẹ");
  await dialog.getByLabel("T7").check();
  await dialog.getByRole("button", { name: "Lưu hoạt động" }).click();
  await page.getByRole("button", { name: "+ Việc hằng ngày" }).click();
  dialog = page.getByRole("dialog");
  await dialog.getByLabel("Tên việc").fill("Dọn bàn học");
  await dialog.getByRole("button", { name: "Lưu việc" }).click();
  await page.getByRole("button", { name: "+ Hoạt động" }).waitFor();
  await page.locator(".toast button").click();
  await page.screenshot({
    path: path.join(SCREENSHOTS, "w1-lich-390x844.png"),
    fullPage: false,
  });

  await page
    .locator(".bottom-nav")
    .getByRole("button", { name: /Hôm nay/ })
    .click();
  await page.getByText("Dọn bàn học").waitFor();
  await page.getByRole("button", { name: "Đánh dấu xong" }).click();
  await page.getByText(/Đúng giờ|Đã hoàn thành/).waitFor();
  await page.locator(".toast button").click();
  await page.screenshot({
    path: path.join(SCREENSHOTS, "w1-hom-nay-390x844.png"),
    fullPage: false,
  });

  await page
    .locator(".bottom-nav")
    .getByRole("button", { name: /Tập trung/ })
    .click();
  await page
    .getByLabel("Hoạt động")
    .selectOption({ label: "⭐ Đọc sách cùng bố mẹ" });
  await page.getByLabel("Thời lượng phiên (phút)").fill("1");
  await page.getByRole("button", { name: "Bắt đầu" }).click();
  await page.screenshot({
    path: path.join(SCREENSHOTS, "w1-tap-trung-390x844.png"),
    fullPage: false,
  });
  await page
    .getByRole("heading", { name: "Đã hoàn thành" })
    .waitFor({ timeout: 70000 });
  await page.getByText("Đọc sách cùng bố mẹ").waitFor();
  await page.locator(".toast button").click();
  await page.screenshot({
    path: path.join(SCREENSHOTS, "w1-da-hoan-thanh-390x844.png"),
    fullPage: false,
  });

  for (const width of [360, 412]) {
    await page.setViewportSize({ width, height: 844 });
    const layout = await page.evaluate(() => ({
      scroll: document.documentElement.scrollWidth,
      viewport: innerWidth,
      smallButtons: [...document.querySelectorAll("button")]
        .filter((button) => {
          const style = getComputedStyle(button);
          const box = button.getBoundingClientRect();
          return (
            style.display !== "none" &&
            box.width > 0 &&
            box.height > 0 &&
            (box.width < 44 || box.height < 44)
          );
        })
        .map((button) => button.textContent?.trim()),
    }));
    assert.ok(
      layout.scroll <= layout.viewport,
      `${width}px horizontal overflow: ${JSON.stringify(layout)}`,
    );
    assert.deepEqual(
      layout.smallButtons,
      [],
      `${width}px tap targets too small`,
    );
  }
  assert.deepEqual(pageErrors, [], "No page errors");
  await browser.close();
  await deleteApp(app);
  console.log(
    "PASS: emulator auth, owner-only rules, task/routine writes, completion, 1-minute focus, history, 360/412 layouts.",
  );
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
