// Manual provider evaluation only. Never called by npm test.
const {validateDraft, providerBody, DAYS} = require("../schedule");
const fixtures = require("../test/fixtures/schedule-parse.vi.json");
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
    ...hourGroups("wake", draft.anchors.wake),
    ...hourGroups("bed", draft.anchors.bed),
    ...draft.anchors.school.map((block) => ({kind: "school", days: block.days, start: block.start,
      duration: (minutes(block.end) - minutes(block.start) + 1440) % 1440})),
  ];
  return items.sort((a, b) => JSON.stringify(a).localeCompare(JSON.stringify(b)));
}
function minutes(time) { const [h, m] = time.split(":").map(Number); return h * 60 + m; }
async function main() {
  if (!process.env.OPENROUTER_API_KEY) {
    console.error("Set OPENROUTER_API_KEY to run the manual, paid schedule evaluation.");
    process.exitCode = 1;
    return;
  }
  const scores = {days: 0, start: 0, duration: 0};
  const totals = {days: 0, start: 0, duration: 0};
  let failed = 0;
  let questionsCorrect = 0;
  let questionCases = 0;
  for (const fixture of fixtures.cases) {
    const expected = fields(validateDraft(fixture.expected));
    let actual = [];
    let draft = null;
    try {
      const response = await fetch("https://openrouter.ai/api/v1/chat/completions", {
        method: "POST", headers: {"Authorization": `Bearer ${process.env.OPENROUTER_API_KEY}`, "Content-Type": "application/json"},
        body: JSON.stringify(providerBody({...fixtures, text: fixture.text, current: []},
            process.env.AI_SCHEDULE_MODEL || "google/gemini-2.5-flash-lite")), signal: AbortSignal.timeout(30_000),
      });
      if (!response.ok) throw new Error("PROVIDER_FAILED");
      const payload = await response.json();
      draft = validateDraft(payload.choices?.[0]?.message?.content);
      actual = fields(draft);
    } catch { failed++; }
    // Missing and extra items count as wrong fields. Empty question-only cases also
    // count, so hallucinated task fields cannot produce a misleading 100% score.
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
      if (draft && draft.questions.length > 0 && actual.length === 0) questionsCorrect++;
    }
  }
  console.log(`Cases: ${fixtures.cases.length}; failed responses: ${failed}`);
  for (const [field, correct] of Object.entries(scores)) console.log(`${field}: ${(100 * correct / Math.max(1, totals[field])).toFixed(1)}% (${correct}/${totals[field]})`);
  console.log(`Missing-information questions: ${questionsCorrect}/${questionCases}`);
  if (failed || questionsCorrect < questionCases || scores.days / totals.days < 0.9 || scores.start / totals.start < 0.9) process.exitCode = 1;
}
main().catch(() => { console.error("EVAL_FAILED"); process.exitCode = 1; });
