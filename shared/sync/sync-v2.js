/*
 * KidFocus cloud sync v2 — reference model (Claude-owned).
 * Pure functions, no Firebase/DOM. The web app imports this file; the Android app ports it to Kotlin and must pass
 * the same fixtures (shared/sync/fixtures/*.json). See docs/tasks/SYNC_V2_SPEC.md.
 *
 * Layout: users/{uid}/sync/meta  (schemaVersion 2, migratedAtMillis)
 *         users/{uid}/{collection}/{docId} for every collection in COLLECTIONS
 * Every item doc = entity fields + updatedAtMillis + deviceId + deleted (tombstone).
 */
(function (root) {
  'use strict';
  const SCHEMA_VERSION = 2;
  const AGE_BANDS = ['2-3', '4-5', '5-6', 'l1', 'l2', 'l3'];
  const DEFAULT_PROFILE = 'default';
  const DEFAULT_AGE_BAND = '4-5';
  const STATUSES = ['PENDING', 'ON_TIME', 'LATE', 'MISSED'];
  const MAX_SAFE = Number.MAX_SAFE_INTEGER;

  const isObj = (x) => x !== null && typeof x === 'object' && !Array.isArray(x);
  const int = (v, fb) => (typeof v === 'number' && Number.isFinite(v) ? Math.trunc(v) : fb);
  const str = (v, fb) => (typeof v === 'string' ? v : fb);
  const bool = (v, fb) => (typeof v === 'boolean' ? v : fb);
  const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));
  const posId = (v) => (Number.isSafeInteger(v) && v > 0 ? v : null);

  /** New numeric ids: random positive safe integers, never sequential (Room autoGenerate collides across devices). */
  function newId(rand = Math.random) {
    // 2^52 range keeps ids exact in JS doubles and in Kotlin Long; 1e-15 collision odds per pair.
    return Math.floor(rand() * (2 ** 52)) + 1;
  }

  /* ---------- per-collection field rules (mirror the v1 Kotlin fromCloud coercions) ---------- */
  const SPECS = {
    tasks: {
      docId: (e) => String(e.id),
      parse(m) {
        const e = {
          id: posId(m.id), taskType: str(m.taskType, ''), name: str(m.name, '').trim(), emoji: str(m.emoji, '📚'),
          hour: clamp(int(m.hour, 0), 0, 23), minute: clamp(int(m.minute, 0), 0, 59),
          daysOfWeek: normalizeDays(m.daysOfWeek),
          focusDurationMinutes: clamp(int(m.focusDurationMinutes, 25), 5, 120),
          breakDurationMinutes: clamp(int(m.breakDurationMinutes, 5), 1, 30),
          enabled: bool(m.enabled, true), isCustom: bool(m.isCustom, false),
          childProfileId: str(m.childProfileId, DEFAULT_PROFILE),
        };
        return e.id && e.name ? e : null;
      },
    },
    routines: {
      docId: (e) => String(e.id),
      parse(m) {
        const linked = m.linkedTimerMinutes == null ? null : clamp(int(m.linkedTimerMinutes, 25), 5, 120);
        const e = {
          id: posId(m.id), title: str(m.title, '').trim(), emoji: str(m.emoji, '⭐'),
          deadlineMinutes: clamp(int(m.deadlineMinutes, 0), 0, 1439),
          repeatDaysMask: clamp(int(m.repeatDaysMask, 127), 1, 127), // bit (dayOfWeek ISO 1..7) - 1, Monday = bit 0
          reminderMinutesBefore: clamp(int(m.reminderMinutesBefore, 15), 0, 180),
          enabled: bool(m.enabled, true), linkedTimerMinutes: linked,
          createdAtMillis: Math.max(0, int(m.createdAtMillis, 0)),
          childProfileId: str(m.childProfileId, DEFAULT_PROFILE),
        };
        return e.id && e.title ? e : null;
      },
    },
    completions: {
      // One completion per routine occurrence; the doc id is the natural key so two devices converge.
      docId: (e) => `${e.routineId}_${e.occurrenceDate}`,
      parse(m) {
        const e = {
          routineId: posId(m.routineId), occurrenceDate: str(m.occurrenceDate, ''),
          scheduledDeadlineMillis: Math.max(0, int(m.scheduledDeadlineMillis, 0)),
          completedAtMillis: Math.max(0, int(m.completedAtMillis, 0)),
          status: STATUSES.includes(m.status) ? m.status : null,
        };
        return e.routineId && /^\d{4}-\d{2}-\d{2}$/.test(e.occurrenceDate) && e.status ? e : null;
      },
    },
    sessions: {
      docId: (e) => String(e.id),
      parse(m) {
        const e = {
          id: posId(m.id), durationSeconds: Math.max(0, int(m.durationSeconds, 0)), isFocus: bool(m.isFocus, false),
          timestampMillis: Math.max(0, int(m.timestampMillis, 0)), childProfileId: str(m.childProfileId, DEFAULT_PROFILE),
          taskId: typeof m.taskId === 'string' && m.taskId.trim() ? m.taskId : null,
          taskTitle: typeof m.taskTitle === 'string' && m.taskTitle.trim() ? m.taskTitle : null,
          completed: bool(m.completed, true),
        };
        return e.id && e.timestampMillis > 0 ? e : null;
      },
    },
    profiles: {
      docId: (e) => e.id,
      parse(m) {
        const e = {
          id: str(m.id, '').slice(0, 64), name: str(m.name, '').trim().slice(0, 40),
          avatarEmoji: [...str(m.avatarEmoji, '🐣')].slice(0, 8).join(''),
          ageBand: AGE_BANDS.includes(m.ageBand) ? m.ageBand : DEFAULT_AGE_BAND,
          createdAtMillis: Math.max(0, int(m.createdAtMillis, 0)), archived: bool(m.archived, false),
        };
        return e.id && e.name && !e.id.includes('/') ? e : null;
      },
    },
  };
  const COLLECTIONS = Object.keys(SPECS);

  function normalizeDays(v) {
    // v1 stores java.util.Calendar day ints as "2,3,4,5,6" (1 = Sunday … 7 = Saturday). Keep that encoding.
    const days = str(v, '').split(',').map((x) => parseInt(x.trim(), 10)).filter((d) => d >= 1 && d <= 7);
    return [...new Set(days)].sort((a, b) => a - b).join(',');
  }

  /** Build the Firestore document for an entity. Returns null if the entity is invalid. */
  function toDoc(collection, entity, { nowMillis, deviceId, deleted = false }) {
    const spec = SPECS[collection];
    if (!spec) throw new Error('unknown collection ' + collection);
    const e = spec.parse(entity);
    if (!e) return null;
    return { id: spec.docId(e), data: { ...e, updatedAtMillis: nowMillis, deviceId: String(deviceId), deleted: !!deleted, schemaVersion: SCHEMA_VERSION } };
  }

  /** Parse a Firestore document. Returns { id, entity, updatedAtMillis, deviceId, deleted } or null (ignore). */
  function fromDoc(collection, docId, data) {
    const spec = SPECS[collection];
    if (!spec || !isObj(data) || int(data.schemaVersion, 0) !== SCHEMA_VERSION) return null;
    const entity = spec.parse(data);
    if (!entity || spec.docId(entity) !== docId) return null; // doc id must match its key, or it is foreign/garbage
    return { id: docId, entity, updatedAtMillis: Math.max(0, int(data.updatedAtMillis, 0)), deviceId: str(data.deviceId, ''), deleted: bool(data.deleted, false) };
  }

  /**
   * Last-writer-wins per item. Ties on updatedAtMillis break on deviceId (lexicographic) so every client picks
   * the same winner. Completions use the domain policy instead (ON_TIME beats others, then earliest completion).
   * Returns 'local' | 'remote'.
   */
  function pickWinner(collection, local, remote) {
    if (!local) return 'remote';
    if (!remote) return 'local';
    if (collection === 'completions' && !local.deleted && !remote.deleted) {
      const better = betterCompletion(local.entity, remote.entity);
      if (better === 'tie') return lww(local, remote);
      return better;
    }
    return lww(local, remote);
  }
  function lww(local, remote) {
    if (remote.updatedAtMillis !== local.updatedAtMillis) return remote.updatedAtMillis > local.updatedAtMillis ? 'remote' : 'local';
    return remote.deviceId > local.deviceId ? 'remote' : 'local';
  }
  /** Mirrors RoutineCompletionPolicy.better: ON_TIME wins, then earlier completedAtMillis. */
  function betterCompletion(a, b) {
    const aOn = a.status === 'ON_TIME', bOn = b.status === 'ON_TIME';
    if (aOn !== bOn) return aOn ? 'local' : 'remote';
    if (a.completedAtMillis !== b.completedAtMillis) return a.completedAtMillis < b.completedAtMillis ? 'local' : 'remote';
    return 'tie';
  }

  /**
   * One-time migration of the v1 snapshot (users/{uid}/backups/current) into v2 item docs.
   * Returns { docs: [{collection, id, data}], settings } — the caller writes them in batches ≤ 450 writes,
   * then sets users/{uid}/sync/meta { schemaVersion: 2, migratedAtMillis }.
   * Completions referencing missing routines are dropped (same as v1 applyRemote).
   */
  function migrateV1(snapshot, { nowMillis, deviceId }) {
    if (!isObj(snapshot) || int(snapshot.schemaVersion, 0) !== 1) throw new Error('not a v1 snapshot');
    const at = Math.max(0, int(snapshot.updatedAtMillis, nowMillis));
    const map = { tasks: 'scheduledTasks', routines: 'routines', completions: 'routineCompletions', sessions: 'sessions', profiles: 'childProfiles' };
    const docs = [];
    const routineIds = new Set();
    for (const c of ['profiles', 'tasks', 'routines', 'sessions', 'completions']) {
      const list = Array.isArray(snapshot[map[c]]) ? snapshot[map[c]] : [];
      const seen = new Map();
      for (const raw of list) {
        if (!isObj(raw)) continue;
        if (c === 'completions' && !routineIds.has(raw.routineId)) continue;
        const d = toDoc(c, raw, { nowMillis: at, deviceId: 'migration-v1' });
        if (!d) continue;
        if (c === 'routines') routineIds.add(d.data.id);
        const prev = seen.get(d.id);
        if (prev && c === 'completions' && betterCompletion(prev.data, d.data) !== 'remote') continue;
        seen.set(d.id, { collection: c, id: d.id, data: d.data });
      }
      docs.push(...seen.values());
    }
    const settings = isObj(snapshot.settings) ? parseSettings(snapshot.settings) : null;
    return { docs, settings: settings && { ...settings, updatedAtMillis: at, deviceId: String(deviceId), schemaVersion: SCHEMA_VERSION } };
  }

  /** users/{uid}/sync/settings — one LWW doc (same keys as v1 settings, incl. scheduleAnchors/schedulePlans maps). */
  function parseSettings(m) {
    if (!isObj(m)) return null;
    const out = {
      focusDurationMinutes: clamp(int(m.focusDurationMinutes, 25), 5, 120),
      breakDurationMinutes: clamp(int(m.breakDurationMinutes, 5), 1, 30),
      dailyGoalMinutes: clamp(int(m.dailyGoalMinutes, 60), 30, 240),
      appTheme: str(m.appTheme, 'DEFAULT'),
      soundEnabled: bool(m.soundEnabled, true), vibrationEnabled: bool(m.vibrationEnabled, true),
      learningAgeBand: AGE_BANDS.includes(m.learningAgeBand) ? m.learningAgeBand : DEFAULT_AGE_BAND,
      calmModeEnabled: bool(m.calmModeEnabled, false), allowChildExtendFocus: bool(m.allowChildExtendFocus, false),
      maxExtraFocusMinutes: int(m.maxExtraFocusMinutes, 0),
    };
    // Opaque to the web: kept verbatim so the app's ScheduleJson/SchedulePlanJson stay the source of truth.
    if (isObj(m.scheduleAnchors)) out.scheduleAnchors = m.scheduleAnchors;
    if (isObj(m.schedulePlans)) out.schedulePlans = m.schedulePlans;
    return out;
  }

  const api = { SCHEMA_VERSION, COLLECTIONS, AGE_BANDS, newId, toDoc, fromDoc, pickWinner, betterCompletion, migrateV1, parseSettings, normalizeDays };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.KidFocusSyncV2 = api;
})(typeof window !== 'undefined' ? window : globalThis);
