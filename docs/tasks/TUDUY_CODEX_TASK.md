# Codex task: finish 'Luyện tư duy' + 5–6 tuổi band (branch feat/learning-tuduy)

Worktree: /Users/duongnguyen/Projects/KidFocusTimer-tuduy (branch feat/learning-tuduy, based on main f948bcb).
Claude already committed 2d08e7a: web module shared/learning/www/tuduy/*, index.html wiring, band '5-6' in the
web AGE_BANDS, service-worker v2, tests shared/learning/tests/tuduy-content.test.js (29 pass).
DO NOT edit shared/learning/www/tuduy/* or tuduy-content.test.js (Claude owns content/validation). Report bugs instead.

## 1. Native: accept age band '5-6' everywhere (required, otherwise the band reverts to 4-5)
Today these hard-code {2-3, 4-5, l1, l2, l3} or default to 4-5:
- ui/viewmodel/LearningViewModel.kt:76 VALID_AGE_BANDS
- data/repository/ChildProfileRepository.kt:100 VALID_AGE_BANDS
- data/repository/LearningRepository.kt:95,154,196 (coerces unknown band to 4-5 — results would be mislabelled)
- ui/screens/ChildProfileScreens.kt:265 picker list + :292 label mapping (add string profile_age_5_6 = '5–6 tuổi', en '5–6 years')
- Check band-dependent logic in: domain/schedule/ScheduleAdvisor.kt, ScheduleAnchors.kt, ScheduleAdvice.kt,
  data/remote/ScheduleAiApi.kt, domain/daylog/DayLogDraft.kt, data/cloud/CloudSyncManager.kt, domain/model/TimerSettings.kt,
  ChildQuickLogViewModel, QuickScheduleViewModel, DayLogViewModel. Where a rule depends on band, treat '5-6' between '4-5'
  and 'l1' (sleep/nap/anchor defaults: same as 4-5 unless the code says otherwise; state your choice in the report).
- Prefer one source of truth: an AgeBand object/enum with ids, order and labels, used by all of the above.
- Unit tests: '5-6' round-trips through ChildProfileRepository, LearningRepository (no coercion), LearningViewModel.setAgeBand,
  cloud sync serialization; unknown bands still fall back as before.
- Order in pickers: 2–3, 4–5, 5–6, Lớp 1, Lớp 2, Lớp 3.

## 2. CI
Add a job step: node --test shared/learning/tests/*.test.js (Node 20+). Must fail the build on any failure.

## 3. Device check (Pixel 9 via adb, debug build only)
- Build debug APK from this worktree, install, set the child profile band to 5–6 tuổi.
- Learning Hub shows 'Luyện tư duy' tile; 4–5 tuổi does NOT show it; Lớp 1 does.
- Play one 8-question 'Trộn các dạng' session: answer some right, some wrong twice. Verify: hint after 1st miss,
  explanation + animation after 2nd miss, TTS reads the prompt (🔊) and explanation, results appear in parent stats.
- Screenshots: chooser, each of the 6 types (question + explanation), end screen, profile picker with 5–6.
- No Play upload, no push, no merge.

## 4. Icon pack (ChatGPT image generation) — prepare only, do not wire in yet
30 flat, cute, consistent icons, transparent WebP 128×128, total < 300 KB, under shared/learning/www/tuduy/icons/:
fruits (apple, pear, banana, grapes, strawberry, orange, carrot, cupcake, mooncake, donut), animals (rabbit, tiger, bear,
cat, dog, panda, turtle, chick, frog, ant), objects (train, car, rocket, flower, star, cloud, balloon, block, apple-basket, scale).
One object per icon, no text, no logos, no brand characters. Claude will decide where they replace emoji;
counts are always placed by code, never drawn by the image model.

## Constraints
- Do not touch other worktrees, the 19+ uncommitted files in /Users/duongnguyen/Projects/KidFocusTimer, or .omc.
- GIT_OPTIONAL_LOCKS=0. No secrets in repo/logs. No real child data (demo profile 'Bé Na').
- Report: files changed, tests (JVM + node), screenshots path, open questions.
