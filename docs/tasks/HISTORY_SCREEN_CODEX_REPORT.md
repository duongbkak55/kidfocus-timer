# Focus history — Codex implementation report

Branch: `feat/focus-history` from `main` (`a02be1a`). No push or Play upload.

## Result

- Added a child-readable **Completed** screen with seven local-calendar-day focus bars, weekly totals, daily entries, completion/early-stop status, routine check-offs, and a friendly empty state. It filters sessions and routines to the active child profile.
- Both Home stat cards, the daily-goal card and its “Review ›” link, and Celebration open the screen without a parent PIN. Only the PIN-gated Parent Settings route supplies single-session and all-focus-session delete controls. Routine check-offs remain read-only.
- Focus sessions now snapshot the selected task ID/title when the timer starts. A natural finish or **Done early** records `completed=true` with actual elapsed time; Stop records elapsed time and `completed=false`. A one-time custom activity keeps its entered title in history. The new fields are included in cloud session serialization, with local metadata preserved when reading older remote documents.
- All new copy is present in Vietnamese and English.

## Schema decision

The task says Room v5→v6, but `main` already uses v6 for `day_log_entries` and `sessions.scheduled_task_id`. Reusing v6 would conflict with installed databases. The smallest additive safe path is v6→v7, adding nullable `task_id`/`task_title` and `completed INTEGER NOT NULL DEFAULT 1`. The v5→v6 migration is unchanged. Tests cover both an existing v6 row and a v5 fixture migrating through v6 to v7 without losing session data.

## Files

- Data: `SessionEntity`, `SessionDatabase`, `SessionDao`, `SessionRepository`, `DataModule`, exported Room schema 7.
- Recording and sync: `TimerDayLogRecorder`, `RecordSessionUseCase`, `TimerService`, `CloudSyncManager`.
- UI: `FocusHistory` grouping model, `FocusHistoryViewModel`, `FocusHistoryScreen`, `HomeScreen`, `CelebrationScreen`, `ParentSettingsScreen`, `NavRoutes`, `AppNavigation`, vi/en strings.
- Tests: migration, recording/status, Asia/Ho_Chi_Minh midnight grouping, Compose entry points and screen states, device image fixture.

## Combined Focus flow

The earlier Focus changes were on `codex/focus-task-timing`, separate from this history branch. Merged that branch here so **Done early**, one-time custom activities, Skip, timing review, and the history screen are present in one APK. The merge resolves the timer recording path by using actual elapsed seconds for early completion and retaining the session title/status. A child can now tap a task row on Home to choose its duration and start Focus, or tap the full task card in today's schedule to start that task immediately, regardless of its scheduled order. The Home task visual is 48 dp for easier recognition. The parent schedule remains unchanged.

## Verification

- `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ./gradlew testDebugUnitTest assembleDebug lintDebug` — PASS: **350 tests**, 0 failures, 0 errors, 0 skipped; debug APK built; lint clean. Includes task-card tap and one-time title tests.
- `npm --prefix functions test` — PASS: **173 tests**. `npm --prefix functions run lint` — PASS. The worktree first needed `npm ci --prefix functions`; dependencies are ignored, with no manifest or lockfile change.
- `./gradlew assembleDebugAndroidTest` — PASS.
- Pixel 9 (Android 17), synthetic screenshot instrumented test — PASS: **1 test** on the history commit. It renders only demo activities for “Bé Na”; no account or stored child data is read. The combined APK is built, but ADB currently lists no connected device and discovers no wireless service, so on-device installation of the combined branch is pending reconnection.
- `git diff --check` — PASS.
- The existing session policy prunes records older than 30 days when a new session is recorded; this change leaves that policy intact.

## Screenshots

- [Empty](focus-history/screenshots/empty.png)
- [Filled](focus-history/screenshots/filled.png)

The Pixel was set to English, so the screenshots show English UI labels. Vietnamese strings are implemented and covered by the resource build.
