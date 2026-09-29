// Offline only: no keys, network, or production writes. Does not claim live model accuracy.
const assert = require("node:assert/strict");
const {validateInput, validateLog} = require("../schedule-log");
const fixtures = require("../test/fixtures/schedule-log.vi.json");
function evaluate(fixture, providerFixture = fixture.providerFixture) {
  const input = validateInput(fixture.input);
  const output = validateLog(providerFixture, input);
  assert.deepEqual(output.entries.map(({date, planRef, category, start, end}) => ({date, planRef, category, start, end})), fixture.constraints.entries);
  assert.equal(output.questions.length > 0, fixture.constraints.mustAsk);
  assert.equal(output.entries.some((e) => e.confidence < 0.6), fixture.constraints.lowConfidenceUnselected);
  return output;
}
if (require.main === module) {
  let passed = 0;
  for (const fixture of fixtures.cases) {
    try { evaluate(fixture); passed++; console.log(`${fixture.id}: PASS`); }
    catch { console.log(`${fixture.id}: FAIL`); }
  }
  console.log(`LOG offline contracts: ${passed}/${fixtures.cases.length} (synthetic responses; live model accuracy not evaluated)`);
  if (passed !== fixtures.cases.length) process.exitCode = 1;
}
module.exports = {evaluate};
