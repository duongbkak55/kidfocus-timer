import { useEffect, useState } from "preact/hooks";
import type { AppStore, DataState } from "./cloud";
import { dateKey, reference, timeLabel, type Entity } from "./model";
import * as guestLearning from "./guest-learning.mjs";

const ageBands = [
  ["2-3", "2–3 tuổi"],
  ["4-5", "4–5 tuổi"],
  ["5-6", "5–6 tuổi"],
  ["l1", "Lớp 1"],
  ["l2", "Lớp 2"],
  ["l3", "Lớp 3"],
];
const categories = [
  ["STUDY", "Học tập"],
  ["HYGIENE", "Vệ sinh"],
  ["CHORES", "Việc nhà"],
  ["ENTERTAINMENT", "Giải trí"],
  ["SCHOOL", "Ở trường"],
  ["SLEEP", "Ngủ"],
  ["WAKE", "Thức dậy"],
  ["ROUTINE", "Sinh hoạt"],
  ["OTHER", "Khác"],
];
const str = (v: unknown) => (typeof v === "string" ? v : String(v ?? ""));
const num = (v: unknown) => Number(v ?? 0);
const sourceLabels: Record<string, string> = { MANUAL: "Ghi tay", AI: "AI", CHILD: "Bé ghi",
  TIMER: "Đồng hồ", TIMER_DONE: "Đồng hồ", ROUTINE: "Việc hằng ngày", SKIP: "Bỏ qua" };
const minutes = (value: string) => {
  const [h, m] = value.split(":").map(Number);
  return h * 60 + m;
};

export function DayLogs({
  data,
  profileId,
  store,
  onNotice,
}: {
  data: DataState;
  profileId: string;
  store: AppStore | null;
  onNotice: (message: string) => void;
}) {
  const [day, setDay] = useState(dateKey(new Date()));
  const [draft, setDraft] = useState<Entity | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const rows = data.dayLogs
    .filter(
      (row) => row.entity.profile_id === profileId && row.entity.date === day,
    )
    .sort((a, b) => num(a.entity.start_minute) - num(b.entity.start_minute));
  const update = (key: string, value: unknown) =>
    setDraft((old) => old && { ...old, [key]: value });
  function fresh() {
    const now = new Date();
    setDraft({
      id: crypto.randomUUID(),
      profile_id: profileId,
      date: day,
      task_id: null,
      name: "",
      category: "OTHER",
      start_minute: now.getHours() * 60 + now.getMinutes(),
      end_minute: null,
      source: "MANUAL",
      created_at: Date.now(),
      updated_at: Date.now(),
      deleted: false,
    });
    setConfirmDelete(false);
  }
  async function save(deleted = false) {
    if (!store || !draft) return;
    try {
      await store.writeExtra("dayLogs", draft, deleted);
      onNotice(deleted ? "Đã xoá ghi chép." : "Đã lưu nhật ký.");
      setDraft(null);
    } catch (error) {
      onNotice(error instanceof Error ? error.message : String(error));
    }
  }
  return (
    <section class="page">
      <div class="eyebrow">NHẬT KÝ NGÀY</div>
      <h1>Thực tế của con</h1>
      <p class="muted">Ghi lại hoạt động đã diễn ra, kể cả ngoài lịch.</p>
      <div class="actions">
        <label>
          Ngày
          <input
            type="date"
            value={day}
            onChange={(event) => {
              setDay(event.currentTarget.value);
              setDraft(null);
            }}
          />
        </label>
        <button class="primary" onClick={fresh}>
          + Ghi hoạt động
        </button>
      </div>
      <div class="daylog-layout">
      <aside class="daylog-chart card"><h2>Thời gian đã ghi</h2>
        <strong>{rows.reduce((sum, row) => row.entity.end_minute == null ? sum : sum +
          ((num(row.entity.end_minute) - num(row.entity.start_minute) + 1440) % 1440), 0)} phút</strong>
        <p>{rows.length} hoạt động trong ngày</p>
        {categories.map(([id, title]) => {
          const count = rows.filter(row => row.entity.category === id).length;
          return count ? <p class="category-bar"><span>{title}</span><meter min="0" max={Math.max(rows.length, 1)} value={count} /> <b>{count}</b></p> : null;
        })}
      </aside><div class="daylog-detail">
      {rows.length ? (
        <div class="history-list">
          {rows.map((row) => (
            <button
              class="history-row edit-row daylog-row"
              key={row.id}
              onClick={() => {
                setDraft({ id: row.id, ...row.entity });
                setConfirmDelete(false);
              }}
            >
              <span class="history-icon">✎</span>
              <span>
                <strong>
                  {timeLabel(num(row.entity.start_minute))} ·{" "}
                  {str(row.entity.name)}
                </strong>
                <small>
                  {row.entity.end_minute == null
                    ? "Chưa có giờ kết thúc"
                    : `Đến ${timeLabel(num(row.entity.end_minute))}`}{" "}
                  ·{" "}
                  {categories.find(([id]) => id === row.entity.category)?.[1] ??
                    "Khác"}
                </small>
              </span>
              <span class="wide-only">{timeLabel(num(row.entity.start_minute))} – {row.entity.end_minute == null ? "…" : timeLabel(num(row.entity.end_minute))}</span>
              <span class="wide-only">{sourceLabels[str(row.entity.source)] ?? "Khác"}</span>
            </button>
          ))}
        </div>
      ) : (
        <div class="empty card">Chưa có ghi chép trong ngày này.</div>
      )}</div></div>
      {draft && (
        <div class="modal-backdrop" onClick={() => setDraft(null)}>
          <section
            class="modal"
            role="dialog"
            aria-modal="true"
            aria-label="Ghi hoạt động"
            onClick={(event) => event.stopPropagation()}
          >
            <div class="modal-top">
              <h2>Ghi hoạt động</h2>
              <button aria-label="Đóng" onClick={() => setDraft(null)}>
                ×
              </button>
            </div>
            <form
              onSubmit={(event) => {
                event.preventDefault();
                save();
              }}
            >
              <label>
                Tên hoạt động
                <input
                  required
                  maxLength={80}
                  value={str(draft.name)}
                  onInput={(event) => update("name", event.currentTarget.value)}
                />
              </label>
              <label>
                Nhóm hoạt động
                <select
                  value={str(draft.category)}
                  onChange={(event) =>
                    update("category", event.currentTarget.value)
                  }
                >
                  {categories.map(([id, label]) => (
                    <option value={id}>{label}</option>
                  ))}
                </select>
              </label>
              <div class="form-row">
                <label>
                  Bắt đầu
                  <input
                    type="time"
                    required
                    value={timeLabel(num(draft.start_minute))}
                    onInput={(event) =>
                      update("start_minute", minutes(event.currentTarget.value))
                    }
                  />
                </label>
                <label>
                  Kết thúc (nếu có)
                  <input
                    type="time"
                    value={
                      draft.end_minute == null
                        ? ""
                        : timeLabel(num(draft.end_minute))
                    }
                    onInput={(event) =>
                      update(
                        "end_minute",
                        event.currentTarget.value
                          ? minutes(event.currentTarget.value)
                          : null,
                      )
                    }
                  />
                </label>
              </div>
              <label>
                Gắn với hoạt động trong lịch
                <select
                  value={str(draft.task_id)}
                  onChange={(event) =>
                    update(
                      "task_id",
                      event.currentTarget.value
                        ? Number(event.currentTarget.value)
                        : null,
                    )
                  }
                >
                  <option value="">Không gắn</option>
                  {data.tasks
                    .filter((row) => row.entity.childProfileId === profileId)
                    .map((row) => (
                      <option value={str(row.entity.id)}>
                        {str(row.entity.name)}
                      </option>
                    ))}
                </select>
              </label>
              <div class="actions">
                <button class="primary">Lưu nhật ký</button>
                <button type="button" onClick={() => setDraft(null)}>
                  Huỷ
                </button>
              </div>
            </form>
            <button
              class="text-button danger-text"
              onClick={() =>
                confirmDelete ? save(true) : setConfirmDelete(true)
              }
            >
              {confirmDelete ? "Xác nhận xoá ghi chép" : "Xoá ghi chép"}
            </button>
          </section>
        </div>
      )}
    </section>
  );
}

export function Profiles({
  data,
  profileId,
  store,
  guestMode,
  onSelect,
  onNotice,
}: {
  data: DataState;
  profileId: string;
  store: AppStore | null;
  guestMode: boolean;
  onSelect: (id: string) => void;
  onNotice: (message: string) => void;
}) {
  const [draft, setDraft] = useState<Entity | null>(null);
  const [confirmArchive, setConfirmArchive] = useState(false);
  const [importChildId, setImportChildId] = useState(profileId);
  const [importConfirm, setImportConfirm] = useState(false);
  const [guestProgress, setGuestProgress] = useState(() => guestLearning.readGuestProgress(localStorage));
  const rows = data.profiles.filter((row) => !row.entity.archived);
  const update = (key: string, value: unknown) =>
    setDraft((old) => old && { ...old, [key]: value });
  async function save(archive = false) {
    if (!store || !draft) return;
    try {
      await store.write("profiles", {
        ...draft,
        archived: archive || Boolean(draft.archived),
      });
      onNotice(archive ? "Đã lưu trữ hồ sơ." : "Đã lưu hồ sơ bé.");
      setDraft(null);
      if (archive && draft.id === profileId) onSelect("default");
    } catch (error) {
      onNotice(error instanceof Error ? error.message : String(error));
    }
  }
  return (
    <section class="page">
      <div class="eyebrow">HỒ SƠ BÉ</div>
      <h1>Mỗi bé một nhịp riêng</h1>
      <p class="muted">
        Ảnh cá nhân chỉ lưu trên điện thoại; web dùng biểu tượng.
      </p>
      <div class="history-list">
        {rows.map((row) => (
          <button
            class="history-row edit-row"
            key={row.id}
            onClick={() => {
              setDraft({ ...row.entity });
              setConfirmArchive(false);
            }}
          >
            <span class="emoji">{str(row.entity.avatarEmoji)}</span>
            <span>
              <strong>{str(row.entity.name)}</strong>
              <small>
                {ageBands.find(([id]) => id === row.entity.ageBand)?.[1]}
              </small>
            </span>
          </button>
        ))}
      </div>
      <button
        class="primary full"
        onClick={() => {
          setDraft({
            id: crypto.randomUUID(),
            name: "",
            avatarEmoji: "🐣",
            ageBand: "4-5",
            createdAtMillis: Date.now(),
            archived: false,
          });
          setConfirmArchive(false);
        }}
      >
        + Thêm hồ sơ
      </button>
      {!guestMode && guestProgress && rows.length > 0 && (
        <section class="card" aria-label="Chuyển tiến bộ chơi thử">
          <h2>Tiến bộ Luyện tư duy trên máy này</h2>
          <p class="muted">Chọn đúng bé để chuyển một lần. Tiến bộ đã đồng bộ của bé sẽ không bị ghi đè.</p>
          <label>Chuyển cho bé
            <select value={importChildId} onChange={event => { setImportChildId(event.currentTarget.value); setImportConfirm(false); }}>
              {rows.map(row => <option value={row.id}>{str(row.entity.name)}</option>)}
            </select>
          </label>
          <button disabled={!store || !guestLearning.canImport(importChildId, data.learning)}
            onClick={async () => {
              if (!store || !guestProgress) return;
              if (!importConfirm) { setImportConfirm(true); return; }
              try {
                await store.importGuestLearning({ profileId: importChildId, ...guestProgress });
                localStorage.setItem(guestLearning.IMPORTED_KEY, "yes");
                setGuestProgress(null);
                onNotice("Đã chuyển tiến bộ chơi thử cho bé.");
              } catch (error) { onNotice(error instanceof Error ? error.message : String(error)); }
            }}>
            {importConfirm ? "Xác nhận chuyển cho bé đã chọn" : `Chuyển tiến bộ trên máy này cho bé ${str(rows.find(row => row.id === importChildId)?.entity.name)}`}
          </button>
          {!guestLearning.canImport(importChildId, data.learning) && <p class="muted">Bé đã có tiến bộ đồng bộ; không ghi đè dữ liệu đó.</p>}
        </section>
      )}
      {draft && (
        <div class="modal-backdrop" onClick={() => setDraft(null)}>
          <section
            class="modal"
            role="dialog"
            aria-modal="true"
            aria-label="Hồ sơ bé"
            onClick={(event) => event.stopPropagation()}
          >
            <div class="modal-top">
              <h2>Hồ sơ bé</h2>
              <button aria-label="Đóng" onClick={() => setDraft(null)}>
                ×
              </button>
            </div>
            <form
              onSubmit={(event) => {
                event.preventDefault();
                save();
              }}
            >
              <label>
                Tên bé
                <input
                  required
                  maxLength={40}
                  value={str(draft.name)}
                  onInput={(event) => update("name", event.currentTarget.value)}
                />
              </label>
              <label>
                Biểu tượng
                <input
                  maxLength={8}
                  value={str(draft.avatarEmoji)}
                  onInput={(event) =>
                    update("avatarEmoji", event.currentTarget.value)
                  }
                />
              </label>
              <label>
                Độ tuổi
                <select
                  value={str(draft.ageBand)}
                  onChange={(event) =>
                    update("ageBand", event.currentTarget.value)
                  }
                >
                  {ageBands.map(([id, label]) => (
                    <option value={id}>{label}</option>
                  ))}
                </select>
              </label>
              <div class="actions">
                <button class="primary">Lưu hồ sơ</button>
                <button type="button" onClick={() => setDraft(null)}>
                  Huỷ
                </button>
              </div>
            </form>
            <button
              class="text-button danger-text"
              onClick={() =>
                confirmArchive ? save(true) : setConfirmArchive(true)
              }
            >
              {confirmArchive ? "Xác nhận lưu trữ hồ sơ" : "Lưu trữ hồ sơ"}
            </button>
          </section>
        </div>
      )}
    </section>
  );
}

export function Settings({
  data,
  store,
  onNotice,
}: {
  data: DataState;
  store: AppStore | null;
  onNotice: (message: string) => void;
}) {
  const [draft, setDraft] = useState<Entity>(
    () => reference.parseSettings({}) ?? {},
  );
  useEffect(() => {
    setDraft({ ...(reference.parseSettings(data.settings ?? {}) ?? {}) });
  }, [data.settings]);
  const update = (key: string, value: unknown) =>
    setDraft((old) => ({ ...old, [key]: value }));
  async function save(event: Event) {
    event.preventDefault();
    if (!store) return;
    try {
      await store.writeExtra("settings", draft);
      onNotice("Đã lưu cài đặt.");
    } catch (error) {
      onNotice(error instanceof Error ? error.message : String(error));
    }
  }
  return (
    <section class="page">
      <div class="eyebrow">CÀI ĐẶT</div>
      <h1>Nhịp tập trung phù hợp</h1>
      <div class="card">
        <form class="settings-form" onSubmit={save}>
          <div class="form-row">
            <label>
              Tập trung mặc định (phút)
              <input
                type="number"
                min="5"
                max="120"
                required
                value={num(draft.focusDurationMinutes)}
                onInput={(event) =>
                  update(
                    "focusDurationMinutes",
                    Number(event.currentTarget.value),
                  )
                }
              />
            </label>
            <label>
              Nghỉ mặc định (phút)
              <input
                type="number"
                min="1"
                max="30"
                required
                value={num(draft.breakDurationMinutes)}
                onInput={(event) =>
                  update(
                    "breakDurationMinutes",
                    Number(event.currentTarget.value),
                  )
                }
              />
            </label>
          </div>
          <label>
            Mục tiêu mỗi ngày (phút)
            <input
              type="number"
              min="30"
              max="240"
              required
              value={num(draft.dailyGoalMinutes)}
              onInput={(event) =>
                update("dailyGoalMinutes", Number(event.currentTarget.value))
              }
            />
          </label>
          <label class="inline">
            <input
              type="checkbox"
              checked={Boolean(draft.soundEnabled)}
              onChange={(event) =>
                update("soundEnabled", event.currentTarget.checked)
              }
            />{" "}
            Bật âm thanh
          </label>
          <label class="inline">
            <input
              type="checkbox"
              checked={Boolean(draft.calmModeEnabled)}
              onChange={(event) =>
                update("calmModeEnabled", event.currentTarget.checked)
              }
            />{" "}
            Chế độ dịu mắt
          </label>
          <label class="inline">
            <input
              type="checkbox"
              checked={Boolean(draft.allowChildExtendFocus)}
              onChange={(event) =>
                update("allowChildExtendFocus", event.currentTarget.checked)
              }
            />{" "}
            Cho bé thêm giờ tập trung
          </label>
          <label>
            Giới hạn thêm giờ mỗi phiên
            <select
              value={num(draft.maxExtraFocusMinutes)}
              onChange={(event) =>
                update(
                  "maxExtraFocusMinutes",
                  Number(event.currentTarget.value),
                )
              }
            >
              {[0, 15, 30, 60].map((value) => (
                <option value={value}>{value} phút</option>
              ))}
            </select>
          </label>
          <button class="primary">Lưu cài đặt</button>
        </form>
      </div>
    </section>
  );
}

export function LearningLink({
  profileId,
  data,
  guestMode,
}: {
  profileId: string;
  data: DataState;
  guestMode: boolean;
}) {
  const profile = data.profiles.find((row) => row.id === profileId)?.entity;
  const ageBand = str(profile?.ageBand);
  const canPlay = ["4-5", "5-6", "l1", "l2", "l3"].includes(ageBand);
  const emulator =
    ["localhost", "127.0.0.1"].includes(location.hostname) &&
    new URLSearchParams(location.search).has("emulator")
      ? "&emulator=1"
      : "";
  return (
    <section class="page">
      <div class="eyebrow">LUYỆN TƯ DUY</div>
      <h1>Chơi mà nghĩ</h1>
      <div class="card">
        <p>{guestMode
          ? `Tiến bộ của ${str(profile?.name ?? "bé")} chỉ lưu trên trình duyệt này.`
          : `Tiến bộ của ${str(profile?.name ?? "bé")} được đồng bộ giữa web và app khi đăng nhập.`}</p>
        {canPlay ? (
          <a
            class="primary link-button"
            href={`/tu-duy/?profile=${encodeURIComponent(profileId)}${ageBand === "4-5" ? "&band=4-5" : ""}${emulator}`}
          >
            Mở Luyện tư duy
          </a>
        ) : (
          <p class="muted">Luyện tư duy dành cho bé từ 4–5 tuổi trở lên.</p>
        )}
      </div>
    </section>
  );
}
