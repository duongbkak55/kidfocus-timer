const test = require("node:test");
const assert = require("node:assert/strict");
const {_test} = require("../index");

test("sanitizeMessages caps history and input length", () => {
  const messages = Array.from({length: 6}, (_, index) => ({
    role: index % 2 ? "assistant" : "user",
    content: `message-${index}-` + "x".repeat(30),
  }));
  const result = _test.sanitizeMessages(messages, {maxHistoryMessages: 3, maxInputChars: 12});
  assert.equal(result.length, 3);
  assert.equal(result[0].content.length, 12);
  assert.equal(result[0].role, "assistant");
});

test("normalizeModel rejects missing ids and clamps limits", () => {
  assert.equal(_test.normalizeModel({label: "missing"}), null);
  assert.deepEqual(
      _test.normalizeModel({id: "provider/model", creditCost: 0, dailyLimit: 5000}),
      {
        id: "provider/model",
        label: "provider/model",
        description: "",
        creditCost: 1,
        dailyLimit: 1000,
        premiumOnly: false,
      },
  );
});

test("premium usage is credit based while free usage is question based", () => {
  const config = {premiumDailyCredits: 60, freeDailyLimit: 10, guestDailyLimit: 10};
  assert.deepEqual(
      _test.usageFromData({premium: true, signedIn: true}, config, {questions: 2, credits: 13}),
      {premium: true, remainingQuestions: 58, remainingCredits: 47},
  );
  assert.deepEqual(
      _test.usageFromData({premium: false, signedIn: false}, config, {questions: 3, credits: 3}),
      {premium: false, remainingQuestions: 7, remainingCredits: 7},
  );
});
