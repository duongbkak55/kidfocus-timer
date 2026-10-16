# Codex task: Luyện tư duy — phase 2B (from Math for Kindergarten Book 2 + Book 3)

Do this AFTER phase 2 (TUDUY_PHASE2_CODEX_TASK.md) is green, on the same branch feat/learning-tuduy. Same worktree.
Sources reviewed by Claude: "math kindergarten 2.pdf" (Lessons 15–30, 65 p) and "math kindergarten 3.pdf" (Lessons 31–40, 42 p).
Page refs are the printed page numbers (±1); both PDFs are in the Ksmart3 Drive folder if you need to look.
All Hard rules from phase 2 §0 apply unchanged (31 old tests untouched, independent brute force 10k per type × level,
unique answer, 0..10 for 5–6, 0..20 NO carry for Lớp 1+, no ratio/multiplication, no exam wording, hint never reveals).
New tests go in shared/learning/tests/tuduy-phase2b.test.js. Do not push, merge or deploy.

## 1. New types (id — book page — spec — independent test)
1. `latin` — Sudoku nhí (B2 p.15, p.23; B3 p.22). Grid n×n, n = 3 (5–6 L2), 4 (5–6 L3, Lớp 1 L2), 5 (Lớp 1 L3); each row and
   column holds 1..n once. Give enough clues for a UNIQUE fill; child fills 2–4 blanks (numpad 1..n). Hint: "Nhìn hàng và cột
   của ô này, số nào còn thiếu?". Test: backtracking solver in the test counts solutions of the whole grid = 1.
   (9×9 sudoku on B2 p.70 stays paper only.)
2. `magic` — Ô vuông kì diệu (B2 p.35, p.50, p.53). 3×3, every row, column (and diagonal if shown) has the same sum S
   (S = 6, 9, 12 for 5–6; up to 15 for Lớp 1). Numbers may repeat (book does). 3–4 givens, 2–4 blanks. Also the
   "each column sums to 10 using 1–4" variant (B2 p.54) as L3. Test: brute force all blank values 0..max; exactly one fill.
3. `signs` — Điền dấu + / − (B2 p.34, p.50). "5 ○ 3 ○ 2 = 6": 2 signs (L2) or 3 signs (L3). Every intermediate stays in
   0..max and no carry (Lớp 1). Input: sign pad with only + and −. Test: try all 2^k sign combos; exactly one is true.
4. `triCenter` — Tam giác: số giữa = tổng 3 góc (B2 p.51, p.59; B3 p.34). L2 the centre is blank; L3 one corner is blank.
   Test: arithmetic; also check every visible number ≤ max and the sum has no carry for Lớp 1.
5. `sumSplit` — Chọn 4 trong 5 số sao cho □ + □ = □ + □ (B3 p.46): "Số nào không dùng?" choice of 4. Generate 5 distinct
   numbers ≤ 10; Test: enumerate all choices of the unused number and all pairings; exactly one unused number works.
6. `addTable` — Bảng cộng (B3 p.11). Header row and column, fill 2–4 cells; Lớp 1 only (11..13 + 1..3 style, no carry);
   5–6 version uses headers 0..5. L3: one header itself is blank (deduce from a filled cell). Test: recompute table, unique.
7. `numberLine` — Nhảy trên tia số (B2 p.16–18, p.36; B3 p.12–13, p.28). Show a 0..10 (or 0..20) line with one hop arc;
   child writes the equation (□ + □ = □ or □ − □ = □, 3 blanks). Animation: frog hops tick by tick. Test: arc start/length
   decoded independently from the visual; no carry for Lớp 1.
8. `ring` — Vòng số (B3 p.30). 6–8 circles in a ring, ops on the arrows (±1..±3), one start number; fill the rest. The loop
   must close (sum of ops = 0). Test: walk the ring from the given number; values in range; closing check.
9. `match` — Nối phép tính cùng kết quả (B2 p.47, p.61, p.66, p.68; B3 p.37). 3–4 pairs; input = tap left then right to
   connect (new input mode 'pairs'). Results within a set are distinct so pairing is unique. Test: evaluate all; unique
   perfect matching (brute force permutations).

## 2. Extensions to phase-2 / phase-1 types
- `position` L3: queue riddle (B2 p.34) "Có 3 bạn đứng trước và 1 bạn đứng sau. Mình là ai?" — row of named kids.
- `insideOut` L3: Venn of 3 circles (B3 p.27) "Con kangaroo ở trong mấy hình tròn?" — answer 1..3 by point-in-circle.
- `condition` L3: pick the expressions whose value is k (B2 p.48 "colour the apples that make 7").
- `shapePattern`: add "dot moving around a 2×2 / L-tromino" (B2 p.19, p.71) and 3×3 attribute matrix with count of dots
  (B3 p.19, p.31, p.33 — reuse `grid`'s Latin-square rule: shape × number of dots).
- `compare` L3: "Ai có tổng lớn nhất?" — 4–5 dice pairs (B2 p.51), choice of names; ties forbidden.
- `pyramid` Lớp 1: 4 rows, top ≤ 20 (B3 p.14, p.30, p.38 hex/stair pyramids), no carry at every addition.
- `chain` L3: mixed-op train of 6–7 hops (B2 p.67 "−3 +5 −6 +5 −2 +3"), every intermediate in range.

## 3. Keep OUT (paper only — say so in the report, do not build)
- Carry across 10 (B3 Lesson 32/37: 9+4, 7+5, 11−7, 13−5…) — audit M4, Grade 2.
- Repeated-symbol equations / doubling / halving: "🧁+🧁+🧁+🧁 = 8", "🎄+🎄 = 8", "same number in each box",
  "circle half of each group", "each cow has two ears", exchange scales "1 ■ = 2 ▲" (B2 p.42, p.53, p.67; B3 p.18, p.35,
  p.41) — audit M11.
- Relative-speed chase (B3 p.19 cat & mouse), fractions-of-circle pattern (B2 p.27), paper folding with holes (B2 p.43),
  3-D cube counting (B2 p.39), matchsticks (B2 p.71, B3 p.39), pentomino/colour-overlay (B2 p.57, B3 p.47),
  shadow matching and left-hand recognition (B3 p.23, p.42) — need art we don't have; revisit later.
- Word problems (Lessons 18, 23–27, 33, 39, 40) belong to the hub's existing math lessons, not tuduy.

## 4. Bands & UI
- 5–6: latin(3/4), magic(S≤9), signs(2 signs), triCenter, sumSplit, numberLine(0..10), ring, match.
- Lớp 1+: all of the above at max 20 + addTable.
- Chooser groups the 20 types into 4 shelves so the screen stays readable: Số & phép tính / Quy luật / Hình & vị trí /
  Câu đố ô số. Layout test at 360 and 412 px for every new SVG (latin 5×5 and ring are the risky ones).

## 5. Deliverables
Same as phase 2 §5: code, tuduy-phase2b.test.js, report with JSON examples per type, screenshots 390×844 (question +
explanation), and every item kind you could not make unambiguous. Run TUDUY_N=10000 node --test shared/learning/tests/*.test.js.
