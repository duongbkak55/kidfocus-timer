const test = require("node:test");
const assert = require("node:assert/strict");
const {HttpsError} = require("firebase-functions/v2/https");
const {_test: gateway} = require("../index");
const log = require("../schedule-log");
const advise = require("../schedule-advise");
const {createScheduleHandler} = require("../schedule");
const {evaluate} = require("../scripts/eval-schedule-log");
const fixtures = require("./fixtures/schedule-log.vi.json");
const clone = (v) => structuredClone(v);
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

function harness(payload = fixtures.cases[0].providerFixture, options = {}) {
  const db = new FakeFirestore(); const codes = []; const models = []; const bodies = [];
  const config = {...gateway.DEFAULT_CONFIG, scheduleEnabled: true, scheduleGuestEnabled: false,
    scheduleModel: "offline/model", ...options};
  const handler = createScheduleHandler({loadConfig: async () => config, quotaDay: () => "2026-09-29",
    resolveIdentity: async (request) => gateway.identityFromEntitlement(request.auth.uid, options.entitlement || {}, 100),
    reserveQuota: async (identity, conf, model, id) => { models.push(model); await gateway.reserveQuota(identity, conf, model, id, db); },
    completeReservation: (identity, conf, id) => gateway.completeReservation(identity, conf, id, db),
    refundReservation: (identity, model, id) => gateway.refundReservation(identity, model, id, db),
    fetch: async (_url, opts) => { bodies.push(JSON.parse(opts.body)); if (payload instanceof Error) throw payload;
      return {ok: true, json: async () => ({choices: [{message: {content: JSON.stringify(payload)}}]})}; },
    key: () => "offline-placeholder", error: (code, message) => new HttpsError(code, message), log: (code) => codes.push(code),
  });
  const request = (input = fixtures.cases[0].input) => ({auth: {uid: "parent"}, data: clone(input)});
  return {db, codes, bodies, models, handler, request};
}
for (const fixture of fixtures.cases) test(`LOG offline golden: ${fixture.id}`, async () => {
  const output = evaluate(fixture); const h = harness(fixture.providerFixture);
  const result = await h.handler(h.request(fixture.input)); assert.deepEqual(result.log, output);
  assert.equal(h.db.usage("uid_parent").requests[fixture.input.requestId].state, "complete");
});
test("LOG costs one by default, shares schedule cap/credits and carries no persisted IDs to provider", async () => {
  const h = harness(undefined, {scheduleFreeDailyParses: 1});
  const result = await h.handler(h.request()); assert.equal(h.models[0].creditCost, 1); assert.equal(h.models[0].scheduleParse, true);
  assert.equal(result.usage.remainingScheduleParses, 0); assert.equal(result.usage.remainingCredits, 9);
  assert.equal(h.bodies[0].response_format.json_schema.name, "schedule_log");
  assert.equal(h.bodies[0].provider.data_collection, "deny"); assert.equal(h.bodies[0].temperature, 0);
  const sent = JSON.parse(h.bodies[0].messages[1].content); assert.ok(!("requestId" in sent)); assert.ok(!("profileId" in sent));
  await assert.rejects(h.handler(h.request({...fixtures.cases[0].input, requestId: "second"})), /SCHEDULE_DAILY_LIMIT_REACHED/);
  assert.equal(h.bodies.length, 1);
});
test("LOG respects configured cost and early access shares the existing pool", async () => {
  const h = harness(undefined, {scheduleLogCost: 3, entitlement: {earlyAccessUntil: 1000}});
  const result = await h.handler(h.request()); assert.equal(result.usage.tier, "early"); assert.equal(result.usage.remainingCredits, 12);
  assert.equal(h.models[0].creditCost, 3);
});
test("provider failure/malformed LOG refunds credits, questions, schedule cap and global pool with safe error codes", async () => {
  for (const payload of [new Error("private input never logged"), {entries: [{bad: true}], questions: []}]) {
    const h = harness(payload, {scheduleLogCost: 3});
    await assert.rejects(h.handler(h.request()), /AI_LOG_FAILED/);
    const quota = h.db.usage("uid_parent"); assert.equal(quota.credits, 0); assert.equal(quota.questions, 0); assert.equal(quota.scheduleParses, 0);
    assert.equal(quota.requests[fixtures.cases[0].input.requestId], undefined); assert.deepEqual(h.codes, ["AI_LOG_FAILED"]);
    const global = [...h.db.rows].find(([path]) => path.startsWith("internalAiQuota/_global/days/")); assert.equal(global[1].credits, 0); assert.equal(global[1].questions, 0);
  }
});
test("invalid input and missing sign-in never reserve or call provider", async () => {
  for (const data of [{...fixtures.cases[0].input,text:"x".repeat(2001)}, {...fixtures.cases[0].input,date:"2026-02-30"},
    {...fixtures.cases[0].input,profileId:"private"}, {...fixtures.cases[0].input,plans:[{...fixtures.cases[0].input.plans[0],ref:"uuid"}]}]) {
    const h=harness();await assert.rejects(h.handler(h.request(data)), /INVALID_SCHEDULE_INPUT/); assert.equal(h.models.length,0);
  }
  const h=harness();await assert.rejects(h.handler({data:fixtures.cases[0].input}), /SIGN_IN_REQUIRED/);assert.equal(h.bodies.length,0);
});
test("LOG rejects unknown refs, mismatched category/date, invalid time/confidence, duplicates and invented starts", () => {
  const input=log.validateInput(fixtures.cases[0].input);const good=fixtures.cases[0].providerFixture;
  assert.throws(()=>log.validateLog({...good,entries:[{...good.entries[0],date:undefined}]},input));
  for (const change of [{planRef:"p999"},{date:"2026-09-27"},{category:"SLEEP"},{start:"24:00"},{confidence:1.1}]) {
    assert.throws(()=>log.validateLog({...good,entries:[{...good.entries[0],...change}]},input));
  }
  assert.throws(()=>log.validateLog({...good,entries:[good.entries[0],good.entries[0]]},input));
  for (const text of ["Làm bài mất 1 tiếng rưỡi", "Làm bài mất 1 giờ", "Làm bài trong 1h"]) {
    assert.throws(()=>log.validateLog(good,log.validateInput({...input,text})));
  }
  assert.throws(()=>log.validateLog({entries:[{...good.entries[0],name:"Học thêm"}],questions:[]},log.validateInput({...input,text:"Không đi học thêm lúc 19h"})));
});
test("ADVISE accepts only aggregate actual stats/ref aliases and new rule IDs; raw logs are rejected", () => {
  const fixture=require("./fixtures/schedule-advise.vi.json").cases[0].input;
  const input={...fixture,findings:[{ruleId:"BED_DRIFT",severity:"HIGH",days:["MON"],taskRefs:[],params:{count:3}}],
    actualStats:{recordedDays:3,bedLateDays:3,tasks:[]}};
  const parsed=advise.validateInput(input);assert.deepEqual(parsed.actualStats,input.actualStats);
  assert.throws(()=>advise.validateInput({...input,actualStats:{...input.actualStats,entries:[{id:"private"}]}}));
  assert.throws(()=>advise.validateInput({...input,actualStats:{...input.actualStats,bedLateDays:4}}));
  assert.ok(advise.RULES.includes("TASK_OVERRUN") && advise.RULES.includes("OFTEN_SKIPPED"));
});

test("LOG default cost is exposed as Remote Config's public field", () => {
  assert.equal(gateway.DEFAULT_CONFIG.scheduleLogCost, 1);
  assert.equal(gateway.publicConfig({...gateway.DEFAULT_CONFIG, scheduleLogCost: 4}, {}).ai_schedule_log_cost, 4);
});
