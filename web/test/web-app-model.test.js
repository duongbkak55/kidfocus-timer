const test = require("node:test");
const assert = require("node:assert/strict");
const sync = require("../../shared/sync/sync-v2.js");

const ctx = { nowMillis: 1000, deviceId: "web" };

test("web task and routine writes normalize via sync-v2 and preserve schedule encodings", () => {
  const task = sync.toDoc(
    "tasks",
    {
      id: sync.newId(() => 0.5),
      taskType: "READING",
      name: " Đọc sách ",
      emoji: "📚",
      hour: 16,
      minute: 5,
      daysOfWeek: "7,2,7",
      focusDurationMinutes: 25,
      breakDurationMinutes: 5,
      enabled: true,
      isCustom: false,
      childProfileId: "be-na",
    },
    ctx,
  );
  assert.equal(task.data.name, "Đọc sách");
  assert.equal(task.data.daysOfWeek, "2,7");
  assert.ok(task.data.id > 0 && task.data.id <= 2 ** 52);
  assert.equal(
    sync.fromDoc("tasks", task.id, task.data).entity.childProfileId,
    "be-na",
  );

  const routine = sync.toDoc(
    "routines",
    {
      id: 71,
      title: "Dọn bàn",
      deadlineMinutes: 21 * 60,
      repeatDaysMask: 127,
      reminderMinutesBefore: 15,
      enabled: true,
      linkedTimerMinutes: null,
      createdAtMillis: 1,
      childProfileId: "be-na",
    },
    ctx,
  );
  assert.equal(routine.data.repeatDaysMask, 127);
  assert.equal(
    sync.fromDoc("routines", routine.id, routine.data).entity.deadlineMinutes,
    1260,
  );
});

test("web completion uses natural key and ON_TIME beats a later LATE write", () => {
  const onTime = sync.toDoc(
    "completions",
    {
      routineId: 71,
      occurrenceDate: "2026-10-17",
      scheduledDeadlineMillis: 1000,
      completedAtMillis: 900,
      status: "ON_TIME",
    },
    ctx,
  );
  const late = sync.toDoc(
    "completions",
    {
      routineId: 71,
      occurrenceDate: "2026-10-17",
      scheduledDeadlineMillis: 1000,
      completedAtMillis: 1100,
      status: "LATE",
    },
    { ...ctx, nowMillis: 2000, deviceId: "android" },
  );
  assert.equal(onTime.id, "71_2026-10-17");
  assert.equal(
    sync.pickWinner(
      "completions",
      sync.fromDoc("completions", onTime.id, onTime.data),
      sync.fromDoc("completions", late.id, late.data),
    ),
    "local",
  );
});

test("web focus session keeps task link and early stop; tombstone wins a stale item", () => {
  const session = sync.toDoc(
    "sessions",
    {
      id: 92,
      durationSeconds: 28,
      isFocus: true,
      timestampMillis: 2000,
      childProfileId: "be-na",
      taskId: "71",
      taskTitle: "Đọc sách",
      completed: false,
    },
    ctx,
  );
  assert.equal(session.data.completed, false);
  assert.equal(session.data.taskId, "71");
  const task = sync.toDoc(
    "tasks",
    { id: 71, name: "Đọc sách", daysOfWeek: "2", childProfileId: "be-na" },
    { ...ctx, nowMillis: 1500 },
  );
  const tombstone = sync.toDoc("tasks", task.data, {
    ...ctx,
    nowMillis: 2000,
    deleted: true,
  });
  assert.equal(
    sync.pickWinner(
      "tasks",
      sync.fromDoc("tasks", tombstone.id, tombstone.data),
      sync.fromDoc("tasks", task.id, task.data),
    ),
    "local",
  );
});

test("web profile add, rename, age band and archive use the reference winner policy", () => {
  const added = sync.toDoc("profiles", {
    id: "be-bin", name: "Bé Bin", avatarEmoji: "🐻", ageBand: "5-6",
    createdAtMillis: 1000, archived: false,
  }, ctx);
  const renamed = sync.toDoc("profiles", { ...added.data, name: "Bé Bình", ageBand: "l1" },
    { ...ctx, nowMillis: 2000 });
  const archived = sync.toDoc("profiles", { ...renamed.data, archived: true },
    { ...ctx, nowMillis: 3000 });
  assert.equal(sync.fromDoc("profiles", renamed.id, renamed.data).entity.name, "Bé Bình");
  assert.equal(sync.fromDoc("profiles", renamed.id, renamed.data).entity.ageBand, "l1");
  assert.equal(sync.pickWinner("profiles", sync.fromDoc("profiles", archived.id, archived.data),
    sync.fromDoc("profiles", renamed.id, renamed.data)), "local");
  assert.equal(archived.data.archived, true);
});
