# Play Console – App content declarations (updated 2026-10-05)

Play received versionCode **5** / versionName **1.0.0-rc.3** from the locally signed `launch-assets/2026-10/release/app-release-v5-UPLOAD-KEY.aab` (upload key SHA-1 `FB:FB:90:D1:53:E5:52:2B:07:E9:AA:04:28:48:55:37:29:3E:FB:4E`). Do not use the CI AAB for Play: CI signs with a different key (`A7:B8…`). No Firebase deployment is part of this update.

| Declaration | Status | Answer |
|---|---|---|
| Content rating (IARC) | Saved | All other app types; online/AI content = Yes; digital purchases = Yes; everything else No → ESRB Everyone, ClassInd 14+ |
| Target audience | Saved | ≤5 → 18+ (all, consecutive); not in Expert Approved; certified children's law compliance |
| Data safety | Saved | See DATA_SAFETY.md |
| Advertising ID | Saved | No |
| Government app | Saved | No |
| Financial features | Saved | None |
| Health apps | Saved | None |
| Foreground service (specialUse) | Video approved locally; **Play video link/submission still pending** | Use the text below and `launch-assets/2026-10/V-fgs-timer.mp4` (26 s, Bé Na). Upload the video as Unlisted and add its link in Play Console. |
| Exact alarm (USE_EXACT_ALARM) | **Do not declare for v5** | The uploaded v5 manifest has only `SCHEDULE_EXACT_ALARM`; `USE_EXACT_ALARM` was removed in f17fe66. Confirm the old declaration disappears after Play processes v5. |

## Foreground service – Describe permission use (paste as-is)

KidFocus Timer is a visual focus/routine timer for children. When a parent or child starts a countdown for a task (e.g. homework 25 minutes, brushing teeth 3 minutes), TimerService runs as a foreground service (subtype "timer") with an ongoing notification showing the remaining time and a pause/stop action. The countdown must start immediately when the user taps Start and must keep running accurately while the screen is off or the child switches apps; it cannot be deferred, batched, paused or restarted by the system because the child relies on the exact end time and the end-of-task sound/vibration. The service stops as soon as the timer finishes or the user stops it. None of the existing foreground service types (e.g. mediaPlayback, dataSync) fits a user-started countdown timer, so we use specialUse with subtype "timer".

## Video (≈30s, upload to YouTube as Unlisted)
1. Open the app → choose a task → tap Start; the countdown begins.
2. Pull down the notification shade: the "remaining time" notification shows Pause/Stop.
3. Go Home / turn the screen off for a few seconds → open it again; the timer is still counting.
4. Tap Stop in the notification → the notification disappears.
Use the demo profile "Bé Na", no real child data.
