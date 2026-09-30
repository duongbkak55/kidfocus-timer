// Manual paid provider evaluation only. Never called by npm test.
const {validateDraft, providerBody, validateImage, DAYS} = require("../schedule");
const fs = require("node:fs/promises");
const path = require("node:path");
const textFixtures = require("../test/fixtures/schedule-parse.vi.json");
const imageFixtures = require("../test/fixtures/schedule-images.vi.json");
const {callProvider, usageMeter, failureCode, differences, writeCaseDiffs} = require("./eval-schedule-live-common");
function fields(draft) {
  const hourGroups = (kind, times) => {
    const groups = new Map();
    for (const [day, start] of Object.entries(times)) {
      const group = groups.get(start) || []; group.push(day); groups.set(start, group);
    }
    return [...groups].map(([start, days]) => ({kind, days: DAYS.filter((d) => days.includes(d)), start, duration: null}));
  };
  const items = [
    ...draft.tasks.map((task) => ({kind: "task", days: task.days, start: task.start, duration: task.durationMin})),
    ...hourGroups("wake", draft.anchors.wake), ...hourGroups("bed", draft.anchors.bed),
    ...draft.anchors.school.map((block) => ({kind: "school", days: block.days, start: block.start,
      duration: (minutes(block.end) - minutes(block.start) + 1440) % 1440})),
  ];
  return items.sort((a, b) => JSON.stringify(a).localeCompare(JSON.stringify(b)));
}
function minutes(time) { const [h, m] = time.split(":").map(Number); return h * 60 + m; }
async function evaluate(fixtures, kind) {
  const model = (kind === "image" ? process.env.AI_SCHEDULE_VISION_MODEL : process.env.AI_SCHEDULE_MODEL) || "google/gemini-2.5-flash-lite";
  console.log(`${kind} model: ${model}`);
  const usage = usageMeter();
  const scores = {days: 0, start: 0, duration: 0};
  const totals = {days: 0, start: 0, duration: 0};
  const caseDiffs = [];
  let failed = 0; let questionsCorrect = 0; let questionCases = 0; let exactCases = 0;
  for (const [index, fixture] of fixtures.cases.entries()) {
    const expected = fields(validateDraft(fixture.expected));
    let actual = []; let draft = null; let failure = null;
    try {
      const image = fixture.imageFile ? validateImage((await fs.readFile(path.join(__dirname, "../test/fixtures", fixture.imageFile))).toString("base64")) : undefined;
      const payload = await callProvider(providerBody({ageBand: fixtures.ageBand, today: fixtures.today, locale: fixtures.locale,
          text: fixture.text, current: [], currentSchool: fixture.currentSchool || [], ...(image ? {image} : {})},
        model));
      usage.add(payload);
      draft = validateDraft(payload.choices?.[0]?.message?.content); actual = fields(draft);
    } catch (error) { failed++; failure = failureCode(error); }
    // Missing/extra fields and hallucinated items count as failures.
    const count = Math.max(expected.length, actual.length, 1);
    for (let i = 0; i < count; i++) {
      for (const field of Object.keys(scores)) {
        if (field === "duration" && (expected[i]?.duration ?? null) === null && (actual[i]?.duration ?? null) === null) continue;
        totals[field]++;
        if (draft && expected[i]?.kind === actual[i]?.kind && JSON.stringify(expected[i]?.[field]) === JSON.stringify(actual[i]?.[field])) scores[field]++;
      }
    }
    if (fixture.expected.questions.length) {
      questionCases++;
      if (draft && draft.questions.length > 0 && actual.length === expected.length) questionsCorrect++;
    }
    const caseCorrect = draft && JSON.stringify(actual) === JSON.stringify(expected) &&
      (draft.questions.length > 0) === (fixture.expected.questions.length > 0);
    if (caseCorrect) exactCases++;
    const expectedResult = {fields: expected, hasQuestions: fixture.expected.questions.length > 0};
    const actualResult = draft ? {fields: actual, hasQuestions: draft.questions.length > 0} : null;
    caseDiffs.push({id: fixture.id || `${kind}-${String(index + 1).padStart(2, "0")}`,
      expected: expectedResult, actual: actualResult, differences: differences(expectedResult, actualResult),
      ...(failure ? {failure} : {})});
    // Synthetic case identifiers only; never output photos or model text.
    console.log(`${kind} ${fixture.id || `${kind}-${String(index + 1).padStart(2, "0")}`}: ${caseCorrect ? "PASS" : draft ? "FAIL SEMANTIC" : `FAIL ${failure}`}`);
  }
  console.log(`${kind}: ${fixtures.cases.length} cases; failed responses: ${failed}`);
  console.log(`Exact cases: ${exactCases}/${fixtures.cases.length} (${(100 * exactCases / fixtures.cases.length).toFixed(1)}%)`);
  for (const [field, correct] of Object.entries(scores)) console.log(`${field}: ${(100 * correct / Math.max(1, totals[field])).toFixed(1)}% (${correct}/${totals[field]})`);
  console.log(`Missing-information questions: ${questionsCorrect}/${questionCases}`);
  usage.print(kind);
  await writeCaseDiffs(kind, model, caseDiffs);
  // Gate each suite independently: good text scores cannot conceal bad vision scores.
  if (failed || questionsCorrect < questionCases || exactCases / fixtures.cases.length < 0.9 ||
    scores.days / totals.days < 0.9 || scores.start / totals.start < 0.9 ||
    totals.duration && scores.duration / totals.duration < 0.9) process.exitCode = 1;
}
async function main() {
  if (!process.env.OPENROUTER_API_KEY) {
    console.error("Set OPENROUTER_API_KEY to run the manual, paid schedule evaluation."); process.exitCode = 1; return;
  }
  if (process.argv.includes("--advise")) { await require("./eval-schedule-advise").evaluate(); return; }
  if (process.argv.includes("--log")) { await require("./eval-schedule-log-live").evaluate(); return; }
  if (!process.argv.includes("--images")) await evaluate(textFixtures, "text");
  if (!process.argv.includes("--text")) await evaluate(imageFixtures, "image");
}
main().catch(() => { console.error("EVAL_FAILED"); process.exitCode = 1; });
