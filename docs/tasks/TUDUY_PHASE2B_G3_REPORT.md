# G3 — Luyện tư duy phase 2 + 2B on Pixel 9 (APK of fd227ce)

Device: Pixel 9 (4A150DLAQ0013S), debug APK built 06:02 from fd227ce (engine file hash in the APK = worktree).
Driver: real adb taps located through Chrome DevTools rects (`g3c.mjs`); each type opened from the chooser, question
screenshot, answered with real taps (deliberately wrong first) until the explanation showed, explanation screenshot.
Screenshots: `Ý tưởng dự án/kidfocus-tuduy-staging/g3/phase2b/` (5-6-L2-*, l1-L3-* = Lớp 1 default level, l1x-L3-* = Lớp 1 level 3).

## Coverage
- 5–6 tuổi, level 2: all 18 visible types (compare … match). triSum and addTable correctly absent from the chooser.
- Lớp 1, level 2: 17 types incl. triSum (S = 10) and addTable.
- Lớp 1, level 3 (levels forced via storage + reload): latin 5×5, magic S = 15, triCenter (corner blank), queue riddle,
  Venn 3 circles, expression condition, dot pattern, shape × dots grid, dice compare, 4-row pyramid, long train,
  ring 7, addTable header blank, 3-sign signs, 4-pair match, order, insideOut.
- Every screen: no horizontal overflow at 411 css px (scrollWidth = 411, 0 elements outside the viewport).
- Every type: hint after 1st miss, answer + steps after 2nd miss, "Đúng rồi"/"Giỏi quá" on a correct try, explanation
  states the right answer. All answers checked by eye against the picture: correct.

## Must fix
1. **insideOut — objects sit on the square's border.** In 3 of 4 captures the object meant to be "in the square, outside
   the circle" (or outside both) is drawn across the square's right edge (🍎 / ⭐ / 🚗 at x ≈ 205 with the edge at 220;
   emoji are ~28 px wide). A child cannot tell inside from outside. Keep every object's whole glyph box ≥ 10 px inside
   or outside every boundary (test with the glyph half-size, not the centre point).
2. **position queue (L3) — which end is the front?** "Có 2 bạn đứng trước và 4 bạn đứng sau" in a row of 7 has two
   answers (3rd from left or 3rd from right) because nothing marks the front of the queue. Add an explicit front marker
   (cô giáo / lá cờ / "Đầu hàng →" label) and say "Đầu hàng ở bên trái" in the prompt and TTS.
3. **triSum — the "Mỗi cạnh = 10" label covers the left mid-side circle** (number under it unreadable, Lớp 1 L2).
   Move the label under the triangle or into the prompt only.
4. **numberLine at 0..20 — tick labels are ~6 css px and a 1-step hop is a tiny arc.** Unreadable on a phone. For max 20
   show only the segment around the hop (e.g. 10 ticks) or label every number at ≥ 12 px and draw ≥ 24 px per step.

## Small
5. expressionCondition tiles read "1. 6 − 1", "3. 10 − 3": the index prefix looks like part of the maths. Drop the
   "n." prefix (the explanation can name the expressions instead of "ô số 3, 4").
6. match: the same expression can appear on both sides (5–6: "2 + 5" left and right) → trivial pair; avoid identical
   expressions across the two columns.
7. diceCompare shows "🎲 3 + 🎲 2" (numbers, not dice faces). Draw dot faces like the book, or rename to "Ai có tổng lớn nhất?".

## Not bugs (driver issues, fixed in the driver)
- order / insideOut first pass did not answer: the driver tapped the same order chip twice and filtered SVG <text> with
  offsetParent. Re-run with the fixed driver: both types answer and explain correctly.
