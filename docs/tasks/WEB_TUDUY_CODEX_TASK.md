# Codex task: "Cho bé thử Luyện tư duy" — web demo page (marketing)

Worktree: /Users/duongnguyen/Projects/KidFocusTimer-tuduy, branch feat/web-tuduy (from main 9e48ab7).
Goal: a public web page where a parent can let the child play Luyện tư duy right in the browser, then install the app.
Claude reviews (G2), checks it on the Pixel 9 browser (G3) and deploys it to the oci server next to /beta/.
Do not push, merge or deploy.

## 0. Hard rules
- Reuse the shipped engine and UI UNCHANGED: shared/learning/www/tuduy/* (core, phase2, phase2b, arithmetic,
  visual-layout, ui, css, icons). No fork, no copy-paste edits. A build script copies them into the output folder.
  If the UI needs a hook to run outside the hub, add the smallest backward-compatible option to tuduy-ui.js and keep
  all existing tests (96 JS, 361 Android) green.
- No tracking, cookies, accounts, analytics, ads, third-party scripts or fonts, and no network requests except
  same-origin files. No service worker. No child data leaves the device; progress only in localStorage key
  `tuduy.web.v1` (must not collide with the hub's `tuduy.v1`).
- Child-facing and parent-facing text follows the content audit: no "thi", "kiểm tra", "đề", "lớp 1 sẵn sàng",
  no promises of results. Say what it trains (quan sát, quy luật, so sánh, suy luận), "không phải luyện thi".
- Vietnamese first; page lang="vi".

## 1. Files
- `web/tu-duy/index.html` — page shell + thin glue (target < 150 lines of JS).
- `web/build-tuduy.sh` (or .mjs) — writes `web/dist/tu-duy/` = index.html + copied tuduy assets. Idempotent.
- `web/dist/` is build output: gitignore it.

## 2. Page
- Header: KidFocus logo/name + one line for parents ("Trò chơi tư duy cho bé 5–7 tuổi, mỗi lượt 5 câu, có giải thích").
- Band switch: "5–6 tuổi" (max 10) / "Lớp 1" (max 20); default 5–6. Implement by setting the same `ageBand` global the
  UI reads ('5-6' / 'l1'); triSum/addTable only appear for Lớp 1 as in the app.
- Reuse the hub's #tuduy markup (same element ids: tdChooser, tdPick, tdQuiz, tdPrompt, tdVisual, tdPad, tdCheck,
  tdNext, tdFb, tdExplain, tdDone…) so tuduy-ui.js works as-is.
- Shims the UI calls through `call()`: speakText → Web Speech API (vi-VN voice if present, else silent, never throw);
  sndTap/sndRight/sndWrong → short WebAudio beeps (muted until first user tap, mute toggle); confetti → small CSS burst.
  "Nghe giải thích"/🔊 must not error on browsers without speechSynthesis.
- Session length 5 câu on web (keep 8 in the app). Done screen: stars + score, then
  primary CTA "Tải KidFocus để luyện tiếp mỗi ngày" → `/beta/`, secondary "Chơi thêm 5 câu".
  Short parent note under the CTA: app adds daily routine timer, tiến bộ theo từng dạng, làm lại câu chưa đúng.
- Footer: link to privacy/legal page already used by /beta/, contact mailto from /beta/.
- Layout: works 360–412 px phones and desktop (content max-width ~480 px, centered). Tap targets ≥ 44 px.
- Meta: <title>Cho bé thử Luyện tư duy — KidFocus</title>, description, og:title/og:description/og:image
  (`og.png` 1200×630 made from a real screenshot of the page, no child photos), theme-color, favicon.
- Size budget: dist/tu-duy ≤ 1.5 MB total.

## 3. Also
- Add a button on the /beta/ page source (kept in repo as `web/beta/index.html`, copy the current one from
  Claude's staging folder `Ý tưởng dự án/kidfocus-tuduy-staging/web-beta-index.html`): "Cho bé chơi thử ngay trên web →" → `/tu-duy/`.

## 4. Tests (shared/learning/tests/web-tuduy.test.js or web/test/)
- dist engine/UI/css files are byte-identical to shared/learning/www/tuduy/* (hash compare).
- index.html: no external http(s) URL except https://play.google.com, https://groups.google.com and mailto;
  no `<script src="http`; uses `tuduy.web.v1`.
- Forbidden-word regex over all visible page text (reuse the existing exam-framing regex).
- Headless run (Playwright with executablePath '/opt/pw-browsers/chromium' is NOT available on the Mac — use whatever
  headless Chromium you have; if none, a jsdom smoke test): open page, start a session for each band, answer 5 questions,
  reach the done screen, CTA href = /beta/. Screenshots 390×844: chooser, one question, one explanation, done screen.

## 5. Deliverables
Code on feat/web-tuduy, docs/tasks/WEB_TUDUY_CODEX_REPORT.md (what changed in tuduy-ui.js if anything, sizes,
test results, screenshots). Then Claude does G2 + G3 and deploys `web/dist/tu-duy` + updated `/beta/` to oci.
