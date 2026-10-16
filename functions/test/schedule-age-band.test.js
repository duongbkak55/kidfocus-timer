const test = require("node:test");
const assert = require("node:assert/strict");
const parse = require("../schedule");
const log = require("../schedule-log");
const advise = require("../schedule-advise");
const parseFixture = require("./fixtures/schedule-parse.vi.json");
const logFixture = require("./fixtures/schedule-log.vi.json");
const adviseFixture = require("./fixtures/schedule-advise.vi.json");

test("five-to-six band is accepted by PARSE, LOG and ADVISE validators", () => {
  const parseInput = {requestId: "age-5-6", text: parseFixture.cases[0].text,
    ageBand: "5-6", today: parseFixture.today, locale: parseFixture.locale};
  assert.equal(parse.validateInput(parseInput).ageBand, "5-6");
  assert.equal(log.validateInput({...logFixture.cases[0].input, ageBand: "5-6"}).ageBand, "5-6");
  assert.equal(advise.validateInput({...adviseFixture.cases[0].input, ageBand: "5-6"}).ageBand, "5-6");
  for (const validator of [
    () => parse.validateInput({...parseInput, ageBand: "unknown"}),
    () => log.validateInput({...logFixture.cases[0].input, ageBand: "unknown"}),
    () => advise.validateInput({...adviseFixture.cases[0].input, ageBand: "unknown"}),
  ]) assert.throws(validator);
});
