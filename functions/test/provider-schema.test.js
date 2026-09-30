const test = require("node:test");
const assert = require("node:assert/strict");
const parse = require("../schedule");
const advise = require("../schedule-advise");
const log = require("../schedule-log");
const parseFixtures = require("./fixtures/schedule-parse.vi.json");
const adviseFixtures = require("./fixtures/schedule-advise.vi.json");
const logFixtures = require("./fixtures/schedule-log.vi.json");

const unsupported = new Set(["pattern", "minLength", "maxLength", "minItems", "maxItems", "uniqueItems", "minimum", "maximum", "const"]);
function walk(value) {
  if (Array.isArray(value)) return value.forEach(walk);
  if (value === null || typeof value !== "object") return;
  for (const [key, child] of Object.entries(value)) {
    assert.equal(unsupported.has(key), false, `provider schema contains ${key}`);
    walk(child);
  }
}

const parseInput = {text: parseFixtures.cases[0].text, ageBand: parseFixtures.ageBand, today: parseFixtures.today,
  locale: parseFixtures.locale, current: [], currentSchool: []};
for (const [name, body] of [
  ["PARSE text", parse.providerBody(parseInput, "mock/model")],
  ["PARSE image", parse.providerBody({...parseInput, image: "synthetic-image"}, "mock/model")],
  ["ADVISE", advise.providerBody(advise.validateInput(adviseFixtures.cases[0].input), "mock/model")],
  ["LOG", log.providerBody(log.validateInput(logFixtures.cases[0].input), "mock/model")],
]) {
  test(`${name} sends a compact provider schema while retaining private-data policy`, () => {
    assert.equal(body.response_format.type, "json_schema");
    assert.equal(body.provider.data_collection, "deny");
    walk(body.response_format.json_schema.schema);
  });
}
test("provider schema retains object shape, enums and required fields", () => {
  const schema = parse.providerBody(parseInput, "mock/model").response_format.json_schema.schema;
  assert.deepEqual(schema.required, ["anchors", "tasks", "questions"]);
  assert.equal(schema.additionalProperties, false);
  assert.deepEqual(schema.properties.tasks.items.properties.days.items.enum, parse.DAYS);
  const advice = advise.providerBody(advise.validateInput(adviseFixtures.cases[0].input), "mock/model").response_format.json_schema.schema;
  assert.deepEqual(advice.properties.proposals.items.anyOf[0].properties.op.enum, ["MOVE"]);
});
