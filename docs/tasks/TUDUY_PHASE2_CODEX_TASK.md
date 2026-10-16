# Codex task: Luyện tư duy — phase 2 (11 new types + range 20 for Lớp 1)

Worktree: /Users/duongnguyen/Projects/KidFocusTimer-tuduy, branch feat/learning-tuduy (HEAD b804776).
Sources reviewed by Claude: Ksmart3 sheets, math_kindergarten.pdf (52 p, Lessons 1–14), toan_under20.pdf (79 p).
Claude reviews the result (G2) and re-runs the Pixel 9 check (G3). Do not push, merge or deploy.

## 0. Hard rules (unchanged from phase 1 — breaking any = reject)
- Do NOT modify or weaken the existing 31 tests in shared/learning/tests/tuduy-content.test.js. Add new tests in a NEW file
  shared/learning/tests/tuduy-phase2.test.js. Existing tests must still pass unchanged.
- Every new type: answers come from a solver, never from generator bookkeeping. Tests re-derive answers with an INDEPENDENT
  brute-force check written in the test file (not by calling the engine's solver). 10,000 items per type × level (env TUDUY_N).
- Every item: exactly one correct answer (or exactly one correct set for multi-select); reject and regenerate otherwise.
- Numbers: 5–6 tuổi → 0..10. Lớp 1+ → 0..20 and NO carrying/borrowing across 10 (a+b: units(a)+units(b) ≤ 9;
  a−b: units(a) ≥ units(b)). Carrying across 10 (8+5) is Grade 2 (audit M4) — never generate it.
- No multiplication, ratio or "exchange rate" reasoning (audit M11): no "1 🍉 = 2 🍍" scales, no "mỗi người 2 chiếc đũa".
- No exam framing: no "thi", "kiểm tra", "đề", "lớp 1", "sẵn sàng vào lớp 1" in any child-facing string (existing regex test).
- Explanation (explain[]) for every item; explain[0] is a hint that must NOT reveal the answer (test it per type);
  the last explanation lines must state the answer.
- Vietnamese wording natural for a 5–6 y/o; keep English sub-label optional. Explanations read by existing speakText.
- Pure engine stays DOM-free and loadable by Node (module.exports). UI changes go in tuduy-ui.js / tuduy.css only.

## 1. Engine API change
- `generate(type, level, rng, opts)` with `opts.max` (10 or 20). UI passes 10 for band '5-6', 20 for l1/l2/l3.
- Existing 6 types: accept max 20 under the no-carry rule (pyramid, balance, chain, sequence, scale). Shapes unchanged.
  Default (no opts) stays 10 so existing tests remain valid.
- New item fields as needed: `input: 'numpad' | 'sign' | 'order' | 'choice' | 'multi'`, `choices` (for choice/multi),
  `answer` shape per input. Keep `blanks` for numpad items.

## 2. New types (id, source, spec, L2 / L3, independent test)
1. `compare` — So sánh (both books). Two numbers or two expressions; child picks > < =.
   L2 numbers / one expression vs number; L3 expression vs expression (e.g. 6 + 4 ○ 14 − 1 at max 20).
   Test: evaluate both sides independently; sign must match. Mix of all three signs (≥20% '=' over 10k).
2. `order` — Sắp xếp. 4–6 distinct numbers, "từ bé đến lớn" or "từ lớn đến bé"; input = tap in order (each tap fills next slot,
   tap again to undo). Test: sorted independently; numbers distinct.
3. `neighbor` — Số liền trước / liền sau / ở giữa, shown on a number line or train. L2 one blank; L3 "2 số liền trước"/
   both neighbours. Test: arithmetic ±1 within range, never <0 or >max.
4. `position` — Vị trí thứ mấy. Row of 5–7 icons; "con vật thứ k từ trái/phải" → tap it. L3: "đứng giữa", or two-step
   ("thứ 2 từ phải sang, rồi đi sang trái 1"). Icons distinct in a row. Test: compute index independently from direction.
5. `condition` — Chọn số theo điều kiện (under20 "lớn hơn 3 nhưng bé hơn 7"; chẵn/lẻ). Multi-select among 6–8 numbers.
   L2 one condition; L3 two conditions (range AND chẵn/lẻ). Must have ≥1 and ≤4 correct. Test: filter independently.
6. `shapePattern` — Quy luật hình (shapes/arrows). Periods AB, AAB, ABB, ABC; arrows rotating 90°/45° (L3). Show ≥2 full
   periods; choice of 4. Test: for each choice, check whether any period p ≤ len/2 explains the visible prefix AND that
   choice; exactly one choice may pass.
7. `grid` — Bảng còn thiếu 2×2 / 3×3 (kindergarten "circle the correct answer", under20 3×3 shape grid).
   3×3: each row and each column contains each of 3 shapes once and each of 3 colours once (Latin square on two attributes);
   2×2: row = same shape, column = same colour (or rotation). One cell '?', choice of 4 drawn in SVG.
   Test: brute-force all candidate tiles against the rule; exactly one fits; the 3 distractors each break a rule.
8. `oddOne` — Khác loại. 4 items: 3 share an attribute (category: con vật/quả/xe; or shape; or colour), 1 differs.
   AMBIGUITY CHECK required: no other attribute may single out a different item. Uses the 30 WebP icons Codex made
   (tuduy/icons) or SVG shapes. Test: for every attribute, count items that are unique on it; only the answer may be unique.
9. `insideOut` — Trong / ngoài (under20). SVG: one circle and one square overlapping; 4–6 objects placed in distinct regions
   (in circle only, in square only, in both, outside both). Ask e.g. "Vật nào ở trong hình tròn nhưng ngoài hình vuông?"
   Objects placed ≥12 px from any boundary. Test: geometric point-in-circle / point-in-rect independently; exactly one fits.
10. `code` — Mật mã hình (under20). Legend icon→digit (3–4 icons, digits 0–9 distinct). Show a row of 3–4 icons; pick the
    matching number among 4 (L2) or type digits (L3, numpad multi-blank). Distractors: one swap, one wrong digit.
    Test: decode independently.
11. `triSum` — Tam giác số (under20, Lớp 1+ only). 6 circles (3 corners + 3 mid-sides), numbers 1..6 each once, every side sum
    = S (9, 10, 11 or 12). Show S and 3–4 given numbers; fill the rest. Test: brute-force all 720 permutations; exactly one
    matches the givens.

Bands: types 1–10 visible in 5–6 and Lớp 1+; type 11 only Lớp 1+. Chooser shows only types valid for the band.

## 3. UI
- New inputs: sign pad (> < = big buttons), order (tap to place, tap to undo), choice (2×2 big tiles), multi (toggle + Xong rồi).
- Same flow as phase 1: hint after 1st miss (explain[0]), answer + steps + animation after 2nd miss, review queue, adaptive level.
- Animations: compare (two piles/number line with the bigger side lifting), order (cards sliding into place),
  position (counting fingers hop from the chosen side), insideOut (highlight the region), triSum (side sums light up).
- Phone width 360–412 px: nothing clipped or overflowing (phase 1 G3 found a clipped figure — add a layout test like
  the existing gridGeom test for every new SVG figure).

## 4. Out of scope (paper only)
Tangram composition, connect-the-dots copying, colouring, exchange-rate scales, chopsticks/pizza division word problems.

## 5. Deliverables
- Code + tuduy-phase2.test.js (independent brute force, 10k per type × level, hint-does-not-reveal test, range/no-carry
  test for max 20, layout tests). Report: per-type example items (JSON), test counts, screenshots at 390×844 of each new
  type (question + explanation), and anything you could not make unambiguous.
- Run: TUDUY_N=10000 node --test shared/learning/tests/*.test.js — all green. JVM tests unchanged and green.
