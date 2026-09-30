const test = require("node:test");
const assert = require("node:assert/strict");
const {differences, gradeParseCase, callProvider} = require("../scripts/eval-schedule-live-common");

test("synthetic eval diff identifies missing fields and changed schedule values", () => {
  const expected = {fields: [{days: ["MON", "WED"], start: "07:15"}], hasQuestions: false};
  const actual = {fields: [{days: ["MON", "TUE"], start: "07:30"}, {days: ["SAT"]}], hasQuestions: true};
  assert.deepEqual(differences(expected, actual), [
    {path: "$.fields.0.days.1", expected: "WED", actual: "TUE"},
    {path: "$.fields.0.start", expected: "07:15", actual: "07:30"},
    {path: "$.fields.1", expected: "<missing>", actual: {days: ["SAT"]}},
    {path: "$.hasQuestions", expected: false, actual: true},
  ]);
});

test("image grader accepts an extra question only when all fields match", () => {
  const fields = [{kind: "school", days: ["MON"], start: "07:15", duration: 240}];
  assert.deepEqual(gradeParseCase("image", fields, fields, false, true, true),
    {pass: true, fieldsMatch: true, missingQuestion: false, extraQuestion: true});
  assert.equal(gradeParseCase("image", fields, [], false, true, true).pass, false);
  assert.equal(gradeParseCase("image", fields, fields, true, false, true).pass, false);
  assert.equal(gradeParseCase("text", fields, fields, false, true, true).pass, false);
});

test("live eval requests measured OpenRouter usage without changing provider privacy policy", async () => {
  const original = global.fetch;
  global.fetch = async (url, options) => {
    assert.match(url, /openrouter\.ai/);
    const body = JSON.parse(options.body);
    assert.deepEqual(body.usage, {include: true});
    assert.deepEqual(body.provider, {data_collection: "deny"});
    return {ok: true, json: async () => ({usage: {cost: 0.0001}})};
  };
  try { assert.equal((await callProvider({model: "mock/model", provider: {data_collection: "deny"}})).usage.cost, 0.0001); } finally { global.fetch = original; }
});
