const test = require("node:test");
const assert = require("node:assert/strict");
const {HttpsError} = require("firebase-functions/v2/https");
const {_test: gateway} = require("../index");
const {validateDraft, validateInput, createScheduleHandler, claimEarlyAccess} = require("../schedule");
const fixtures = require("./fixtures/schedule-parse.vi.json");
const clone = (value) => structuredClone(value);
const config = {scheduleEnabled: true, scheduleGuestEnabled: false, scheduleModel: "mock/model", scheduleParseCost: 1,
  earlyAccessOpen: true, earlyAccessDays: 60, earlyDailyCredits: 15,
  freeDailyLimit: 10, guestDailyLimit: 10, premiumDailyCredits: 60, globalDailyCredits: 500};
const error = (code, message) => new HttpsError(code, message);
class FakeFirestore {
  constructor() { this.rows = new Map(); this.pending = Promise.resolve(); this.writes = 0; }
  collection(path) { return {doc: (id) => this.ref(`${path}/${id}`)}; }
  ref(path) {
    return {path, collection: (name) => ({doc: (id) => this.ref(`${path}/${name}/${id}`)}), get: async () => this.snapshot(path)};
  }
  snapshot(path) {
    const data = this.rows.get(path);
    return {data: () => clone(data), get: (key) => clone(data?.[key])};
  }
  runTransaction(fn) {
    const run = this.pending.then(async () => {
      const writes = [];
      const result = await fn({
        get: async (ref) => { assert.equal(writes.length, 0, "All transaction reads precede writes"); return this.snapshot(ref.path); },
        set: (ref, data, opts) => writes.push({ref, data: clone(data), opts}),
      });
      for (const {ref, data, opts} of writes) {
        const merged = opts?.merge ? deepMerge(this.rows.get(ref.path) || {}, data) : data;
        this.rows.set(ref.path, merged); this.writes++;
      }
      return result;
    });
    this.pending = run.catch(() => undefined);
    return run;
  }
  usage(subject) { return [...this.rows].find(([path]) => path.startsWith(`internalAiQuota/${subject}/days/`))?.[1]; }
}
function deepMerge(before, after) {
  const result = {...before};
  for (const [key, value] of Object.entries(after)) result[key] = value && typeof value === "object" && !Array.isArray(value) ? deepMerge(result[key] || {}, value) : value;
  return result;
}
function harness(payload = JSON.stringify(fixtures.cases[0].expected), customConfig = {}) {
  const db = new FakeFirestore();
  const currentConfig = {...config, ...customConfig};
  const logs = [];
  const bodies = [];
  let calls = 0;
  const handler = createScheduleHandler({
    loadConfig: async () => currentConfig, quotaDay: () => "2026-09-28",
    resolveIdentity: async (request) => request.auth ? gateway.identityFromEntitlement(request.auth.uid, {}, 100) : {subject: "guest_mock", premium: false, signedIn: false},
    reserveQuota: (identity, conf, model, id) => gateway.reserveQuota(identity, conf, model, id, db),
    completeReservation: (identity, conf, id) => gateway.completeReservation(identity, conf, id, db),
    refundReservation: (identity, model, id) => gateway.refundReservation(identity, model, id, db),
    fetch: async (_url, options) => { calls++; bodies.push(JSON.parse(options.body));
      assert.ok(options.signal instanceof AbortSignal);
      if (payload instanceof Error) throw payload;
      if (payload === null) return {ok: false};
      return {ok: true, json: async () => ({choices: [{message: {content: payload}}]})}; },
    key: () => "test-key", error, log: (code) => logs.push(code),
  });
  return {db, handler, logs, bodies, calls: () => calls};
}
const request = () => ({auth: {uid: "parent"}, data: {requestId: "request-1", text: fixtures.cases[0].text,
  ageBand: fixtures.ageBand, today: fixtures.today, locale: fixtures.locale}});

for (const [index, fixture] of fixtures.cases.entries()) {
  test(`Vietnamese golden ${index + 1}: expected draft validates and normalizes`, () => {
    assert.deepEqual(validateDraft(JSON.stringify(fixture.expected)), fixture.expected);
    assert.equal(validateInput({...request().data, text: fixture.text}).text, fixture.text);
  });
}
test("valid parse uses schema and zero temperature, shares quota and completes reservation", async () => {
  const h = harness();
  const result = await h.handler(request());
  assert.deepEqual(result.draft, fixtures.cases[0].expected);
  assert.equal(result.usage.remainingCredits, 9);
  assert.equal(result.usage.tier, "free");
  assert.equal(h.db.usage("uid_parent").requests["request-1"].state, "complete");
  assert.equal(h.bodies[0].temperature, 0);
  assert.equal(h.bodies[0].response_format.type, "json_schema");
  assert.equal(h.bodies[0].provider.data_collection, "deny");
  assert.equal(h.bodies[0].model, "mock/model");
  assert.equal(h.logs.length, 0);
});
for (const [label, mutate] of [
  ["unknown day", (d) => d.tasks[0].days.push("T2")],
  ["invalid time", (d) => d.tasks[0].start = "24:00"],
  ["duration under minimum", (d) => d.tasks[0].durationMin = 4],
  ["duration over maximum", (d) => d.tasks[0].durationMin = 121],
  ["fractional duration", (d) => d.tasks[0].durationMin = 10.5],
  ["empty name", (d) => d.tasks[0].name = " "],
  ["long name", (d) => d.tasks[0].name = "a".repeat(61)],
  ["missing field", (d) => delete d.tasks[0].source],
  ["too many tasks", (d) => d.tasks = Array(31).fill(d.tasks[0])],
  ["invalid confidence", (d) => d.tasks[0].confidence = 1.1],
  ["bad anchor day", (d) => d.anchors.wake = {MO: "06:30"}],
  ["extra metadata", (d) => d.childName = "private"],
]) {
  test(`invalid schema ${label} refunds quota and returns only error code`, async () => {
    const invalid = clone(fixtures.cases[0].expected); mutate(invalid);
    const h = harness(JSON.stringify(invalid));
    await assert.rejects(h.handler(request()), {code: "unavailable", message: "AI_PARSE_FAILED"});
    assert.equal(h.db.usage("uid_parent").credits, 0);
    assert.equal(h.db.usage("_global").credits, 0);
    assert.equal(h.db.usage("uid_parent").requests["request-1"], undefined);
    assert.deepEqual(h.logs, ["AI_PARSE_FAILED"]);
  });
}
test("broken JSON refunds once; same request may retry after refund", async () => {
  const h = harness("not JSON");
  await assert.rejects(h.handler(request()), {message: "AI_PARSE_FAILED"});
  await assert.rejects(h.handler(request()), {message: "AI_PARSE_FAILED"});
  assert.equal(h.calls(), 2);
  assert.equal(h.db.usage("uid_parent").credits, 0);
});
test("unknown taskType falls back to CUSTOM and days normalize", () => {
  const draft = clone(fixtures.cases[0].expected);
  draft.tasks[0].taskType = "HOC_THEM";
  draft.tasks[0].days.reverse();
  assert.equal(validateDraft(draft).tasks[0].taskType, "CUSTOM");
  assert.deepEqual(validateDraft(draft).tasks[0].days, ["TUE", "THU"]);
});
test("guest blocked before quota or provider; disabled schedule and invalid input fail early", async () => {
  const h = harness();
  await assert.rejects(h.handler({...request(), auth: null}), {code: "unauthenticated"});
  for (const data of [{...request().data, text: "x".repeat(2001)}, {...request().data, current: Array(61).fill({})},
    {...request().data, childName: "secret"}, {...request().data, image: "base64"}, {...request().data, today: "2026-02-30"},
    {...request().data, requestId: "__proto__"}]) await assert.rejects(h.handler({...request(), data}), {code: "invalid-argument"});
  await assert.rejects(harness("", {scheduleEnabled: false}).handler(request()), {code: "failed-precondition"});
  assert.equal(h.calls(), 0);
  assert.equal(h.db.writes, 0);
});
test("tiers prefer premium, honor expiry boundary and return usage expiry", () => {
  for (const [entitlement, tier, remaining] of [[{earlyAccessUntil: 101}, "early", 12],
    [{earlyAccessUntil: 100}, "free", 7], [{premium: true, earlyAccessUntil: 101}, "premium", 57]]) {
    const identity = gateway.identityFromEntitlement("parent", entitlement, 100);
    assert.equal(identity.tier, tier);
    const usage = gateway.usageFromData(identity, config, {questions: 2, credits: 3});
    assert.equal(usage.remainingCredits, remaining);
    assert.equal(usage.earlyAccessUntil, entitlement.earlyAccessUntil);
  }
});
test("early/free/premium/guest enforce credit budgets through real quota functions", async () => {
  for (const [identity, limit] of [[gateway.identityFromEntitlement("p", {earlyAccessUntil: 200}, 100), 15],
    [gateway.identityFromEntitlement("p", {}, 100), 10], [gateway.identityFromEntitlement("p", {premium: true}, 100), 60],
    [{subject: "guest_mock", premium: false, signedIn: false}, 10]]) {
    const db = new FakeFirestore();
    const model = {id: "model", creditCost: 1, dailyLimit: 1000};
    for (let i = 0; i < limit; i++) await gateway.reserveQuota(identity, config, model, `r-${i}`, db);
    await assert.rejects(gateway.reserveQuota(identity, config, model, "too-many", db), {code: "resource-exhausted", message: "DAILY_LIMIT_REACHED"});
    assert.equal(db.usage(identity.subject).credits, limit);
  }
});
test("parse cost, global cap and duplicate request each prevent overcharging", async () => {
  const h = harness(JSON.stringify(fixtures.cases[0].expected), {scheduleParseCost: 3, globalDailyCredits: 3});
  const result = await h.handler(request());
  assert.equal(result.usage.remainingCredits, 7);
  await assert.rejects(h.handler(request()), {message: "DUPLICATE_REQUEST"});
  await assert.rejects(h.handler({...request(), data: {...request().data, requestId: "r-2"}}), {message: "GLOBAL_DAILY_LIMIT_REACHED"});
  assert.equal(h.calls(), 1);
  assert.equal(h.db.usage("_global").credits, 3);
});
test("claim is transactional/idempotent under concurrent requests, preserves premium and old expiry", async () => {
  const db = new FakeFirestore();
  db.rows.set("entitlements/parent", {premium: true});
  const call = (now) => claimEarlyAccess({db, uid: "parent", config, now, error});
  const results = await Promise.all(Array.from({length: 10}, (_, i) => call(100 + i)));
  assert.ok(results.every((r) => r.earlyAccessUntil === 100 + 60 * 86_400_000));
  assert.equal(db.writes, 1);
  assert.equal(db.rows.get("entitlements/parent").premium, true);
  assert.deepEqual(await call(100 + 61 * 86_400_000), results[0]);
  assert.equal(db.writes, 1);
});
test("closed enrollment refuses existing/new users; unauthenticated cannot claim", async () => {
  const db = new FakeFirestore();
  await assert.rejects(claimEarlyAccess({db, uid: null, config, now: 1, error}), {code: "unauthenticated"});
  await assert.rejects(claimEarlyAccess({db, uid: "new", config: {...config, earlyAccessOpen: false}, now: 1, error}), {message: "EARLY_ACCESS_CLOSED"});
  db.rows.set("entitlements/old", {earlyAccessUntil: 10});
  await assert.rejects(claimEarlyAccess({db, uid: "old", config: {...config, earlyAccessOpen: false}, now: 1, error}), {message: "EARLY_ACCESS_CLOSED"});
  assert.equal(db.writes, 0);
  assert.equal(db.rows.get("entitlements/old").earlyAccessUntil, 10);
});

test("completion/refund use the frozen reservation date across midnight", async () => {
  const db = new FakeFirestore();
  const identity = {...gateway.identityFromEntitlement("parent", {}, 100), quotaDate: "2026-09-28"};
  const model = {id: "model", creditCost: 2, dailyLimit: 1000};
  await gateway.reserveQuota(identity, config, model, "r-1", db);
  await gateway.refundReservation(identity, model, "r-1", db);
  await gateway.refundReservation(identity, model, "r-1", db);
  assert.equal(db.rows.get("internalAiQuota/uid_parent/days/2026-09-28").credits, 0);
  assert.equal(db.rows.get("internalAiQuota/_global/days/2026-09-28").credits, 0);
  assert.equal(db.rows.size, 2);
});

for (const [label, payload] of [["provider HTTP failure", null], ["network failure", new Error("private text must not be logged")]]) {
  test(`${label} refunds quota and never logs provider/user text`, async () => {
    const h = harness(payload);
    await assert.rejects(h.handler(request()), {message: "AI_PARSE_FAILED"});
    assert.equal(h.db.usage("uid_parent").credits, 0);
    assert.equal(h.db.usage("_global").credits, 0);
    assert.deepEqual(h.logs, ["AI_PARSE_FAILED"]);
  });
}
