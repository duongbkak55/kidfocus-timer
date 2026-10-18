# G2 review — Luyện tư duy "Làm quen" 4–5 tuổi (db0fcca)

Verdict: **changes requested** (UI size + 3 content fixes). Engine boundaries, stat separation (`l1:` keys), review
filtering by band, level 2/3 byte-identical output, web `?band=4-5`, web app band map and the visibility-test change are good.
Do not push or deploy.

## Must fix
1. **Pictures are too small for 4–5 ("picture-first").** In the screenshots objects/dots/symbols are ~20 px while the keypad
   is huge. A 4-year-old has to count these. Make every counted object / symbol ≥ 48 px (emoji font-size ≥ 40px on 390 px),
   pattern and latin choice symbols ≥ 40 px inside their 64 px buttons, `↔` / `→` arrows ≥ 28 px.
   Groups > 5 objects: lay out as rows of 5 (ten-frame style) so `compare` up to 10 stays countable; must fit 320 px.
2. **Pattern answer is always the second symbol.** `shown` is fixed at 5 items, so AB, AAB and ABB all end on "B next".
   Vary the shown length (4–6) so the answer is A or B ~50/50; add a test over 2,000 seeds that both occur ≥ 35 %.
3. **Position wording.** "Con nào ở phải?" is not natural Vietnamese. Use: "ở bên trái", "ở bên phải", "ở giữa",
   "ở trên cùng", "ở dưới cùng" (prompt and explanation).

## Small
4. `oddOne` variety: only 🔵 vs 🟠/🔷. Add a third mode by kind (3 fruits + 1 animal, or 3 animals + 1 vehicle) with the
   same picture size; keep exactly one odd item.
5. `sequence` should be numbers most of the time (e.g. 70 % +1 counting, 30 % picture pattern) so it differs from
   "Quy luật hình".
6. Remove the stray file `.l1sheet.jpg` at the worktree root (Claude created it while reviewing; not part of the change).

## Tests to keep / add
- 2,000-seed checks as today + pattern A/B balance + position wording.
- Screenshot page: re-capture the 13 types at 390×844 and 320×640; no horizontal overflow; report picture sizes.

## Re-review b60835f — PASS
All 3 must-fix and 3 small items addressed (pictures ≥ 40 px, ten-frame rows of 5, pattern length 4–6, natural position
wording, oddOne by kind, sequence 70 % numbers, stray file removed). Claude web G3 on the built dist (Chromium, 320 and
390 px): `?band=4-5` selects 4–5, 13 "Làm quen" cards, 5/5 correct answers through the done screen, stats under `l1:*`
only, switching to 5–6 still shows "Dễ", no horizontal overflow, no page errors.
Nit (not blocking): numeric sequence shows "?" in the row and again in the answer box.
Ready for web deploy (hosting:demo) after Duong approves; Pixel G3 together with the app release.
