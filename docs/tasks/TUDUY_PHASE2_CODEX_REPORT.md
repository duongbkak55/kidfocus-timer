# Luyện tư duy — Phase 2 implementation report

Branch: `feat/learning-tuduy` in `KidFocusTimer-tuduy`. The task file was copied and committed separately as `db302a6`. No push, merge, or deploy.

## Delivered

- Added 11 puzzle types: compare, order, neighbor, position, condition, shapePattern, grid, oddOne, insideOut, code, triSum. Their answers are derived from the displayed data by a solver; ambiguous candidates are rejected.
- Added `generate(type, level, rng, { max })`: 5–6 uses 0..10; older bands use 0..20. The six previous types keep 0..10 as their default and accept max 20 with no carry/borrow for arithmetic.
- Added sign, ordered taps with undo, choice, multi-select, and multi-blank number input. First miss gives a hint; second miss reveals the answer and explanation, keeps the review queue, and updates adaptive level. Added animations for compare, order, position, insideOut, and triSum.
- The chooser hides triSum in the 5–6 band. No multiplication, ratio or exchange-rate reasoning was added. The old 31-test file was not modified.

## Example items

The complete generated item JSON, including each visual, input, choices, solution, hint and explanation, is in [TUDUY_PHASE2_EXAMPLES.json](TUDUY_PHASE2_EXAMPLES.json). A few examples from that file:

| Type | Displayed data | Answer |
| --- | --- | --- |
| compare | Two visible numbers/expressions with `?` between them | `<` |
| order | Four distinct numbers, ascending | `[6,11,17,18]` |
| neighbor | One missing number in the number train | `14` |
| condition | Select the even numbers from the displayed set | `[4,20]` |
| insideOut | Find the object inside both overlapping shapes | Object at index `2` |
| triSum | Each side of the triangle totals 9 | Missing cells `[2,3,5]` |

## Verification

- `TUDUY_N=10000 node --test shared/learning/tests/*.test.js`: **56/56 pass** (31 unchanged previous tests, 25 new Phase 2 tests). The new test suite independently checks 10,000 generated items per new type and level for each applicable band: **420,000 Phase 2 items** in total. It checks uniqueness, hint text, number range, no-carry cases, geometry and viewport bounds.
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ./gradlew testDebugUnitTest lintDebug assembleDebug`: **361 Android unit tests pass; lint and debug build pass**.
- `node --check` for both engine files and the UI passed. `git diff --check` passed.

## Screenshots and review notes

The [390×844 screenshot fixture](../../shared/learning/tests/tuduy-phase2-screenshot.html) renders a question or its second-miss explanation for every new type through the actual UI. **Screenshot files could not be captured in this run.** Browser use rejected opening the local fixture under its URL security policy and expressly prohibited an alternate browser route. No substitute images were fabricated. Claude's G3 review can capture the fixture or the Pixel UI through an approved route; this is an outstanding deliverable.

The task's triSum target set `9/10/11/12` conflicts with its hard no-carry rule: adding positive digits 1..6 to reach 10 or more necessarily crosses ten. The generator therefore uses **target 9 only** and documents this choice in code and independent tests. If the content owner wants 10–12, that requires explicitly changing the no-carry rule for this type.

## Follow-up: Back navigation on Pixel 9

The Luyện tư duy header's Back button and Android Back now use the same route: puzzle/results → type chooser → Learning Hub home. The previous Android handler forced `currentScreen = home` even when the WebView stayed within Luyện tư duy; it now reads the WebView's destination. Two new navigation tests pass, making the Node total **58/58**. Android's **361 unit tests**, lint and debug build pass. The updated APK was installed on Pixel 9 with `adb install -r`; tapping the in-page Back button from a puzzle visibly returned to the type chooser. Hardware Back is covered by the route test but was not separately verified on-device in this follow-up.
