const test = require("node:test");
const assert = require("node:assert/strict");
const {differences} = require("../scripts/eval-schedule-live-common");

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
