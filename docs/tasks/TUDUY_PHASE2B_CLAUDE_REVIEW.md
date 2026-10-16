# G2 review — Luyện tư duy phase 2 + 2B (commits ae33b07 … 3b95588)

Re-ran in a clean copy: `TUDUY_N=10000 node --test shared/learning/tests/*.test.js` → 87/87 pass; the 31 original tests are
byte-identical. Decisions in the report (magic 6 givens, addTable Lớp 1 only, 26 types in 4 shelves) are accepted.
Verdict: **changes requested** — 3 correctness bugs a parent would hit, 1 rule fix (my spec's fault), 3 small items.
Do not push. Fix, add the tests below, then Claude runs G3 on the Pixel.

## Must fix

1. **sumSplit has two right answers in ~18% of items.** `equalPairings` only accepts pairings whose sum is ≤ 10, but a child
   who finds a pairing with a larger sum is still right. Example `[2, 6, 10, 4, 8]`: engine answer 10 (2+8 = 6+4), but
   leaving out 2 also works (6+8 = 10+4). 3,555 / 20,000 items ambiguous. Fix: uniqueness over ALL pairings, any sum; the
   answer's own pairing must still be ≤ max with no carry. Also avoid 0 in the five numbers (0 appears in ~60% of items).
2. **signs has a second correct sign choice in 4–23% of items.** `signSolutions` drops combos whose intermediate is > max
   or carries, but the child can still compute them. Example `8 ○ 3 ○ 3 = 8`: engine wants − +, but + − is also true
   (8 + 3 − 3). Measured: 5–6 L2 383/10k, L3 2,051/10k; Lớp 1 L2 512/10k, L3 2,349/10k. Fix: uniqueness over every sign
   combo whose intermediates stay ≥ 0 (no upper cap); the answer combo itself must be legal.
3. **numberLine rejects the swapped addition.** For a hop 3 → 7 the child typing `4 + 3 = 7` is marked wrong. Accept both
   addend orders for '+' (keep exact order for '−'). Same check for any other '+' equation input if it exists.

## Rule fix (spec error, not Codex's)

4. My no-carry rule `units(a)+units(b) ≤ 9`, `units(a) ≥ units(b)` is too strict at 10: it bans 4 + 6 = 10, 10 − 3 = 7,
   10 − 6 = 4 for Lớp 1, which are core "phạm vi 10" facts. New rule for max 20:
   - a + b allowed if a + b ≤ 10, or units(a) + units(b) ≤ 9 (so 14 + 5 ok, 14 + 6 = 20 and 8 + 5 still banned);
   - a − b allowed if a ≤ 10, or units(a) ≥ units(b) (so 10 − 3 ok, 13 − 5 and 20 − 3 still banned).
   Apply in all three step() copies (tuduy-core.js, phase2, phase2b) — better: one shared helper. Update the no-carry tests.
   Consequences: triSum may use S = 9 and S = 10 (S = 10 partial sums never exceed 10); `neighbor` must stop excluding
   9 → 10 (counting across ten is not carrying).

## Small

5. Variety: `venn` has only 3 distinct items (fixed circles and points), phase-2 `insideOut` has 4 fixed positions, `magic`
   5–6 has 66 grids. Jitter positions (keep ≥12 px from every edge, test it) and add triples {0,1,2},{1,2,4},{1,3,5}-style
   Latin magic squares within range.
6. `ring` L3 asks for 7 blanks in a row; show one more given value at L3 (book shows two) so the child has a second anchor.
7. Phase-2 `compare` always differs by exactly 0 or 1; let |difference| be 0..3 at L2.

## Tests to add (tuduy-phase2b.test.js / tuduy-phase2.test.js; old 31 untouched)
- sumSplit: brute force all 5 × 3 pairings with no sum cap → exactly one unused number.
- signs: all 2^k combos with intermediates ≥ 0, no cap → exactly one true.
- numberLine: UI check helper accepts `[b, a, c]` for '+', rejects it for '−' (extract the comparison into a pure function).
- no-carry: 4+6, 10−3, 14+5 allowed; 8+5, 14+6, 13−5, 20−3 rejected, in every engine.
- venn / insideOut / magic: ≥ 30 distinct visuals per 1,000 items.

## Then
Report back; Claude installs the APK on the Pixel 9 and captures question + explanation for every new type (G3).
