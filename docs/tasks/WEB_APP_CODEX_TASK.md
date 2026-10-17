# Codex task: KidFocus web app (W1 + W2) and sync v2 in the Android app

Worktree /Users/duongnguyen/Projects/KidFocusTimer-tuduy, branch feat/web-app (from feat/web-tuduy).
Read first: docs/tasks/SYNC_V2_SPEC.md and shared/sync/sync-v2.js (+ fixtures, tests) — Claude-owned; do NOT change
their behaviour. If you think the spec is wrong, stop and write the question in your report.
Do not push, merge, deploy, or change Firebase console settings. Claude does G2/G3 and deploys after Duong approves.

## Step A — app on sync v2 (report after A, before starting B)
1. Kotlin `SyncV2Model` = port of sync-v2.js. Unit test loads shared/sync/fixtures/*.json (copied by a Gradle task
   into test resources, not duplicated in git) and must match `v1-expected.json` and `conflicts.json`.
2. Replace CloudSyncManager's whole-snapshot flow with per-collection listeners + per-item transactional writes per
   the spec; keep CloudSyncStatus/UI contract. Room stays the local source of truth; writes mark items dirty and
   upload in the background; remote changes merge item by item (no deleteAll/insertAll).
3. Explicit random ids (`newId`) for tasks, routines, sessions created after the upgrade; tombstones for deletes
   (Room: soft-delete column or a pending-delete table — your call, migration with a Room schema test).
4. One-time migration v1 → v2 exactly as the spec; old-app warning state exposed to the UI.
5. Keep alarms in sync: any remote task/routine change reschedules only that item.
6. Tests: fixtures, migration, two simulated devices editing different items and the same item (Firestore emulator
   if available, else fakes), offline queue replay, 361+ Android tests, lint, assembleDebug.

## Step B — web app at kidfocus.web.app/app/ (W1)
Stack: Vite + TypeScript + Preact, Firebase modular SDK (auth, firestore) bundled; no other runtime deps, no CDN,
no analytics. Source `web/app/`, built into `web/dist/app/` by the existing web build; Hosting rewrite `/app/**` →
`/app/index.html` (add to firebase.json target `demo`). Vietnamese UI, same visual language as /tu-duy/.
Firebase web config comes from `web/app/firebase-config.json` (Claude creates it — public values, not secrets).
- Auth: email+password and Google (popup, redirect fallback on mobile), password reset, sign-out. Shared-computer notice
  on the sign-in screen; Firestore offline persistence OFF by default (toggle "Đây là máy của tôi" turns it on).
- Profile switcher (child profiles from `profiles`).
- Hôm nay: timeline of today's tasks + routines for the selected child, mark routine done (writes a completion with the
  same status logic as the app: ON_TIME if before deadline, else LATE), start focus for a task.
- Lịch: week view; create / edit / disable / delete tasks and routines with the same limits as the app.
- Tập trung: focus timer for the browser tab (Wake Lock if available, sound at end, Notification only if the parent
  allows). Writes a `sessions` doc on finish or early stop (`completed` false), with taskId/taskTitle like the app.
  Visible note: "Trên web không có báo thức khi đóng tab — dùng app để nhắc giờ."
- Đã hoàn thành: same data as the app's Completed/FocusHistory screen (week bars + list).
- Sync status chip (Đang đồng bộ / Đã đồng bộ / Lỗi + retry) and the old-app warning from the spec.

## Step C — W2
- Nhật ký ngày: read/write `dayLogs` in the app's existing format (DayLogCloud.kt) — same validation.
- Hồ sơ bé: add / rename / avatar / age band / archive.
- Cài đặt: the v1 settings keys (focus/break length, daily goal, sound, calm mode, extend focus), stored in `sync/settings`.
- Luyện tư duy when signed in: the /tu-duy/ page uses `learning/{profileId}_tuduy` instead of localStorage (same
  `{stats, review}` shape, LWW by updatedAtMillis); app syncs the same doc from its WebView store.
- Out of scope: AI features, Premium/billing, photos (`photoUri` is device-local), alarms/overlay, parent PIN.

## Tests & deliverables
- `npm --prefix web test`: unit tests for every write path (task/routine/completion/session/profile/settings/dayLog/
  learning) go through `toDoc`/`pickWinner` (no hand-built docs); Firestore emulator tests for rules (other uid denied).
- Playwright smoke (Chromium you have on the Mac): sign-in against the emulator, create a task, mark a routine, run a
  1-minute focus, see it in history; 360 and 412 px layouts, no horizontal overflow, tap targets ≥ 44 px.
- `node --test shared/learning/tests/*.test.js shared/sync/test/*.test.js` green; 31 original tuduy tests unchanged.
- Report docs/tasks/WEB_APP_CODEX_REPORT.md: what changed in the app, migration notes, screenshots 390×844 of each screen.
