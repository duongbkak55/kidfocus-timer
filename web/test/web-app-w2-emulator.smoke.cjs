// Only local Auth/Firestore emulators; never accepts a production project or URL.
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
  collection,
  doc,
  setDoc,
  getDoc,
  getDocs,
} = require("firebase/firestore");
const { chromium } = require("playwright-core");
const sync = require("../../shared/sync/sync-v2.js");

const projectId = "demo-kidfocus-web";
const base = "http://127.0.0.1:8787";
const output = path.resolve(__dirname, "../../docs/tasks/web-app-screenshots");
fs.mkdirSync(output, { recursive: true });
const options = {
  apiKey: "demo-key",
  authDomain: projectId + ".firebaseapp.com",
  projectId,
  appId: "1:123456789:web:demo",
};

async function emulatorClient(name) {
  const app = initializeApp(options, name);
  const auth = getAuth(app);
  connectAuthEmulator(auth, "http://127.0.0.1:9099", { disableWarnings: true });
  const db = getFirestore(app);
  connectFirestoreEmulator(db, "127.0.0.1", 8080);
  return { app, auth, db };
}

(async () => {
  const owner = await emulatorClient("w2-owner");
  const email = "w2-" + Date.now() + "@example.test";
  const user = await createUserWithEmailAndPassword(
    owner.auth,
    email,
    "TestOnly123!",
  );
  const uid = user.user.uid;
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
    { nowMillis: Date.now(), deviceId: "w2-seed" },
  );
  await setDoc(
    doc(owner.db, "users", uid, "profiles", profile.id),
    profile.data,
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
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await page.goto(base + "/app/?emulator=1");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Mật khẩu").fill("TestOnly123!");
  await page.getByRole("button", { name: "Đăng nhập" }).click();
  await page.getByRole("heading", { name: "Việc của con hôm nay" }).waitFor();
  await page.waitForFunction(
    () => document.querySelector(".profile-picker select")?.value === "be-na",
  );
  async function more(name) {
    await page.locator('button[aria-controls="more-menu"]').click();
    await page
      .getByRole("navigation", { name: "Mục khác" })
      .getByRole("button", { name })
      .click();
  }
  async function shot(name) {
    await page.screenshot({ path: path.join(output, name), fullPage: false });
    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - innerWidth,
    );
    assert(overflow <= 0, name + " horizontal overflow: " + overflow);
  }

  await more("🐣 Hồ sơ bé");
  await page.getByRole("heading", { name: "Mỗi bé một nhịp riêng" }).waitFor();
  await shot("w2-ho-so-390x844.png");
  await page.getByRole("button", { name: "+ Thêm hồ sơ" }).click();
  let dialog = page.getByRole("dialog");
  await dialog.getByLabel("Tên bé").fill("Bé Bin");
  await dialog.getByLabel("Độ tuổi").selectOption("l1");
  await dialog.getByRole("button", { name: "Lưu hồ sơ" }).click();
  await page.getByRole("button", { name: /Bé Bin/ }).waitFor();
  assert(
    (await getDocs(collection(owner.db, "users", uid, "profiles"))).docs.some(
      (row) => row.data().name === "Bé Bin",
    ),
  );

  await more("⚙️ Cài đặt");
  await page.getByRole("heading", { name: "Nhịp tập trung phù hợp" }).waitFor();
  await shot("w2-cai-dat-390x844.png");
  await page.getByLabel("Tập trung mặc định (phút)").fill("30");
  await page.getByRole("button", { name: "Lưu cài đặt" }).click();
  await page.getByText("Đã lưu cài đặt.").waitFor();
  assert.equal(
    (await getDoc(doc(owner.db, "users", uid, "sync", "settings"))).data()
      .focusDurationMinutes,
    30,
  );

  await more("📝 Nhật ký ngày");
  await page.getByRole("heading", { name: "Thực tế của con" }).waitFor();
  await page.getByRole("button", { name: "+ Ghi hoạt động" }).click();
  dialog = page.getByRole("dialog");
  await dialog.getByLabel("Tên hoạt động").fill("Đọc truyện cùng mẹ");
  await dialog.getByLabel("Bắt đầu").fill("16:00");
  await dialog.getByLabel("Kết thúc (nếu có)").fill("16:25");
  await dialog.getByRole("button", { name: "Lưu nhật ký" }).click();
  await page.getByText("Đọc truyện cùng mẹ").waitFor();
  await shot("w2-nhat-ky-390x844.png");
  const dayLogDocs = await getDocs(
    collection(owner.db, "users", uid, "dayLogs"),
  );
  const entry = dayLogDocs.docs.find(
    (row) => row.data().name === "Đọc truyện cùng mẹ",
  );
  assert(
    entry &&
      entry.data().source === "MANUAL" &&
      entry.data().start_minute === 960,
  );
  await page.getByRole("button", { name: /Đọc truyện cùng mẹ/ }).click();
  await page.getByRole("button", { name: "Xoá ghi chép" }).click();
  await page.getByRole("button", { name: "Xác nhận xoá ghi chép" }).click();
  await page.getByText("Đã xoá ghi chép.").waitFor();
  assert.equal((await getDoc(entry.ref)).data().deleted, true);

  await more("🧠 Luyện tư duy");
  await page.getByRole("heading", { name: "Chơi mà nghĩ" }).waitFor();
  await shot("w2-luyen-tu-duy-link-390x844.png");
  await page.getByRole("link", { name: "Mở Luyện tư duy" }).click();
  await page.getByText("Đã đồng bộ tiến bộ.").waitFor({ timeout: 15000 });
  await shot("w2-luyen-tu-duy-390x844.png");
  await page.locator("#tdPick .td-pick").filter({ hasText: "So sánh" }).click();
  const beforeCloudUpdate = await page.locator("#tdCount").innerText();
  await page.evaluate(() =>
    window.TuDuyUI.applyStore({ stats: { compare: { level: 3 } }, review: [] }),
  );
  assert.equal(
    await page.locator("#tdQuiz").isVisible(),
    true,
    "cloud update does not interrupt a question",
  );
  assert.equal(await page.locator("#tdCount").innerText(), beforeCloudUpdate);
  await page.locator("#tdPad .td-choice").first().click();
  await page.locator("#tdCheck").click();
  if (await page.locator("#tdNext").isHidden())
    await page.locator("#tdCheck").click();
  await page.waitForFunction(async () => {
    const el = document.getElementById("webSyncStatus");
    return el?.textContent === "Đã đồng bộ tiến bộ.";
  });
  const learning = doc(owner.db, "users", uid, "learning", "be-na_tuduy");
  await page.waitForTimeout(1000);
  assert((await getDoc(learning)).exists(), "answer writes learning document");

  const outsider = await emulatorClient("w2-outsider");
  await createUserWithEmailAndPassword(
    outsider.auth,
    "outside-" + Date.now() + "@example.test",
    "TestOnly123!",
  );
  for (const target of [
    doc(outsider.db, "users", uid, "sync", "settings"),
    doc(outsider.db, "users", uid, "dayLogs", entry.id),
    doc(outsider.db, "users", uid, "learning", "be-na_tuduy"),
  ]) {
    await assert.rejects(getDoc(target), /permission/i);
  }
  assert.deepEqual(errors, [], "no browser page errors");
  await browser.close();
  await deleteApp(owner.app);
  await deleteApp(outsider.app);
  console.log(
    "PASS W2: profiles, settings, dayLog, learning sync, owner rules, 390×844 screenshots.",
  );
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
