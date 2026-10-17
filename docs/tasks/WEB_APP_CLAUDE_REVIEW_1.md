# G2 review 1 — feat/web-app (Step A–C, commits 4718a4c, 15a8ed5, 6ea5cc0)

Re-ran on the Mac: `node --test shared/learning/tests/*.test.js shared/sync/test/*.test.js` 100/100,
`npm --prefix web test` 11/11. shared/sync unchanged (git diff empty). Good: per-item transactions everywhere,
durable dirty queue in Room (`sync_records`), tombstones, explicit random ids, alarms rescheduled per item, offline
cache off by default on web, W2 adapters kept separate from the reference.
Verdict: **changes requested** — 3 must-fix (data safety), 3 small. Do not push or deploy.

## Must fix
1. **Signing into a different account uploads the previous account's data (app).** Sign-out keeps Room
   ("Dữ liệu trên máy vẫn được giữ nguyên"); on the next sign-in `upload()` → `scanLocal(uid)` (full scan) turns every
   untracked local row into a new dirty record for the NEW uid. Account A's tasks, routines, sessions and child names
   end up in account B. v1 at least replaced local data with B's snapshot; v2 merges and publishes it.
   Fix: persist `room_owner_uid`. On sign-in with local synced data:
   - owner == uid → current flow;
   - owner == null (guest data, never synced) → ask once: "Đưa dữ liệu trên máy vào tài khoản này" (merge) or
     "Chỉ dùng dữ liệu của tài khoản" (clear local synced tables + sync_records, then pull);
   - owner is another uid → clear local synced tables + that uid's sync_records BEFORE any scan, then pull. No prompt.
   Tests: A signs out, B signs in → B's cloud receives zero writes from A's rows; guest→merge and guest→discard paths.
2. **Migration race can overwrite newer edits (app + web; my spec's fault).** Both `migrateOnce` and `ensureV2`
   read meta, then blind `batch.set()` every v1 item with the v1 `updatedAtMillis`, then set meta. If client 1 finishes
   migrating and the parent edits a task, a second client that read meta before that still batch-writes the OLD
   version over the new one. Fix (update SYNC_V2_SPEC accordingly — I approve the change):
   - claim a lease first: transaction on `sync/meta` → `{state:'migrating', by, leaseUntil: now+120 s}` unless
     `schemaVersion==2` or a live lease by someone else (then wait/poll up to the lease, then re-check);
   - write migrated items through the normal `pickWinner` transaction path (never blind set), then meta = 2.
   Test: two migrators interleaved with an edit between them → edit survives.
3. **Listener storm on startup (app).** `listen()` launches, per changed document, `scanLocal(uid)` (full DB scan +
   canonical JSON of every row) + `upload()`. The first snapshot of each listener re-delivers every doc already fetched
   in `initialize()`, so N items cost O(N²) work (≈1,000 sessions → ~10⁶ canonicalisations) on every app start.
   Fix: handle a snapshot as one unit — one `mutex` section, one `scanLocal`, `receive` all changes, one `upload`;
   skip `metadata.hasPendingWrites` echoes; skip docs whose canonical form equals the stored record.
   Test: fake 2,000 sessions + 200 tasks initial sync performs one scan per snapshot (count calls).

## Small
4. Web sign-out with "Đây là máy của tôi" on: Firestore IndexedDB cache survives. On logout and when the toggle is
   turned off: `terminate(db)` + `clearIndexedDbPersistence(db)`, then reload.
5. Guest Luyện tư duy progress: keep it local, but offer an explicit one-time button in the profile screen
   "Chuyển tiến bộ trên máy này cho bé …" (parent chooses the child); never automatic.
6. `web/app/firebase-config.json`: Claude registers the Firebase web app and supplies it at deploy time — no action.

## After fixes
Report back; Claude then registers the web app config, runs G3 against the emulator and a real beta account with
Duong (two devices: Pixel + web), then asks Duong to approve deploy.
