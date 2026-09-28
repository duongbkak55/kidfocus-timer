// Manual paid evaluation; entry point checks for the key before any provider call.
const {providerBody, validateInput, validateAdvice} = require("../schedule-advise");
const {evaluateConstraints} = require("../schedule-advise-eval");
const fixtures = require("../test/fixtures/schedule-advise.vi.json");
async function evaluate() {
  let passed = 0;
  for (const fixture of fixtures.cases) {
    let ok = false;
    try {
      const input = validateInput(fixture.input);
      const response = await fetch("https://openrouter.ai/api/v1/chat/completions", {
        method: "POST", headers: {"Authorization": `Bearer ${process.env.OPENROUTER_API_KEY}`, "Content-Type": "application/json"},
        body: JSON.stringify(providerBody(input, process.env.AI_SCHEDULE_ADVISE_MODEL || process.env.AI_SCHEDULE_MODEL || "google/gemini-2.5-flash-lite")), signal: AbortSignal.timeout(30_000),
      });
      if (!response.ok) throw new Error("PROVIDER_FAILED");
      const payload = await response.json();
      const advice = validateAdvice(payload.choices?.[0]?.message?.content, input);
      ok = evaluateConstraints(input, advice, fixture.constraints);
    } catch { /* Only print synthetic ids and pass/fail, never model text or notes. */ }
    if (ok) passed++;
    console.log(`advise ${fixture.id}: ${ok ? "PASS" : "FAIL"}`);
  }
  console.log(`ADVISE constraints: ${passed}/${fixtures.cases.length}. Medical-language checks also require human review.`);
  if (passed !== fixtures.cases.length) process.exitCode = 1;
}
module.exports = {evaluate};
