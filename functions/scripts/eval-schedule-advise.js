// Manual paid evaluation; entry point checks for the key before any provider call.
const {providerBody, validateInput, validateAdvice} = require("../schedule-advise");
const {evaluateConstraints} = require("../schedule-advise-eval");
const fixtures = require("../test/fixtures/schedule-advise.vi.json");
const {callProvider, usageMeter, failureCode, writeCaseDiffs} = require("./eval-schedule-live-common");
async function evaluate() {
  const model = process.env.AI_SCHEDULE_ADVISE_MODEL || process.env.AI_SCHEDULE_MODEL || "google/gemini-2.5-flash-lite";
  console.log(`advise model: ${model}`);
  const usage = usageMeter();
  let passed = 0;
  const caseDiffs = [];
  for (const fixture of fixtures.cases) {
    let ok = false; let failure = null;
    let advice = null;
    try {
      const input = validateInput(fixture.input);
      const payload = await callProvider(providerBody(input, model));
      usage.add(payload);
      advice = validateAdvice(payload.choices?.[0]?.message?.content, input);
      ok = evaluateConstraints(input, advice, fixture.constraints);
    } catch (error) { failure = failureCode(error); }
    if (ok) passed++;
    caseDiffs.push({id: fixture.id, expected: fixture.constraints, actual: advice,
      differences: ok ? [] : [{path: "$.constraints", expected: fixture.constraints, actual: advice}],
      ...(failure ? {failure} : {})});
    console.log(`advise ${fixture.id}: ${ok ? "PASS" : `FAIL ${failure || "CONSTRAINT"}`}`);
  }
  console.log(`ADVISE constraints: ${passed}/${fixtures.cases.length} (${(100 * passed / fixtures.cases.length).toFixed(1)}%). Medical-language checks also require human review.`);
  usage.print("advise");
  await writeCaseDiffs("advise", model, caseDiffs);
  if (passed / fixtures.cases.length < 0.9) process.exitCode = 1;
}
module.exports = {evaluate};
