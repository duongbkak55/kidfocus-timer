# Codex task: Luyện tư duy "Làm quen" level for 4–5 tuổi

Branch feat/web-app, on top of the commit that holds the install-pill + "Dễ/Khó" label change (commit those first if
still uncommitted). Do not push, merge or deploy. Claude does G2/G3.

## Why
Today tuduy starts at band 5–6 (Learning Hub `visible` list, web `/tu-duy/`, web app `canPlay`). Band 4–5 is the
Learning Hub DEFAULT, so most new users never see tuduy. The easiest level (internal level 2) is still too hard for 4–5.

## Product rules (level 1 = "Làm quen")
- Numbers 0–10. Any + or − result, and every operand, within 0–5 (a+b ≤ 5, a−b with a ≤ 5). At most ONE blank.
- Picture-first: prefer dots/objects next to numerals; prompt must be fully understandable when read aloud
  (existing 🔊), max ~12 words, no Lớp-1 vocabulary ("số liền trước" → "số đứng ngay trước").
- Answers via ≤ 4 choices or the keypad 0–10. Exactly one correct answer, checked with plain arithmetic.
- Hints and explanations as today, simpler words.
- Session 5 questions; no timers, no exam framing.

## Types for 4–5 (whitelist, level 1 only)
| type | level 1 shape |
|---|---|
| shapes (Đếm hình) | 2–5 separate shapes of one kind, no overlaps/nested counting |
| compare | two groups of objects ≤ 10, choose more/less/equal |
| order | 3 numbers ≤ 10, small → large |
| neighbor | number right after (or right before) n ≤ 9, dots shown |
| sequence | +1 counting ≤ 10, or AB/ABB picture pattern, one blank |
| shapePattern | AB, AAB, ABB with 2 shapes/colours |
| oddOne | 4 pictures, 1 different by one feature (colour OR shape OR kind) |
| position | trên/dưới/trái/phải/giữa with 3 objects |
| insideOut | count objects inside a circle, ≤ 5 |
| match | match dot groups to numerals (≤ 5), 3 pairs |
| chain | 3 cars, +1 or +2 only, results ≤ 5, one blank |
| balance | picture addition a + b = □, sum ≤ 5 |
| latin | 3×3 with 3 shapes (not numbers), 1–2 blanks with exactly one solution |
Every other type is hidden for 4–5.

## Engine
- New file `shared/learning/www/tuduy/tuduy-level1-core.js` (same UMD pattern as phase2b). `generate(type, 1, rng, {max})`
  dispatches to it; level 2/3 behaviour byte-for-byte unchanged (existing seeded tests must still match).
- `generate` accepts level 1 only for the whitelist; throws otherwise.
- Adaptive: band 4–5 stays on level 1 (no promotion to 2). Store level 1 stats under separate keys
  (e.g. `stats['l1:'+type]`) so switching a profile's band does not drop a 5–6 child back to level 1 or vice versa.
  Existing `{stats, review}` shape and sync doc `learning/{profileId}_tuduy` stay compatible (old clients ignore new keys).
- Review queue items keep their level so a re-try regenerates nothing.

## UI / surfaces
- tuduy-ui.js: band `4-5` → level 1, whitelist, label "Làm quen" on chooser cards; shelves hide empty groups.
  Bigger targets for 4–5 (choices ≥ 64 px).
- Learning Hub (`shared/learning/www/index.html`): add `tuduy` to `AGE_BANDS['4-5'].visible`.
- Web `/tu-duy/`: band buttons "4–5 tuổi · 5–6 tuổi · Lớp 1" (fit at 320 px, no overflow); default stays 5–6;
  `?band=4-5` supported for links from posts.
- Web app: `canPlay` and learning-sync band map include `4-5`.
- Android: no Kotlin change expected beyond the WebView assets; confirm.

## Tests
- Level 1 generator tests: 2,000 seeds per type — bounds above, one blank, unique answer, prompt length,
  no type outside whitelist; deterministic seeds.
- All existing tests unchanged and green (96 Learning Hub + sync + `npm --prefix web test`).
- Layout tests for new visuals at 320/360/412 px; screenshot page like `tuduy-phase2b-screenshot.html` for level 1.
- Playwright: /tu-duy/?band=4-5 plays 5 questions per type, no external requests, no horizontal scroll.
- Report: docs/tasks/TUDUY_LEVEL1_45_CODEX_REPORT.md with screenshots of each type (390×844).

## Decision (Claude, answer to Codex question)
The assertion in `shared/learning/tests/tuduy-content.test.js` ("tuduy is shown from 5–6 tuổi upward and never to 2–3
or 4–5") encodes the OLD product rule and is intentionally changed by this task. Update it, do not delete it:
rename to "tuduy is shown from 4–5 tuổi upward and never to 2–3", `shouldShow = ['4-5','5-6','l1','l2','l3']`.
Keep the 2–3 band hidden. Count this as an intended change in the report (not a weakened test).
