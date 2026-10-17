// W2 formats are app-owned and intentionally outside shared/sync/sync-v2.js SPECS.
// Keep their documents and winner policy compatible with Android's DayLogCloud/Settings sync.
import reference from "../../../shared/sync/sync-v2.js";

const categories = new Set([
  "STUDY",
  "HYGIENE",
  "CHORES",
  "ENTERTAINMENT",
  "SCHOOL",
  "SLEEP",
  "WAKE",
  "ROUTINE",
  "OTHER",
]);
const sources = new Set([
  "TIMER",
  "TIMER_DONE",
  "ROUTINE",
  "MANUAL",
  "AI",
  "CHILD",
  "SKIP",
]);
const object = (value) =>
  value !== null && typeof value === "object" && !Array.isArray(value);
const integer = (value) => Number.isSafeInteger(value);
const profileId = (value) =>
  typeof value === "string" &&
  value.length > 0 &&
  value.length <= 64 &&
  !value.includes("/");
const date = (value) =>
  typeof value === "string" &&
  /^\d{4}-\d{2}-\d{2}$/.test(value) &&
  !Number.isNaN(Date.parse(value + "T00:00:00Z")) &&
  new Date(value + "T00:00:00Z").toISOString().slice(0, 10) === value;

export function toDoc(kind, entity, { nowMillis, deviceId, deleted = false }) {
  if (!object(entity) || !integer(nowMillis) || nowMillis <= 0 || !deviceId)
    return null;
  if (kind === "settings") {
    const parsed = reference.parseSettings(entity);
    if (!parsed) return null;
    return {
      id: "settings",
      data: {
        ...parsed,
        updatedAtMillis: nowMillis,
        deviceId: String(deviceId),
        schemaVersion: 2,
      },
    };
  }
  if (kind === "dayLogs") {
    const e = entity;
    if (
      typeof e.id !== "string" ||
      !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(
        e.id,
      ) ||
      !profileId(e.profile_id) ||
      !date(e.date) ||
      typeof e.name !== "string" ||
      !e.name.trim() ||
      e.name.length > 80 ||
      !categories.has(e.category) ||
      !sources.has(e.source) ||
      !integer(e.start_minute) ||
      e.start_minute < 0 ||
      e.start_minute > 1439 ||
      (e.end_minute != null &&
        (!integer(e.end_minute) || e.end_minute < 0 || e.end_minute > 1439)) ||
      (e.task_id != null && (!integer(e.task_id) || e.task_id <= 0)) ||
      (e.source === "SKIP" && (e.task_id == null || e.end_minute != null)) ||
      !integer(e.created_at) ||
      e.created_at <= 0
    )
      return null;
    return {
      id: e.id,
      data: {
        profile_id: e.profile_id,
        date: e.date,
        task_id: e.task_id ?? null,
        name: e.name,
        category: e.category,
        start_minute: e.start_minute,
        end_minute: e.end_minute ?? null,
        source: e.source,
        created_at: e.created_at,
        updated_at: Math.max(nowMillis, e.created_at),
        deleted: Boolean(deleted),
      },
    };
  }
  if (kind === "learning") {
    if (
      !profileId(entity.profileId) ||
      !object(entity.stats) ||
      !Array.isArray(entity.review) ||
      entity.review.length > 12 ||
      JSON.stringify({ stats: entity.stats, review: entity.review }).length >
        131_072
    )
      return null;
    return {
      id: `${entity.profileId}_tuduy`,
      data: {
        stats: entity.stats,
        review: entity.review,
        updatedAtMillis: nowMillis,
        deviceId: String(deviceId),
        schemaVersion: 2,
      },
    };
  }
  throw new Error(`unknown W2 collection ${kind}`);
}

export function fromDoc(kind, id, data) {
  if (!object(data)) return null;
  if (kind === "settings") {
    if (
      id !== "settings" ||
      data.schemaVersion !== 2 ||
      !integer(data.updatedAtMillis)
    )
      return null;
    const parsed = reference.parseSettings(data);
    return (
      parsed && {
        id,
        entity: parsed,
        updatedAtMillis: data.updatedAtMillis,
        deviceId: String(data.deviceId ?? ""),
        deleted: false,
      }
    );
  }
  if (kind === "dayLogs") {
    const valid = toDoc(
      kind,
      { ...data, id },
      { nowMillis: data.updated_at, deviceId: "reader", deleted: data.deleted },
    );
    if (
      !valid ||
      (data.deleted !== true && data.deleted !== false) ||
      !integer(data.updated_at) ||
      data.updated_at < data.created_at
    )
      return null;
    return {
      id,
      entity: { ...valid.data, updated_at: data.updated_at },
      updatedAtMillis: data.updated_at,
      deviceId: "",
      deleted: data.deleted,
    };
  }
  if (kind === "learning") {
    const match =
      typeof id === "string" && id.endsWith("_tuduy") ? id.slice(0, -6) : "";
    if (
      !profileId(match) ||
      data.schemaVersion !== 2 ||
      !object(data.stats) ||
      !Array.isArray(data.review) ||
      data.review.length > 12 ||
      !integer(data.updatedAtMillis) ||
      data.updatedAtMillis <= 0
    )
      return null;
    return {
      id,
      entity: { profileId: match, stats: data.stats, review: data.review },
      updatedAtMillis: data.updatedAtMillis,
      deviceId: String(data.deviceId ?? ""),
      deleted: false,
    };
  }
  throw new Error(`unknown W2 collection ${kind}`);
}

function dayLogKotlinString(row) {
  const e = row.entity;
  return `DayLogEntry(id=${row.id}, profileId=${e.profile_id}, date=${e.date}, taskId=${e.task_id ?? "null"}, name=${e.name}, category=${e.category}, startMinute=${e.start_minute}, endMinute=${e.end_minute ?? "null"}, source=${e.source}, createdAt=${e.created_at}, updatedAt=${e.updated_at}, deleted=${e.deleted})`;
}

export function pickWinner(kind, local, remote) {
  if (!local) return "remote";
  if (!remote) return "local";
  if (kind === "dayLogs") {
    if (local.updatedAtMillis !== remote.updatedAtMillis)
      return local.updatedAtMillis > remote.updatedAtMillis
        ? "local"
        : "remote";
    if (local.deleted !== remote.deleted)
      return local.deleted ? "local" : "remote";
    return dayLogKotlinString(local) > dayLogKotlinString(remote)
      ? "local"
      : "remote";
  }
  if (local.updatedAtMillis !== remote.updatedAtMillis)
    return local.updatedAtMillis > remote.updatedAtMillis ? "local" : "remote";
  return local.deviceId > remote.deviceId ? "local" : "remote";
}
