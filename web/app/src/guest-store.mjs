import reference from "../../../shared/sync/sync-v2.js";
import * as extraSync from "./extra-sync.mjs";

export const GUEST_DATA_KEY = "kidfocus-web-guest-data-v1";
export const GUEST_MODE_KEY = "kidfocus-web-guest-mode";
const core = ["profiles", "tasks", "routines", "completions", "sessions"];
const extras = ["dayLogs", "learning"];
const names = [...core, ...extras];
const empty = () => Object.fromEntries(names.map((name) => [name, {}]));
const isRecord = (value) => value !== null && typeof value === "object" && !Array.isArray(value);

function readRecords(storage) {
  const raw = storage.getItem(GUEST_DATA_KEY);
  if (!raw) return { fresh: true, docs: empty(), settings: null };
  let value;
  try { value = JSON.parse(raw); } catch { throw new Error("Dữ liệu khách trên máy bị lỗi; chưa ghi đè dữ liệu cũ."); }
  if (value?.version !== 1 || !isRecord(value.docs) ||
      names.some((name) => !isRecord(value.docs[name])) ||
      (value.settings !== null && !isRecord(value.settings)))
    throw new Error("Dữ liệu khách trên máy bị lỗi; chưa ghi đè dữ liệu cũ.");
  const docs = empty();
  for (const name of names) {
    for (const [id, data] of Object.entries(value.docs[name])) {
      const parsed = core.includes(name)
        ? reference.fromDoc(name, id, data)
        : extraSync.fromDoc(name, id, data);
      if (!parsed) throw new Error("Dữ liệu khách trên máy bị lỗi; chưa ghi đè dữ liệu cũ.");
      docs[name][id] = data;
    }
  }
  if (value.settings && !extraSync.fromDoc("settings", "settings", value.settings))
    throw new Error("Dữ liệu khách trên máy bị lỗi; chưa ghi đè dữ liệu cũ.");
  return { fresh: false, docs, settings: value.settings };
}

export class GuestStore {
  constructor(storage, now = () => Date.now()) {
    this.storage = storage;
    this.now = now;
    this.subscriber = null;
    const { fresh, docs, settings } = readRecords(storage);
    this.records = { version: 1, docs, settings };
    if (fresh) {
      const profile = reference.toDoc("profiles", {
        id: "guest-child", name: "Bé của tôi", avatarEmoji: "🐣",
        ageBand: "5-6", createdAtMillis: now(), archived: false,
      }, { nowMillis: now(), deviceId: "guest" });
      if (!profile) throw new Error("Không thể tạo hồ sơ khách.");
      this.persist({ ...this.records, docs: { ...docs, profiles: { [profile.id]: profile.data } } });
    }
  }
  state() {
    const rows = (name) => Object.entries(this.records.docs[name])
      .map(([id, data]) => core.includes(name)
        ? reference.fromDoc(name, id, data)
        : extraSync.fromDoc(name, id, data))
      .filter((row) => row && !row.deleted);
    return {
      ...Object.fromEntries(names.map((name) => [name, rows(name)])),
      settings: this.records.settings
        ? extraSync.fromDoc("settings", "settings", this.records.settings)?.entity ?? null
        : null,
      warning: false, sync: "Chỉ trên máy", error: "",
    };
  }
  emit() { this.subscriber?.(this.state()); }
  start(subscriber) { this.subscriber = subscriber; this.emit(); }
  stop() { this.subscriber = null; }
  async retry() { this.emit(); }
  async reimport() { /* Guest data never imports a cloud backup. */ }
  async importGuestLearning() { throw new Error("Hãy đăng nhập để chuyển tiến bộ lên tài khoản."); }
  persist(records) {
    this.storage.setItem(GUEST_DATA_KEY, JSON.stringify(records));
    this.records = records;
    this.emit();
  }
  async write(name, entity, deleted = false) {
    if (!core.includes(name)) throw new Error("Loại dữ liệu khách không hợp lệ.");
    const candidate = reference.toDoc(name, entity, {
      nowMillis: this.now(), deviceId: "guest", deleted,
    });
    if (!candidate) throw new Error("Dữ liệu không hợp lệ. Vui lòng kiểm tra tên và thời gian.");
    this.persist({ ...this.records, docs: {
      ...this.records.docs,
      [name]: { ...this.records.docs[name], [candidate.id]: candidate.data },
    } });
  }
  async writeExtra(name, entity, deleted = false) {
    const candidate = extraSync.toDoc(name, entity, {
      nowMillis: this.now(), deviceId: "guest", deleted,
    });
    if (!candidate) throw new Error("Dữ liệu không hợp lệ. Kiểm tra tên, ngày và thời gian.");
    if (name === "settings") {
      this.persist({ ...this.records, settings: candidate.data });
      return;
    }
    if (!extras.includes(name)) throw new Error("Loại dữ liệu khách không hợp lệ.");
    this.persist({ ...this.records, docs: {
      ...this.records.docs,
      [name]: { ...this.records.docs[name], [candidate.id]: candidate.data },
    } });
  }
}
