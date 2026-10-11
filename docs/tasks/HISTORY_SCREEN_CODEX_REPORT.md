# Focus history — Codex implementation report

Branch: `feat/focus-history` from `main` (`a02be1a`). No push or Play upload.

## Result

- Added a child-readable **Completed** screen with seven local-calendar-day focus bars, weekly totals, daily entries, completion/early-stop status, routine check-offs, and a friendly empty state. It filters sessions and routines to the active child profile.
- Both Home stat cards, the daily-goal card and its “Review ›” link, and Celebration open the screen without a parent PIN. Only the PIN-gated Parent Settings route supplies single-session and all-focus-session delete controls. Routine check-offs remain read-only.
- Focus sessions now snapshot the selected task ID/title when the timer starts. A natural finish records `completed=true`; an early Stop records elapsed time and `completed=false`. Free focus keeps a null title and uses the localized fallback. The new fields are included in cloud session serialization, with local metadata preserved when reading older remote documents.
- All new copy is present in Vietnamese and English.

## Schema decision

The task says Room v5→v6, but `main` already uses v6 for `day_log_entries` and `sessions.scheduled_task_id`. Reusing v6 would conflict with installed databases. The smallest additive safe path is v6→v7, adding nullable `task_id`/`task_title` and `completed INTEGER NOT NULL DEFAULT 1`. The v5→v6 migration is unchanged. Tests cover both an existing v6 row and a v5 fixture migrating through v6 to v7 without losing session data.

## Files

- Data: `SessionEntity`, `SessionDatabase`, `SessionDao`, `SessionRepository`, `DataModule`, exported Room schema 7.
- Recording and sync: `TimerDayLogRecorder`, `RecordSessionUseCase`, `TimerService`, `CloudSyncManager`.
- UI: `FocusHistory` grouping model, `FocusHistoryViewModel`, `FocusHistoryScreen`, `HomeScreen`, `CelebrationScreen`, `ParentSettingsScreen`, `NavRoutes`, `AppNavigation`, vi/en strings.
- Tests: migration, recording/status, Asia/Ho_Chi_Minh midnight grouping, Compose entry points and screen states, device image fixture.

## Verification

- `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ./gradlew testDebugUnitTest assembleDebug lintDebug` — PASS: **341 tests**, 0 failures, 0 errors, 0 skipped; debug APK built; lint clean.
- `./gradlew assembleDebugAndroidTest` — PASS.
- Pixel 9 (Android 17), synthetic screenshot instrumented test — PASS: **1 test**. It renders only demo activities for “Bé Na”; no account or stored child data is read.
- `git diff --check` — PASS.
- The existing session policy prunes records older than 30 days when a new session is recorded; this change leaves that policy intact.

## Screenshots

- [Empty](focus-history/screenshots/empty.png)
- [Filled](focus-history/screenshots/filled.png)

The Pixel was set to English, so the screenshots show English UI labels. Vietnamese strings are implemented and covered by the resource build.
