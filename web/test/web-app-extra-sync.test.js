const test = require("node:test");
const assert = require("node:assert/strict");

const adapter = import("../app/src/extra-sync.mjs");
const ctx = { nowMillis: 2000, deviceId: "web" };

test("settings write uses adapter normalization and LWW, retaining opaque schedule maps", async () => {
  const { toDoc, fromDoc, pickWinner } = await adapter;
  const local = toDoc(
    "settings",
    {
      focusDurationMinutes: 45,
      breakDurationMinutes: 7,
      dailyGoalMinutes: 90,
      soundEnabled: false,
      calmModeEnabled: true,
      allowChildExtendFocus: true,
      maxExtraFocusMinutes: 15,
      scheduleAnchors: { wake: "07:00" },
    },
    ctx,
  );
  assert.equal(local.id, "settings");
  assert.deepEqual(local.data.scheduleAnchors, { wake: "07:00" });
  assert.equal(local.data.focusDurationMinutes, 45);
  assert.equal(local.data.soundEnabled, false);
  const stale = toDoc("settings", {}, { nowMillis: 1000, deviceId: "android" });
  assert.equal(
    pickWinner(
      "settings",
      fromDoc("settings", local.id, local.data),
      fromDoc("settings", stale.id, stale.data),
    ),
    "local",
  );
  assert.equal(fromDoc("settings", "wrong", local.data), null);
});

test("dayLog write matches Android snake_case, rejects invalid time, and deletion wins equal timestamp", async () => {
  const { toDoc, fromDoc, pickWinner } = await adapter;
  const entry = {
    id: "f3171c4e-a844-4dd7-a72b-e77cab8b66c5",
    profile_id: "be-na",
    date: "2026-10-17",
    task_id: null,
    name: "Đọc sách",
    category: "STUDY",
    start_minute: 21 * 60,
    end_minute: 21 * 60 + 30,
    source: "MANUAL",
    created_at: 1000,
  };
  const live = toDoc("dayLogs", entry, ctx);
  assert.deepEqual(
    Object.keys(live.data).sort(),
    [
      "profile_id",
      "date",
      "task_id",
      "name",
      "category",
      "start_minute",
      "end_minute",
      "source",
      "created_at",
      "updated_at",
      "deleted",
    ].sort(),
  );
  assert.equal(live.data.updated_at, 2000);
  assert.equal(toDoc("dayLogs", { ...entry, start_minute: 1440 }, ctx), null);
  assert.equal(toDoc("dayLogs", { ...entry, date: "2026-02-30" }, ctx), null);
  assert.equal(toDoc("dayLogs", { ...entry, source: "SKIP" }, ctx), null);
  const tombstone = toDoc("dayLogs", entry, { ...ctx, deleted: true });
  assert.equal(
    pickWinner(
      "dayLogs",
      fromDoc("dayLogs", live.id, tombstone.data),
      fromDoc("dayLogs", live.id, live.data),
    ),
    "local",
  );
  assert.equal(
    fromDoc("dayLogs", live.id, { ...live.data, deleted: "false" }),
    null,
  );
});

test("learning write uses per-profile document and LWW, rejecting oversized review", async () => {
  const { toDoc, fromDoc, pickWinner } = await adapter;
  const entity = {
    profileId: "be-na",
    stats: { sudoku: { level: 3, played: 2 } },
    review: [{ id: 1 }],
  };
  const local = toDoc("learning", entity, ctx);
  assert.equal(local.id, "be-na_tuduy");
  assert.deepEqual(
    fromDoc("learning", local.id, local.data).entity.stats,
    entity.stats,
  );
  const stale = toDoc("learning", entity, {
    nowMillis: 1000,
    deviceId: "android",
  });
  assert.equal(
    pickWinner(
      "learning",
      fromDoc("learning", local.id, local.data),
      fromDoc("learning", stale.id, stale.data),
    ),
    "local",
  );
  assert.equal(
    toDoc("learning", { ...entity, review: Array(13).fill({}) }, ctx),
    null,
  );
  assert.equal(
    fromDoc("learning", "other_tuduy", {
      ...local.data,
      review: Array(13).fill({}),
    }),
    null,
  );
});
