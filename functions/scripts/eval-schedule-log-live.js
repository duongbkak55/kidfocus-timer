// Manual live evaluation of the offline LOG golden cases; no app data is sent.
const assert = require("node:assert/strict");
const {validateInput, validateLog, providerBody} = require("../schedule-log");
const fixtures = require("../test/fixtures/schedule-log.vi.json");
const {callProvider, usageMeter, failureCode} = require("./eval-schedule-live-common");

async function evaluate() {
  const model = process.env.AI_SCHEDULE_LOG_MODEL || process.env.AI_SCHEDULE_MODEL || "google/gemini-2.5-flash-lite";
  console.log(`log model: ${model}`);
  const usage = usageMeter();
  let passed = 0;
  for (const fixture of fixtures.cases) {
    let ok = false; let failure = null;
    try {
      const input = validateInput(fixture.input);
      const payload = await callProvider(providerBody(input, model));
      usage.add(payload);
      const output = validateLog(payload.choices?.[0]?.message?.content, input);
      assert.deepEqual(output.entries.map(({date, planRef, category, start, end}) => ({date, planRef, category, start, end})), fixture.constraints.entries);
      assert.equal(output.questions.length > 0, fixture.constraints.mustAsk);
      assert.equal(output.entries.some((entry) => entry.confidence < 0.6), fixture.constraints.lowConfidenceUnselected);
      ok = true;
    } catch (error) { failure = failureCode(error); }
    if (ok) passed++;
    console.log(`log ${fixture.id}: ${ok ? "PASS" : `FAIL ${failure || "CONSTRAINT"}`}`);
  }
  console.log(`LOG golden: ${passed}/${fixtures.cases.length} (${(100 * passed / fixtures.cases.length).toFixed(1)}%)`);
  usage.print("log");
  if (passed / fixtures.cases.length < 0.9) process.exitCode = 1;
}
module.exports = {evaluate};
