// A conservative, offline check of clock evidence, not a second activity parser.
// Plans/provider times never supply evidence; only the parent's text can do that.
const WORDS = {khong: 0, mot: 1, hai: 2, ba: 3, bon: 4, tu: 4, nam: 5, lam: 5, sau: 6, bay: 7, tam: 8, chin: 9};
const N = "(?:[0-9]{1,2}|(?:(?:hai|ba|bon|nam) muoi(?: (?:mot|hai|ba|bon|tu|nam|lam|sau|bay|tam|chin))?|muoi(?: (?:mot|hai|ba|bon|tu|nam|lam|sau|bay|tam|chin))?|khong|mot|hai|ba|bon|nam|sau|bay|tam|chin)(?![a-z0-9]))";
const GROUPS = [
  ["SLEEP", "sleep", "ngu|sleep|slept|bedtime|went to bed|fell asleep"],
  ["WAKE", "wake", "thuc day|thuc|day|wake|woke|got up"],
  ["STUDY", "homework", "lam bai|bai tap|homework"],
  ["STUDY", "reading", "doc sach|read|reading"],
  ["STUDY", "tutoring", "hoc them|tutoring|tuition"],
  ["STUDY", "math", "hoc toan|math|maths"],
  ["STUDY", "study", "hoc bai|hoc|study|studied|learning"],
  ["HYGIENE", "bath", "tam|bath|bathed|shower|showered"],
  ["HYGIENE", "teeth", "danh rang|brush teeth|brushed teeth"],
  ["CHORES", "room", "don phong|clean room|cleaned room|tidied room"],
  ["CHORES", "chores", "viec nha|chores|wash dishes|washed dishes|rua bat|rua chen"],
  ["ENTERTAINMENT", "tv", "xem tv|xem tivi|watch tv|watched tv|tv"],
  ["ENTERTAINMENT", "play", "choi|play|played|game|games"],
  ["SCHOOL", "school", "di hoc|o truong|school"],
];
function normalize(s) { return s.toLowerCase().replace(/(?<!\p{L})tắm(?!\p{L})/gu, "bath").normalize("NFD").replace(/\p{M}/gu, "").replace(/đ/g, "d").replace(/([ap])\.m\.?/g, "$1m"); }
function number(s) {
  if (/^[0-9]+(?:[.,][0-9]+)?$/.test(s)) return Number(s.replace(",", "."));
  const words = s.trim().split(/\s+/); const tens = words.indexOf("muoi");
  return tens < 0 ? WORDS[s] : (tens === 0 ? 10 : WORDS[words[0]] * 10) + (WORDS[words[tens + 1]] || 0);
}
function wordPattern(words) { return new RegExp(`(?<![a-z0-9])(?:${words})(?![a-z0-9])`, "g"); }
function activityMarks(s, names) {
  const marks = GROUPS.flatMap(([, id, words]) => [...s.matchAll(wordPattern(words))].map((m) => ({index: m.index, end: m.index + m[0].length, id})));
  for (const name of names) {
    if (!name || name.length < 3) continue;
    let index = s.indexOf(name);
    while (index >= 0) { marks.push({index, end: index + name.length, id: name}); index = s.indexOf(name, index + name.length); }
  }
  // Prefer the full activity phrase over a word inside it (e.g. học toán vs học).
  marks.sort((a, b) => a.index - b.index || b.end - a.end);
  const evidence = clockEvidence(s);
  const times = [...evidence.clocks, ...evidence.durations];
  const activities = marks.filter((m) => !times.some((t) => m.index < t.end && t.index < m.end));
  return activities.filter((m, i) => !activities.slice(0, i).some((prior) => prior.index <= m.index && prior.end >= m.end));
}
function clauses(s, names) {
  const result = [];
  const parts = s.split(/[,;\n]|(?<![0-9])\.(?![0-9])|\b(?:va|and)\b/);
  for (const part of parts) {
    const marks = activityMarks(part, names);
    let start = 0; let previous = marks[0];
    for (const mark of marks.slice(1)) {
      if (mark.id !== previous.id && mark.index >= previous.end) { result.push(part.slice(start, mark.index)); start = mark.index; }
      previous = mark;
    }
    const tail = part.slice(start);
    if (!marks.length && result.length) result[result.length - 1] += `, ${tail}`;
    else if (tail.trim()) result.push(tail);
  }
  return result;
}
function durations(s) {
  const values = [];
  const amount = `(?:[0-9]+(?:[.,][0-9]+)?|${N})`;
  const pattern = new RegExp(`(?<![a-z0-9])(${amount})\\s*(tieng|gio|hours?|hrs?|h|phut|minutes?|mins?)(?![a-z])(?:\\s+(ruoi))?(?:\\s*(?:va|and)?\\s*(${N})\\s*(?:phut|minutes?|mins?))?`, "g");
  for (const m of s.matchAll(pattern)) {
    const prefix = s.slice(0, m.index);
    const hour = /^(?:tieng|gio|hours?|hrs?|h)$/.test(m[2]);
    // "1 giờ/h" can be a clock; a duration marker must distinguish it.
    if (/^(?:gio|h)$/.test(m[2]) && !/\b(?:mat|trong|suot|keo dai|for|took|lasted|spent|after)\s*$/.test(prefix)) continue;
    const minutes = number(m[1]) * (hour ? 60 : 1) + (m[3] && hour ? 30 : 0) + (m[4] ? number(m[4]) : 0);
    if (Number.isInteger(minutes) && minutes > 0 && minutes <= 1440) values.push({index: m.index, end: m.index + m[0].length, minutes});
  }
  return values;
}
function clockEvidence(s) {
  const spans = durations(s); const clocks = [];
  function add(m, hour, minute, offset, suffix, adjust = 0) {
    const index = m.index + offset; const end = m.index + m[0].length;
    if (!Number.isInteger(hour) || hour < 0 || hour > 23 || !Number.isInteger(minute) || minute < 0 || minute > 59 ||
        spans.some((d) => index < d.end && d.index < end) || clocks.some((c) => index < c.end && c.index < end)) return;
    let period = suffix;
    const nearby = s.slice(index, end + 18);
    if (!period) period = /\b(?:am|sang|morning)\b/.test(nearby) ? "am" : /\b(?:pm|toi|chieu|dem|afternoon|evening|night)\b/.test(nearby) ? "pm" : null;
    if (!period) {
      const am = /\b(?:sang|morning)\b/.test(s); const pm = /\b(?:toi|chieu|dem|trua|afternoon|evening|night)\b/.test(s);
      if (am !== pm) period = am ? "am" : "pm";
    }
    const base = hour * 60 + minute;
    const values = hour > 12 || hour === 0 ? [base] : period === "am" ? [(hour % 12) * 60 + minute] :
      period === "pm" ? [(hour % 12 + 12) * 60 + minute] : [base, ((hour + 12) % 24) * 60 + minute];
    const before = s.slice(0, index);
    const isEnd = /\b(?:xong|ket thuc|finished|ended|done)\b[^,;]{0,50}$/.test(before) ||
      /\b(?:den|until|to)\s*$/.test(before) || /[-–]\s*$/.test(before) || /^\s*(?:xong|finished|ended)\b/.test(s.slice(end));
    clocks.push({index, end, values: values.map((v) => (v + adjust + 1440) % 1440), isEnd});
  }
  // Specific fractions first so "7 giờ kém 15" cannot also become "07:00".
  const less = new RegExp(`(?<![a-z0-9])(${N})\\s*(?:(?:gio|h|g)\\s*)?kem\\s*(${N})(?:\\s*phut)?`, "g");
  for (const m of s.matchAll(less)) {
    const h = number(m[1]); const lessMin = number(m[2]);
    if (h <= 23 && lessMin > 0 && lessMin < 60) add(m, h, 0, 0, null, -lessMin);
  }
  const half = new RegExp(`(?<![a-z0-9])(${N})\\s*(?:(?:gio|h|g)\\s*)?ruoi`, "g");
  for (const m of s.matchAll(half)) add(m, number(m[1]), 30, 0);
  const unit = new RegExp(`(?<![a-z0-9])(${N})\\s*(?:(?:gio|h|g)(?![a-z])|:)\\s*(${N})?(?:\\s*phut)?\\s*(am|pm)?`, "g");
  for (const m of s.matchAll(unit)) {
    // A bare "1 giờ" can be duration, so require clock context for this ambiguous unit.
    if (/\bgio\b/.test(m[0]) && !/\b(?:luc|tu|den|at|from|until|to|xong)\s*$/.test(s.slice(0, m.index)) &&
        !/\b(?:sang|toi|chieu|dem|trua|am|pm|morning|afternoon|evening|night)\b/.test(s)) continue;
    add(m, number(m[1]), m[2] ? number(m[2]) : 0, 0, m[3]);
  }
  const meridiem = new RegExp(`(?<![a-z0-9])(${N})\\s*(am|pm)(?![a-z])`, "g");
  for (const m of s.matchAll(meridiem)) add(m, number(m[1]), 0, 0, m[2]);
  const bare = new RegExp(`\\b(?:luc|tu|den|at|from|until|to|around)\\s+(${N})(?![a-z0-9])`, "g");
  for (const m of s.matchAll(bare)) add(m, number(m[1]), 0, m[0].lastIndexOf(m[1]));
  return {clocks, durations: spans};
}
function minute(s) { const [h, m] = s.split(":").map(Number); return h * 60 + m; }
function grounding(input, entries) {
  const names = entries.flatMap((e) => [normalize(e.name), normalize(input.plans.find((p) => p.ref === e.planRef)?.name || "")]);
  const parts = clauses(normalize(input.text), names);
  return (entry) => {
    const name = normalize(entry.name); const planName = normalize(input.plans.find((p) => p.ref === entry.planRef)?.name || "");
    const identify = (label) => {
      const found = GROUPS.filter(([category, , words]) => category === entry.category && wordPattern(words).test(label));
      const specific = found.filter(([, id]) => id !== "study");
      return specific.length ? specific : found;
    };
    const groups = identify(name); const byPlan = identify(planName);
    const related = parts.filter((s) => groups.length ? groups.some(([, , words]) => wordPattern(words).test(s)) :
      s.includes(name) || planName && s.includes(planName) || byPlan.some(([, , words]) => wordPattern(words).test(s)));
    if (related.some((s) => /\bkhong\s+(?:di\s+)?hoc them\b/.test(s) && /\b(?:hoc them|tutoring|tuition)\b/.test(name + " " + planName))) return "not_done";
    const start = minute(entry.start);
    for (const s of related) {
      const evidence = clockEvidence(s);
      if (evidence.clocks.some((c) => !c.isEnd && c.values.includes(start))) return null;
      if (evidence.clocks.some((c) => c.isEnd && c.values.some((end) =>
        (entry.end === null || minute(entry.end) === end) && evidence.durations.some((d) => (end - d.minutes + 1440) % 1440 === start)))) return null;
    }
    return "start_missing";
  };
}
module.exports = {grounding};
