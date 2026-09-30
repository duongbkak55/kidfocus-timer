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
    let attempts = 0; const validatorChecks = [];
    try {
      const input = validateInput(fixture.input);
      for (let attempt = 0; attempt < 2; attempt++) {
        const payload = await callProvider(providerBody(input, model));
        attempts++; usage.add(payload);
        try { advice = validateAdvice(payload.choices?.[0]?.message?.content, input); } catch (error) {
          validatorChecks.push(error?.checkName || "UNKNOWN");
          if (attempt === 1) throw error;
          continue;
        }
        ok = evaluateConstraints(input, advice, fixture.constraints);
        break;
      }
    } catch (error) { failure = failureCode(error); }
    if (ok) passed++;
    caseDiffs.push({id: fixture.id, attempts, validatorChecks, expected: fixture.constraints, actual: advice,
      differences: ok ? [] : [{path: "$.constraints", expected: fixture.constraints, actual: advice}],
      ...(failure ? {failure} : {})});
    console.log(`advise ${fixture.id}: ${ok ? "PASS" : `FAIL ${failure || "CONSTRAINT"}`}`);
  }
  console.log(`ADVISE constraints: ${passed}/${fixtures.cases.length} (${(100 * passed / fixtures.cases.length).toFixed(1)}%). Medical-language checks also require human review.`);
  usage.print("advise");
  const measured = usage.totals();
  console.log(`ADVISE actual OpenRouter cost USD per logical case including retry: ${measured.costResponses ? `$${(measured.costUsd / fixtures.cases.length).toFixed(8)}` : "unavailable"} (${measured.costResponses} measured responses / ${fixtures.cases.length} cases)`);
  await writeCaseDiffs("advise", model, caseDiffs);
  if (passed / fixtures.cases.length < 0.9) process.exitCode = 1;
}
module.exports = {evaluate};
