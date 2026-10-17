const test = require("node:test");
const assert = require("node:assert/strict");
const sync = require("../../shared/sync/sync-v2.js");

test("lease allows only one live migrator and an interleaved edit survives an expired lease", async () => {
  const lease = await import("../app/src/migration-lease.mjs");
  let meta = null;
  assert.equal(lease.decide(meta, 1000, "phone").kind, "claim");
  meta = lease.claim(1000, "phone");
  assert.equal(lease.decide(meta, 2000, "web").kind, "wait");
  assert.equal(lease.decide(meta, 121001, "web").kind, "claim");
  const old = sync.toDoc("tasks", { id: 7, name: "Old", daysOfWeek: "2" },
    { nowMillis: 1000, deviceId: "migration" });
  const edit = sync.toDoc("tasks", { id: 7, name: "Edited", daysOfWeek: "2" },
    { nowMillis: 2000, deviceId: "parent" });
  let current = old;
  current = edit;
  for (const candidate of [old, old]) {
    if (sync.pickWinner("tasks", sync.fromDoc("tasks", candidate.id, candidate.data),
      sync.fromDoc("tasks", current.id, current.data)) === "local") current = candidate;
  }
  assert.equal(current.data.name, "Edited");
});

test("guest progress is explicit, one-time and cannot target an existing cloud record", async () => {
  const guest = await import("../app/src/guest-learning.mjs");
  const values = new Map([[guest.GUEST_PROGRESS_KEY, JSON.stringify({ stats: { sequence: 2 }, review: [] })]]);
  const storage = { getItem: key => values.get(key) ?? null };
  assert.deepEqual(guest.readGuestProgress(storage), { stats: { sequence: 2 }, review: [] });
  assert.equal(guest.canImport("na", [{ id: "na_tuduy", deleted: false }]), false);
  assert.equal(guest.canImport("na", [{ id: "bin_tuduy", deleted: false }]), true);
  values.set(guest.IMPORTED_KEY, "yes");
  assert.equal(guest.readGuestProgress(storage), null);
});
