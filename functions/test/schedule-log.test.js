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
test("LOG rejects malformed schema/refs but turns unsupported start times into questions", () => {
  const input=log.validateInput(fixtures.cases[0].input);const good=fixtures.cases[0].providerFixture;
  assert.throws(()=>log.validateLog({...good,entries:[{...good.entries[0],date:undefined}]},input));
  for (const change of [{planRef:"p999"},{date:"2026-09-27"},{category:"SLEEP"},{start:"24:00"},{confidence:1.1}]) {
    assert.throws(()=>log.validateLog({...good,entries:[{...good.entries[0],...change}]},input));
  }
  assert.throws(()=>log.validateLog({...good,entries:[good.entries[0],good.entries[0]]},input));
  for (const text of ["Làm bài mất 1 tiếng rưỡi", "Làm bài mất 1 giờ", "Làm bài trong 1h"]) {
    const result=log.validateLog(good,log.validateInput({...input,text}));assert.deepEqual(result.entries,[]);assert.ok(result.questions.some((q)=>q.includes("bắt đầu")));
  }
  assert.deepEqual(log.validateLog({entries:[{...good.entries[0],name:"Học thêm"}],questions:[]},log.validateInput({...input,text:"Không đi học thêm lúc 19h"})).entries,[]);
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

test("a hallucinated entry cannot borrow another activity's clock, even the identical hour; partial preview completes quota normally", async () => {
  const fixture=fixtures.cases.find((f)=>f.id==="log-vi-30");const h=harness(fixture.providerFixture);
  const result=await h.handler(h.request(fixture.input));assert.equal(result.log.entries.length,1);
  assert.equal(result.log.entries[0].category,"SLEEP");assert.ok(result.log.questions.some((q)=>q.includes("Làm bài") && q.includes("bắt đầu")));
  assert.equal(result.usage.remainingCredits,9);assert.equal(h.db.usage("uid_parent").requests[fixture.input.requestId].state,"complete");assert.deepEqual(h.codes,[]);
  const sameCategory=log.validateLog({entries:[
    {...fixture.providerFixture.entries[1],planRef:"p3",name:"Học thêm",start:"19:00",end:"20:00"},
    {...fixture.providerFixture.entries[1],planRef:null,name:"Học toán",start:"19:00",end:"20:30"}],questions:[]},
    {...fixture.input,text:"Học thêm từ 19h đến 20h, học toán mất 90 phút"});
  assert.deepEqual(sameCategory.entries.map((e)=>e.name),["Học thêm"]);assert.ok(sameCategory.questions[0].includes("Học toán"));
});
test("start derivation uses an evidenced end and explicit duration, never planned duration or another activity's duration", () => {
  const input=log.validateInput(fixtures.cases[0].input);const good=fixtures.cases[0].providerFixture.entries[0];
  const validate=(text,start,end)=>log.validateLog({entries:[{...good,start,end}],questions:[]},{...input,text});
  assert.equal(validate("Làm bài xong lúc 20h, mất 1 tiếng rưỡi","18:30","20:00").entries.length,1);
  for (const [text,start,end] of [["Làm bài xong lúc 20h","19:30","20:00"],["Làm bài từ 19h mất 90 phút","17:30","19:00"],
    ["Làm bài xong lúc 20h và đọc sách mất 90 phút","18:30","20:00"],["Làm bài mất 90 phút","19:00","20:30"],["Làm bài 1 giờ","13:00",null]]) {
    const result=validate(text,start,end);assert.deepEqual(result.entries,[]);assert.ok(result.questions.length>0);
  }
});
test("clock syntax handles Vietnamese words/quarters and English meridiem without treating counts as clocks", () => {
  const base=log.validateInput(fixtures.cases[0].input);
  const check=(text,start,category="STUDY",name="Homework")=>log.validateLog({entries:[{date:base.date,planRef:null,name,category,start,end:null,confidence:.9}],questions:[]},{...base,text,locale:"en-US"});
  for (const [text,time] of [["Did homework at 7am","07:00"],["Did homework at 7pm","19:00"],["Did homework at 7p.m.","19:00"],["Did homework at 7","19:00"],
    ["Làm bài lúc mười chín giờ mười lăm","19:15"],["Làm bài lúc 12 giờ kém 15 trưa","11:45"],
    ["Làm bài lúc 12 giờ kém 15 sáng","23:45"],["Làm bài lúc 19g15","19:15"]]) assert.equal(check(text,time).entries.length,1,text);
  assert.equal(check("Lúc 19h tắm mất 15 phút","19:00","HYGIENE","Tắm").entries.length,1);
  for (const [text,time] of [["Did homework at 7am","19:00"],["Làm 7 bài trong 90 phút","07:00"],["Làm bài mất 1 giờ","13:00"]]) {
    const result=check(text,time);assert.deepEqual(result.entries,[]);assert.ok(result.questions.every((q)=>q.includes("what time")));
  }
});
test("grounding questions are deduplicated and bounded without hiding malformed response fields", () => {
  const fixture=fixtures.cases.find((f)=>f.id==="log-vi-29");const result=log.validateLog({...fixture.providerFixture,questions:Array(30).fill("Bạn xác nhận không?")},fixture.input);
  assert.equal(result.entries.length,1);assert.equal(result.questions.length,2);assert.ok(result.questions[0].includes("Làm bài"));
  assert.throws(()=>log.validateLog({...fixture.providerFixture,entries:[...fixture.providerFixture.entries,{bad:true}]},fixture.input));
});
