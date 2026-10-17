// The reference stays Claude-owned. All v2 item writes use its coercion and conflict policy.
import sync from "../../../shared/sync/sync-v2.js";

export type CollectionName =
  "tasks" | "routines" | "completions" | "sessions" | "profiles";
export type Entity = Record<string, unknown>;
export type Parsed = {
  id: string;
  entity: Entity;
  updatedAtMillis: number;
  deviceId: string;
  deleted: boolean;
};
export type V2Doc = {
  id: string;
  data: Entity & {
    updatedAtMillis: number;
    deviceId: string;
    deleted: boolean;
    schemaVersion: number;
  };
};
export const reference = sync as {
  newId: () => number;
  toDoc: (
    collection: CollectionName,
    entity: Entity,
    options: { nowMillis: number; deviceId: string; deleted?: boolean },
  ) => V2Doc | null;
  fromDoc: (
    collection: CollectionName,
    id: string,
    data: Entity,
  ) => Parsed | null;
  pickWinner: (
    collection: CollectionName,
    local: Parsed | null,
    remote: Parsed | null,
  ) => "local" | "remote";
  migrateV1: (
    snapshot: Entity,
    options: { nowMillis: number; deviceId: string },
  ) => {
    docs: Array<{ collection: CollectionName; id: string; data: Entity }>;
    settings: Entity | null;
  };
  parseSettings: (source: Entity) => Entity | null;
};

export const DAY_LABELS = ["CN", "T2", "T3", "T4", "T5", "T6", "T7"];
export const TASK_TYPES = [
  ["CUSTOM", "Tự tạo", "⭐"],
  ["HOMEWORK", "Làm bài tập", "📝"],
  ["READING", "Đọc sách", "📚"],
  ["TEST_PRACTICE", "Luyện đề", "📋"],
  ["LEARNING_GAMES", "Luyện tập", "🎓"],
  ["EXERCISE", "Vận động", "🏃"],
  ["ART", "Vẽ & sáng tạo", "🎨"],
  ["MUSIC_PRACTICE", "Tập đàn", "🎵"],
  ["CLEAN_ROOM", "Dọn phòng", "🧹"],
  ["OUTDOOR_PLAY", "Chơi ngoài trời", "🌳"],
] as const;

export function dateKey(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}
export function atMinutes(date: Date, minutes: number): number {
  return new Date(
    date.getFullYear(),
    date.getMonth(),
    date.getDate(),
    0,
    minutes,
  ).getTime();
}
export function taskOnDate(task: Entity, date: Date): boolean {
  return (
    Boolean(task.enabled) &&
    String(task.daysOfWeek ?? "")
      .split(",")
      .includes(String(date.getDay() + 1))
  );
}
export function routineOnDate(routine: Entity, date: Date): boolean {
  const iso = date.getDay() === 0 ? 7 : date.getDay();
  return (
    Boolean(routine.enabled) &&
    (Number(routine.repeatDaysMask) & (1 << (iso - 1))) !== 0
  );
}
export function timeLabel(minutes: number): string {
  return `${String(Math.floor(minutes / 60)).padStart(2, "0")}:${String(minutes % 60).padStart(2, "0")}`;
}
export function durationLabel(seconds: number): string {
  const m = Math.floor(seconds / 60);
  return `${String(m).padStart(2, "0")}:${String(seconds % 60).padStart(2, "0")}`;
}
export function monday(date: Date): Date {
  const day = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  day.setDate(day.getDate() - ((day.getDay() + 6) % 7));
  return day;
}
