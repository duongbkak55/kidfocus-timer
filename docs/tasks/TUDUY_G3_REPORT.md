# G3 device check — Luyện tư duy (Pixel 9, debug build, 2026-10-16)

Device: Pixel 9 (4A150DLAQ0013S, USB). App: com.kidfocusstudio.timer.debug 1.0.0-rc.5 (side-by-side with Play beta, no data touched).
Method: real adb touch events; page state read via WebView DevTools. Screenshots: 'Ý tưởng dự án/kidfocus-tuduy-staging/g3/' (not committed).

| Check | Result |
|---|---|
| 4–5 tuổi hides Luyện tư duy | PASS (tile hidden) |
| 5–6 tuổi shows it, hides 'Đề thi thử vào Lớp 1' | PASS (21 trò chơi phù hợp) |
| Lớp 1 shows it | PASS |
| Band 5-6 persists across reinstall + restart (native save, no revert to 4-5) | PASS |
| Mixed 8-question session, wrong twice on every item: hint after 1st miss, answer + steps + animation after 2nd | PASS (all 6 types) |
| Review queue gets the 8 misses; 'Làm lại câu chưa đúng' answered correctly: 8/8 'Giỏi quá!', stars, queue 8 → 0 | PASS |
| learningResult recorded (header '1 → 2 activities completed') | PASS |
| TTS audio | NOT VERIFIED by ear (device check is visual) — Duong to listen once |

Bugs found and fixed (commit 7f9617c, regression tests added, 31 node tests pass):
1. Đếm hình: 3-cell rectangle strip drawn 360 wide in a 300 frame → clipped on the right (4-cell L3 worse). Now scales to fit.
2. Quy luật dãy số: first hint was the rule itself ('Nhóm 9, 6, 1 lặp lại'). Now a nudge; the rule moves into the explanation.
Re-verified on device after rebuild: strip right edge 290/300, fully visible.
