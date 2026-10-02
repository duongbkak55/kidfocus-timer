// Offline golden constraints. Mirror the relevant mathematical rules, never model prose.
const {validateAdvice} = require("./schedule-advise");
const DAYS = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"];
const mins = (t) => { const [h, m] = t.split(":").map(Number); return h * 60 + m; };
const study = new Set(["STUDY", "MORNING_STUDY", "AFTERNOON_STUDY", "HOMEWORK", "READING", "WEEKEND_STUDY", "MUSIC_PRACTICE", "LEARNING_GAMES", "TEST_PRACTICE", "CUSTOM"]);
function apply(input, advice) {
  const state = structuredClone(input);
  state.tasks = input.tasks.flatMap((t) => t.days.map((d) => ({...t, days: [d]})));
  for (const p of advice.proposals) {
    if (p.op === "SET_BED" || p.op === "SET_WAKE") p.days.forEach((d) => state.anchors[p.op === "SET_BED" ? "bed" : "wake"][d] = p.start);
    else if (p.op === "REMOVE") state.tasks = state.tasks.filter((t) => t.ref !== p.taskRef || !p.days.includes(t.days[0]));
    else for (const t of state.tasks.filter((t) => t.ref === p.taskRef && p.days.includes(t.days[0]))) {
      if (p.op === "MOVE") t.start = p.start;
      else t.durationMin = p.durationMin;
    }
  }
  return state;
}
function findings(state) {
  const found = [];
  const add = (rule, day, high = false) => found.push({rule, day, high});
  const minSleep = {"2-3": 660, "4-5": 600, l1: 540, l2: 540, l3: 540}[state.ageBand];
  const maxFocus = {"2-3": 15, "4-5": 20, l1: 30, l2: 40, l3: 40}[state.ageBand];
  const beds = {};
  for (const [i, day] of DAYS.entries()) {
    const wake = state.anchors.wake[day] ? mins(state.anchors.wake[day]) : null;
    const bedRaw = state.anchors.bed[day] ? mins(state.anchors.bed[day]) : null;
    const bed = bedRaw === null ? null : bedRaw + (bedRaw < (wake ?? 720) ? 1440 : 0); beds[day] = bed;
    const nextWake = state.anchors.wake[DAYS[(i + 1) % 7]];
    if (bed !== null && nextWake && 1440 + mins(nextWake) - bed < minSleep) add("SLEEP_SHORT", day, true);
    const intervals = [];
    for (const offset of [-1, 0, 1]) {
      const occurrence = DAYS[(i + offset + 7) % 7];
      for (const t of state.tasks.filter((t) => t.days.includes(occurrence))) {
        const start = offset * 1440 + mins(t.start); const end = start + t.durationMin;
        intervals.push({start, end, task: t});
        if (offset === 0 && t.taskType !== "TEST_PRACTICE" && t.durationMin > maxFocus) add("FOCUS_TOO_LONG", day);
        if (bed !== null && study.has(t.taskType) && start >= (wake ?? 0) && start < (nextWake ? 1440 + mins(nextWake) : 1440) && end > bed - 60) add("LATE_HOMEWORK", day);
        if (bed !== null && ["LEARNING_GAMES", "GAME_TIME", "TV_TIME"].includes(t.taskType) && start < bed && end > bed - 60) add("SCREEN_BEFORE_BED", day);
      }
      for (const b of state.anchors.school.filter((b) => b.days.includes(occurrence))) {
        const start = offset * 1440 + mins(b.start); intervals.push({start, end: start + (mins(b.end) - mins(b.start) + 1440) % 1440});
      }
    }
    const visible = intervals.filter((t) => t.start < 1440 && t.end > 0);
    visible.forEach((a, index) => visible.slice(index + 1).forEach((b) => {
      if ((a.task || b.task) && a.start < b.end && b.start < a.end && Math.max(a.start, b.start) < 1440 && Math.min(a.end, b.end) > 0) add("OVERLAP", day, true);
    }));
    if (wake !== null && bed !== null) {
      let cursor = wake; let gap = 0;
      for (const t of intervals.filter((t) => t.start < bed && t.end > wake).sort((a, b) => a.start - b.start)) { gap = Math.max(gap, t.start - cursor); cursor = Math.max(cursor, t.end); }
      if (Math.max(gap, bed - cursor) < 60) add("NO_FREE_TIME", day);
    }
  }
  const weekdays = DAYS.slice(0, 5).map((d) => beds[d]).filter((v) => v !== null);
  const weekend = DAYS.slice(5).map((d) => beds[d]).filter((v) => v !== null);
  const avg = (a) => a.reduce((s, v) => s + v, 0) / a.length;
  if (weekdays.length && weekend.length && Math.abs(avg(weekdays) - avg(weekend)) > 60) add("SOCIAL_JETLAG", "WEEK");
  return found;
}
function evaluateConstraints(input, advice, expected) {
  validateAdvice(advice, input);
  const after = apply(input, advice); const beforeFindings = findings(input); const afterFindings = findings(after);
  const count = (rows, rule, day) => rows.filter((f) => f.rule === rule && f.day === day).length;
  const noNewHigh = afterFindings.filter((f) => f.high).every((f) => count(afterFindings, f.rule, f.day) <= count(beforeFindings, f.rule, f.day));
  const fixed = expected.mustFix.every((rule) => beforeFindings.some((f) => f.rule === rule) && !afterFindings.some((f) => f.rule === rule));
  const noMedical = !/(chẩn đoán (là|bé|con)|cho (bé|con) uống|liều thuốc|melatonin|diagnosed with|take medication)/i.test([advice.summary, ...advice.proposals.map((p) => p.reason)].join(" "));
  return noNewHigh && fixed && noMedical && JSON.stringify(after.anchors.school) === JSON.stringify(input.anchors.school);
}
module.exports = {apply, findings, evaluateConstraints};
