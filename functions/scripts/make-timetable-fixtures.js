// Draw synthetic fixtures locally. Never uses children's photos or a network API.
const fs = require("node:fs/promises");
const path = require("node:path");
const sharp = require("sharp");
const {DAYS, validateDraft} = require("../schedule");
const output = path.join(__dirname, "../test/fixtures/timetables");
const weekdayNames = ["Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ nhật"];
const subjects = ["Chào cờ", "Toán", "Tiếng Việt", "Âm nhạc", "Mỹ thuật", "Sinh hoạt lớp"];
const block = (days, start, end) => ({days, start, end, label: "Ở trường"});
const draft = (school = [], tasks = [], questions = []) => ({anchors: {wake: {}, bed: {}, school}, tasks, questions});
const task = (name, days, start, durationMin) => ({name, taskType: "CUSTOM", emoji: "", days, start, durationMin, confidence: 1, source: "Lịch gia đình"});
const cases = [
  {id: "school-morning", sessions: [DAYS.slice(0, 5), []], hours: ["07:15–11:15", ""], expected: draft([block(DAYS.slice(0, 5), "07:15", "11:15")])},
  {id: "school-afternoon", sessions: [[], DAYS.slice(0, 5)], hours: ["", "13:00–16:30"], expected: draft([block(DAYS.slice(0, 5), "13:00", "16:30")])},
  {id: "school-two-shifts", sessions: [DAYS.slice(0, 5), DAYS.slice(0, 5)], hours: ["07:30–11:30", "13:30–16:00"], expected: draft([block(DAYS.slice(0, 5), "07:30", "11:30"), block(DAYS.slice(0, 5), "13:30", "16:00")])},
  {id: "school-alternating", sessions: [["MON", "WED", "FRI"], ["TUE", "THU"]], hours: ["07:00–11:00", "13:15–16:15"], expected: draft([block(["MON", "WED", "FRI"], "07:00", "11:00"), block(["TUE", "THU"], "13:15", "16:15")])},
  {id: "school-saturday", sessions: [["SAT"], []], hours: ["08:00–10:30", ""], expected: draft([block(["SAT"], "08:00", "10:30")])},
  {id: "school-missing-hours", sessions: [DAYS.slice(0, 5), []], hours: ["", ""], expected: draft([], [], ["Buổi sáng vào học và ra về lúc mấy giờ?"])},
  {id: "school-text-hours", sessions: [DAYS.slice(0, 5), []], hours: ["", ""], text: "Buổi sáng vào học 7h15, ra về 11h20.", expected: draft([block(DAYS.slice(0, 5), "07:15", "11:20")])},
  {id: "school-current-hours", sessions: [["TUE", "THU"], []], hours: ["", ""], currentSchool: [{days: DAYS.slice(0, 5), start: "07:20", end: "11:10"}], expected: draft([block(["TUE", "THU"], "07:20", "11:10")])},
  {id: "school-one-unknown-shift", sessions: [["MON", "WED"], ["FRI"]], hours: ["07:00–11:00", ""], expected: draft([block(["MON", "WED"], "07:00", "11:00")], [], ["Thứ 6 buổi chiều vào và ra lúc mấy giờ?"])},
  {id: "school-ambiguous-all-day", sessions: [DAYS.slice(0, 5), DAYS.slice(0, 5)], hours: ["", ""], text: "Vào trường 7h15, ra về 16h30.", expected: draft([], [], ["Con ở trường cả ngày 07:15–16:30 hay cần chia ca sáng/chiều? Giờ nghỉ trưa là mấy giờ?"])},
  {id: "family-table", family: [["Thứ 3, Thứ 5", "18:00", "60 phút", "Học tiếng Anh"], ["Thứ 7", "09:00", "45 phút", "Vẽ"]], expected: draft([], [task("Học tiếng Anh", ["TUE", "THU"], "18:00", 60), task("Vẽ", ["SAT"], "09:00", 45)])},
  {id: "family-handwritten", handwritten: true, family: [["Chủ nhật", "16:30", "30 phút", "Chơi ngoài trời"], ["Thứ 2", "18:15", "20 phút", "Đọc sách"]], expected: draft([], [task("Chơi ngoài trời", ["SUN"], "16:30", 30), task("Đọc sách", ["MON"], "18:15", 20)])},
];
function escape(value) { return value.replaceAll("&", "&amp;").replaceAll("<", "&lt;"); }
function draw(fixture) {
  const lines = [];
  const text = (x, y, value, size = 19) => lines.push(`<text x="${x}" y="${y}" font-size="${size}">${escape(value)}</text>`);
  const rect = (x, y, w, h, fill = "white") => lines.push(`<rect x="${x}" y="${y}" width="${w}" height="${h}" fill="${fill}" stroke="#94a3b8"/>`);
  text(30, 40, fixture.family ? "LỊCH GIA ĐÌNH" : "THỜI KHÓA BIỂU", 28);
  text(30, 70, "MẪU GIẢ — KHÔNG DỮ LIỆU TRẺ", 16);
  if (fixture.family) {
    const headers = ["Ngày", "Bắt đầu", "Thời lượng", "Hoạt động"];
    [headers, ...fixture.family].forEach((row, y) => row.forEach((cell, x) => {
      rect(30 + x * 275, 110 + y * 95, 275, 95, y ? "#fffdf5" : "#e0f2fe"); text(42 + x * 275, 165 + y * 95, cell, 23);
    }));
  } else {
    rect(30, 95, 1100, 50, "#e0f2fe"); text(42, 126, "Buổi / Tiết");
    weekdayNames.forEach((name, x) => text(225 + x * 129, 126, name, 18));
    fixture.sessions.forEach((days, session) => {
      for (let period = 0; period < 5; period++) {
        const y = 145 + (session * 5 + period) * 51;
        rect(30, y, 1100, 51, period % 2 ? "#f8fafc" : "white");
        text(38, y + 22, `${session ? "Chiều" : "Sáng"} Tiết ${period + 1}`, 17);
        if (period === 0 && fixture.hours[session]) text(38, y + 43, fixture.hours[session], 17);
        DAYS.forEach((day, x) => { if (days.includes(day)) text(225 + x * 129, y + 32, subjects[(period + x) % subjects.length], 15); });
      }
    });
  }
  return `<svg xmlns="http://www.w3.org/2000/svg" width="1160" height="700"><rect width="1160" height="700" fill="white"/><g fill="#0f172a" font-family="${fixture.handwritten ? "cursive" : "sans-serif"}">${lines.join("")}</g></svg>`;
}
async function main() {
  await fs.mkdir(output, {recursive: true});
  for (const fixture of cases) {
    validateDraft(fixture.expected);
    await sharp(Buffer.from(draw(fixture))).jpeg({quality: 80}).toFile(path.join(output, fixture.id + ".jpg"));
  }
  const manifest = {ageBand: "l1", today: "2026-09-28", locale: "vi-VN", cases: cases.map((fixture) => ({
    id: fixture.id, imageFile: "timetables/" + fixture.id + ".jpg", text: fixture.text || "", currentSchool: fixture.currentSchool || [], expected: fixture.expected,
  }))};
  await fs.writeFile(path.join(output, "../schedule-images.vi.json"), JSON.stringify(manifest, null, 2) + "\n");
  console.log(`Generated ${cases.length} synthetic JPEGs and expected JSON.`);
}
main().catch((error) => { console.error(error.message); process.exitCode = 1; });
