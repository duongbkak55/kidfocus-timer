# Sync v2 — spec (Claude) for KidFocus app ↔ web

Why: v1 keeps everything in ONE doc `users/{uid}/backups/current`, the writer replaces it whole and the reader deletes
and reinserts every Room row. Two writers (phone + web) silently erase each other's edits; the doc also grows with every
focus session toward Firestore's 1 MB limit; Room autoGenerate ids collide when two clients create items.

## Layout (all under the existing rule `users/{uid}/**` = owner only)
| path | content |
|---|---|
| `users/{uid}/sync/meta` | `{schemaVersion: 2, migratedAtMillis, migratedBy}` |
| `users/{uid}/sync/settings` | one LWW doc: v1 settings keys + `scheduleAnchors`, `schedulePlans` (opaque maps, app owns their format) + `updatedAtMillis`, `deviceId`, `schemaVersion` |
| `users/{uid}/tasks/{id}` | ScheduledTask |
| `users/{uid}/routines/{id}` | Routine |
| `users/{uid}/completions/{routineId}_{yyyy-mm-dd}` | RoutineCompletion (natural key; no numeric id) |
| `users/{uid}/sessions/{id}` | focus/break Session |
| `users/{uid}/profiles/{id}` | ChildProfile |
| `users/{uid}/dayLogs/{id}` | unchanged (already per-doc, snake_case, `updated_at` + `deleted`) |
| `users/{uid}/learning/{profileId}_tuduy` | W2: Luyện tư duy store `{stats, review}` + LWW fields |

Every item doc = entity fields exactly as `shared/sync/sync-v2.js` SPECS + `updatedAtMillis`, `deviceId`, `deleted`,
`schemaVersion: 2`. Deletes are tombstones (`deleted: true`), never `delete()` — so an offline device cannot resurrect
a deleted item by re-uploading. Tombstones older than 90 days may be purged by the client that sees them.

## Rules of the model (reference: shared/sync/sync-v2.js, fixtures in shared/sync/fixtures)
- Field coercion = v1 Kotlin `*FromCloud` rules (clamps, defaults, invalid rows dropped). `daysOfWeek` keeps the
  java.util.Calendar encoding (1 = Sunday … 7 = Saturday), normalised to sorted unique `"2,3,4"`. `repeatDaysMask` bit 0 = Monday.
- Conflict: per item last-writer-wins on `updatedAtMillis`, tie → larger `deviceId`. Completions: domain policy first
  (ON_TIME beats others, then earlier `completedAtMillis`), LWW only for tombstones / exact ties.
- New numeric ids: `newId()` = random integer in [1, 2^52]. The app must stop relying on Room autoGenerate for rows that
  sync (insert with explicit generated id). Existing ids stay.
- `updatedAtMillis` is the writer's clock. Writes go through a Firestore transaction: read the doc, `pickWinner`, write only
  if local wins. Never blind `set()`.
- Readers listen per collection; sessions are queried with `timestampMillis >= now - 400 days` (history pages older on demand).

## Migration (once per account, whichever v2 client logs in first)
1. In a transaction read `sync/meta`. If `schemaVersion == 2` → done.
2. Read `backups/current`; `migrateV1()` → batched writes (≤ 450 per batch), then `sync/meta` = 2.
3. v2 clients never write `backups/current` again. If its `updatedAtMillis > migratedAtMillis` (an old app version
   wrote later), show "Một máy đang dùng bản app cũ — hãy cập nhật để đồng bộ" and offer a one-tap re-import of that
   snapshot through `pickWinner` (never a blind overwrite).
4. Old app versions ignore v2 (they only read `backups/current`): acceptable while the app is in closed beta.

## Acceptance (both platforms)
- JS: `node --test shared/sync/test/*.test.js` (Claude's reference tests) stays green.
- Kotlin: a unit test reads the SAME fixture files (copy them into app test resources at build time, do not fork) and
  asserts the same migration output and the same winners as `conflicts.json`.
