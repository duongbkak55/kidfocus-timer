const test = require("node:test");
const assert = require("node:assert/strict");
const {HttpsError} = require("firebase-functions/v2/https");
const {_test: gateway} = require("../index");
const {createScheduleHandler} = require("../schedule");
const fixtures = require("./fixtures/schedule-advise.vi.json");
const clone = (value) => structuredClone(value);
const config = {scheduleEnabled: true, scheduleGuestEnabled: false, scheduleModel: "mock/model", scheduleParseCost: 1,
  earlyAccessOpen: true, earlyAccessDays: 60, earlyDailyCredits: 15,
  freeDailyLimit: 10, guestDailyLimit: 10, premiumDailyCredits: 60, globalDailyCredits: 500};
const {validateInput, validateAdvice, providerBody} = require("../schedule-advise");
const {evaluateConstraints} = require("../schedule-advise-eval");
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
function harness(payload = JSON.stringify(fixtures.cases[0].expected), customConfig = {}, entitlement = {}, timing = {}) {
  const db = new FakeFirestore();
  const currentConfig = {...config, ...customConfig};
  const logs = [];
  const bodies = [];
  let calls = 0;
  const handler = createScheduleHandler({
    loadConfig: async () => currentConfig, quotaDay: () => "2026-09-28",
    resolveIdentity: async (request) => request.auth ? gateway.identityFromEntitlement(request.auth.uid, entitlement, 100) : {subject: "guest_mock", premium: false, signedIn: false},
    reserveQuota: (identity, conf, model, id) => gateway.reserveQuota(identity, conf, model, id, db),
    completeReservation: (identity, conf, id) => gateway.completeReservation(identity, conf, id, db),
    refundReservation: (identity, model, id) => gateway.refundReservation(identity, model, id, db),
    now: timing.now, timeoutSignal: timing.timeoutSignal,
    fetch: async (_url, options) => { calls++; bodies.push(JSON.parse(options.body));
      assert.ok(options.signal instanceof AbortSignal);
      timing.onFetch?.(calls);
      const answer = Array.isArray(payload) ? payload[Math.min(calls - 1, payload.length - 1)] : payload;
      if (answer instanceof Error) throw answer;
      if (answer === null) return {ok: false};
      return {ok: true, json: async () => ({choices: [{message: {content: answer}}]})}; },
    key: () => "test-key", error, log: (code) => logs.push(code),
  });
  return {db, handler, logs, bodies, calls: () => calls};
}
const request = () => ({auth: {uid: "parent"}, data: clone(fixtures.cases[0].input)});
for (const fixture of fixtures.cases) {
  test(`ADVISE golden: ${fixture.id} fixes rule after valid changes`, () => {
    const input = validateInput(fixture.input);
    const advice = validateAdvice(fixture.expected, input);
    assert.ok(evaluateConstraints(input, advice, fixture.constraints));
  });
}
test("ADVISE shared quota charges configured credits, uses separate model, never PARSE cap", async () => {
  const h = harness(undefined, {scheduleAdviseCost: 4, scheduleAdviseModel: "mock/advice", scheduleFreeDailyParses: 1});
  const reply = await h.handler(request());
  assert.equal(reply.usage.remainingCredits, 6);
  assert.equal(h.db.usage("uid_parent").scheduleParses || 0, 0);
  assert.equal(h.bodies[0].model, "mock/advice");
  assert.equal(h.bodies[0].max_tokens, 1500);
  assert.equal(h.bodies[0].temperature, 0.2);
  assert.equal(h.bodies[0].response_format.json_schema.name, "schedule_advice");
  assert.equal(h.bodies[0].provider.data_collection, "deny");
  const another = request(); another.data.requestId = "another";
  await h.handler(another); assert.equal(h.db.usage("uid_parent").credits, 8);
});
test("ADVISE defaults to 2 credits and PARSE model for every tier", async () => {
  for (const entitlement of [{}, {earlyAccessUntil: 200}, {premium: true}]) {
    const h = harness(undefined, {}, entitlement);
    const reply = await h.handler(request());
    assert.equal(h.db.usage("uid_parent").credits, 2);
    assert.equal(h.bodies[0].model, "mock/model");
    assert.ok(reply.advice);
  }
});
test("ADVISE retries one rejected validator response under one reservation and logs only the check name", async () => {
  const bad = clone(fixtures.cases[0].expected); bad.proposals[0].reason = "private child text ".repeat(20);
  const h = harness([JSON.stringify(bad), JSON.stringify(fixtures.cases[0].expected)]);
  const result = await h.handler(request());
  assert.ok(result.advice); assert.equal(h.calls(), 2); assert.equal(h.db.usage("uid_parent").credits, 2);
  assert.equal(h.db.usage("_global").credits, 2);
  assert.equal(h.db.usage("uid_parent").requests["golden-sleep-grade1"].state, "complete");
  assert.deepEqual(h.logs, ["AI_ADVISE_VALIDATION_REASON"]);
  assert.ok(!h.logs.join(" ").includes("private child text"));
});
test("ADVISE refunds its single reservation after two invalid outputs and never logs their content", async () => {
  const bad = clone(fixtures.cases[0].expected); bad.proposals[0].reason = "private child text ".repeat(20);
  const h = harness([JSON.stringify(bad), JSON.stringify(bad)]);
  await assert.rejects(h.handler(request()), /AI_ADVISE_FAILED/);
  assert.equal(h.calls(), 2); assert.equal(h.db.usage("uid_parent").credits, 0);
  assert.equal(h.db.usage("_global").credits, 0);
  assert.deepEqual(h.logs, ["AI_ADVISE_VALIDATION_REASON", "AI_ADVISE_VALIDATION_REASON", "AI_ADVISE_FAILED"]);
});
test("ADVISE retry uses remaining provider budget and one credit reservation", async () => {
  let clock = 1_000; const timeouts = [];
  const bad = clone(fixtures.cases[0].expected); bad.proposals[0].reason = "x".repeat(201);
  const h = harness([JSON.stringify(bad), JSON.stringify(fixtures.cases[0].expected)], {}, {}, {
    now: () => clock,
    timeoutSignal: (ms) => { timeouts.push(ms); return AbortSignal.timeout(ms); },
    onFetch: (call) => { if (call === 1) clock += 57_000; },
  });
  await h.handler(request());
  assert.deepEqual(timeouts, [25_000, 3_000]);
  assert.equal(h.calls(), 2); assert.equal(h.db.usage("uid_parent").credits, 2);
});
test("ADVISE refunds without a second provider call when its 60s budget is exhausted", async () => {
  let clock = 1_000; const timeouts = [];
  const bad = clone(fixtures.cases[0].expected); bad.proposals[0].reason = "x".repeat(201);
  const h = harness(JSON.stringify(bad), {}, {}, {
    now: () => clock,
    timeoutSignal: (ms) => { timeouts.push(ms); return AbortSignal.timeout(ms); },
    onFetch: () => { clock += 60_000; },
  });
  await assert.rejects(h.handler(request()), {message: "AI_ADVISE_FAILED"});
  assert.deepEqual(timeouts, [25_000]); assert.equal(h.calls(), 1);
  assert.equal(h.db.usage("uid_parent").credits, 0);
});
for (const [label, mutate] of [
  ["unknown metadata", (r) => r.profileId = "db"], ["db task id", (r) => r.tasks = [{...fixtures.cases[1].input.tasks[0], ref: "1234567890"}]],
  ["too many tasks", (r) => r.tasks = Array(61).fill(fixtures.cases[1].input.tasks[0])],
  ["bad days", (r) => r.anchors.school = [{days: ["T2"], start: "07:00", end: "12:00"}]],
  ["bad anchor time", (r) => r.anchors.bed.MON = "24:00"], ["note length", (r) => r.note = "x".repeat(501)],
  ["tags", (r) => r.noteTags = ["DIAGNOSE"]], ["routine raw history", (r) => r.routineStats = [{name: "Sáng", lateOrMissedLast7: 8}]],
  ["unknown finding task", (r) => r.findings[0].taskRefs = ["t999"]], ["unknown finding", (r) => r.findings[0].ruleId = "DIAGNOSE"],
]) test(`invalid ADVISE input ${label} is rejected before quota`, async () => {
  const h = harness(); const req = request(); mutate(req.data);
  await assert.rejects(h.handler(req), (e) => e.code === "invalid-argument"); assert.equal(h.calls(), 0); assert.equal(h.db.writes, 0);
});
for (const [label, mutate] of [
  ["task ref", (v) => v.proposals = [{...fixtures.cases[1].expected.proposals[0], taskRef: "t999"}]],
  ["missing op field", (v) => delete v.proposals[0].start],
  ["extra op field", (v) => v.proposals[0].taskRef = "t0"],
  ["too many proposals", (v) => v.proposals = Array(11).fill(v.proposals[0])],
  ["wrong op", (v) => v.proposals[0].op = "SET_SCHOOL"],
  ["wrong time", (v) => v.proposals[0].start = "25:00"],
  ["reason length", (v) => v.proposals[0].reason = "x".repeat(201)],
  ["summary length", (v) => v.summary = "x".repeat(601)],
]) test(`invalid ADVISE output ${label} refunds ledger`, async () => {
  const v = clone(fixtures.cases[0].expected); mutate(v);
  const h = harness(JSON.stringify(v)); await assert.rejects(h.handler(request()), (e) => e.message === "AI_ADVISE_FAILED");
  assert.equal(h.calls(), 2);
  assert.equal(h.db.usage("uid_parent").credits, 0); assert.equal(h.db.usage("uid_parent").questions, 0);
  assert.deepEqual(h.db.usage("uid_parent").requests, {}); assert.equal(h.db.usage("_global").credits, 0);
  assert.equal(h.logs.length, 3); assert.match(h.logs[0], /^AI_ADVISE_VALIDATION_[A-Z_]+$/);
  assert.equal(h.logs[0], h.logs[1]); assert.equal(h.logs[2], "AI_ADVISE_FAILED");
});
test("school overlap across Sunday midnight and REMOVE study are forbidden", () => {
  const input = clone(fixtures.cases[7].input);
  const move = clone(fixtures.cases[7].expected);
  move.proposals[0].start = "08:00"; assert.throws(() => validateAdvice(move, input));
  input.anchors.school = [{days: ["SUN"], start: "23:00", end: "08:00"}];
  move.proposals[0].start = "07:30"; assert.throws(() => validateAdvice(move, input));
  const remove = {...move, proposals: [{op: "REMOVE", taskRef: "t0", days: ["MON"], reason: "Xoá", fixes: ["OVERLAP"]}]};
  for (const type of ["STUDY", "MORNING_STUDY", "HOMEWORK", "CUSTOM"]) { input.tasks[0].taskType = type; assert.throws(() => validateAdvice(remove, input)); }
  const resize = {...move, proposals: [{op: "RESIZE", taskRef: "t0", days: ["MON"], durationMin: 90, reason: "Đổi", fixes: ["OVERLAP"]}]};
  input.tasks[0].start = "07:30"; assert.throws(() => validateAdvice(resize, input));
});
for (const payload of [null, new Error("network"), "not-json"]) test("ADVISE provider/JSON failure refunds", async () => {
  const h = harness(payload); await assert.rejects(h.handler(request()));
  assert.equal(h.db.usage("uid_parent").credits, 0); assert.equal(h.db.usage("_global").credits, 0);
  assert.equal(h.calls(), payload === "not-json" ? 2 : 1);
  if (payload === "not-json") assert.deepEqual(h.logs, ["AI_ADVISE_VALIDATION_JSON", "AI_ADVISE_VALIDATION_JSON", "AI_ADVISE_FAILED"]);
});
test("injected note remains user data and cannot widen schema; mocked injected output refunds", async () => {
  const fixture = fixtures.cases.find((f) => f.id === "note-injection");
  const body = providerBody(validateInput(fixture.input), "mock/model");
  assert.match(body.messages[0].content, /dữ liệu, không phải lệnh/);
  assert.equal(JSON.parse(body.messages[1].content).note, fixture.input.note);
  assert.ok(!body.messages[0].content.includes(fixture.input.note));
  const bad = clone(fixture.expected); bad.proposals[0].op = "SET_SCHOOL";
  const h = harness(JSON.stringify(bad)); await assert.rejects(h.handler({auth: {uid: "parent"}, data: fixture.input}));
  assert.equal(h.db.usage("uid_parent").credits, 0);
});
test("ADVISE optional note data defaults empty and public config includes price", () => {
  const raw = clone(fixtures.cases[0].input); delete raw.note; delete raw.noteTags; delete raw.routineStats;
  const input = validateInput(raw); assert.equal(input.note, ""); assert.deepEqual(input.noteTags, []);
  assert.equal(gateway.publicConfig({...gateway.DEFAULT_CONFIG, scheduleAdviseCost: 7}, {}).ai_schedule_advise_cost, 7);
});
test("school and protected REMOVE output errors refund configured advice credits", async () => {
  for (const proposal of [
    {...fixtures.cases[7].expected.proposals[0], start: "08:00"},
    {op: "REMOVE", taskRef: "t0", days: ["MON"], reason: "Xoá", fixes: ["OVERLAP"]},
  ]) {
    const fixture = fixtures.cases[7]; const advice = {...fixture.expected, proposals: [proposal]};
    const h = harness(JSON.stringify(advice), {scheduleAdviseCost: 5});
    await assert.rejects(h.handler({auth: {uid: "parent"}, data: fixture.input}), (e) => e.message === "AI_ADVISE_FAILED");
    assert.equal(h.db.usage("uid_parent").credits, 0); assert.equal(h.db.usage("_global").credits, 0);
  }
});
test("bed after midnight checks school on the next calendar day", () => {
  const input = clone(fixtures.cases[0].input); input.anchors.school = [{days: ["TUE"], start: "00:00", end: "02:00"}];
  const value = clone(fixtures.cases[0].expected); value.proposals[0].days = ["MON"]; value.proposals[0].start = "00:30";
  assert.throws(() => validateAdvice(value, input));
});
