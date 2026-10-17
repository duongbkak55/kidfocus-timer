# Codex task: stale explanation animation runs on the next question (tuduy-ui.js)

Branch feat/web-tuduy. Found by Claude G3 of the web demo (headless Pixel 7, live site).

## Bug
`revealExplain(item, true)` schedules `setTimeout(() => animate(item), 400)` directly (tuduy-ui.js ~line 424), not via
`after()`, so `stopAnim()` cannot cancel it. If the child taps "Câu tiếp", "← Chọn dạng khác" or a band switch within
0.4 s of the second miss, `animate(oldItem)` runs against the NEW question's DOM:
- uncaught TypeErrors ("Cannot set properties of undefined (setting 'textContent')" at animate ~466,
  "Cannot read properties of null (reading 'appendChild')" for position/condition), seen on the live web page;
- worse, the pyramid branch writes "?" into the new question's boxes and removes their state classes.
Same code ships in the app (Learning Hub WebView).

## Fix
1. Schedule through `after(400, …)` so `stopAnim()` cancels it; call `stopAnim()` in next(), open(), start().
2. In `animate(item)` (and every `after` callback it creates) bail out unless `item === items[idx]` and the quiz is visible.
3. Optional chaining is not enough on its own — the guard in (2) is the real fix.

## Test
Add a jsdom (or headless) regression test: start a session, miss twice, call next() within 100 ms, advance timers
3 s → no exceptions and the new question's blanks still read "?" only where they are blank (no stray writes).
Run for every type at L2/L3 and both bands (5-6, l1). Keep the 31 original tests unchanged; all JS + 361 Android green.
No push/deploy.
