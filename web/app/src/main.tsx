import { render } from "preact";
import { useEffect, useMemo, useRef, useState } from "preact/hooks";
import type { Auth, User } from "firebase/auth";
import type { Firestore } from "firebase/firestore";
import {
  CloudStore,
  checkRedirect,
  emptyData,
  loadFirebase,
  loginEmail,
  loginGoogle,
  logout,
  resetPassword,
  watchAuth,
  type DataState,
} from "./cloud";
import {
  atMinutes,
  dateKey,
  DAY_LABELS,
  durationLabel,
  monday,
  reference,
  routineOnDate,
  TASK_TYPES,
  taskOnDate,
  timeLabel,
  type Entity,
  type Parsed,
} from "./model";
import "./style.css";

type Tab = "today" | "week" | "focus" | "history";
type FocusRun = {
  task: Entity;
  startedAt: number;
  elapsedBefore: number;
  totalSeconds: number;
  paused: boolean;
};
const blankTask = (profileId: string): Entity => ({
  id: reference.newId(),
  taskType: "CUSTOM",
  name: "",
  emoji: "⭐",
  hour: 16,
  minute: 0,
  daysOfWeek: "2,3,4,5,6",
  focusDurationMinutes: 25,
  breakDurationMinutes: 5,
  enabled: true,
  isCustom: true,
  childProfileId: profileId,
});
const blankRoutine = (profileId: string): Entity => ({
  id: reference.newId(),
  title: "",
  emoji: "⭐",
  deadlineMinutes: 21 * 60,
  repeatDaysMask: 127,
  reminderMinutesBefore: 15,
  enabled: true,
  linkedTimerMinutes: null,
  createdAtMillis: Date.now(),
  childProfileId: profileId,
});
const stringValue = (value: unknown) =>
  typeof value === "string" ? value : String(value ?? "");
const numberValue = (value: unknown) => Number(value ?? 0);
const labelDate = (date: Date) =>
  date.toLocaleDateString("vi-VN", {
    weekday: "long",
    day: "numeric",
    month: "numeric",
  });
const alive = (rows: Parsed[], profileId: string) =>
  rows.filter((row) => row.entity.childProfileId === profileId);

function App() {
  const [auth, setAuth] = useState<Auth | null>(null);
  const [db, setDb] = useState<Firestore | null>(null);
  const [user, setUser] = useState<User | null>(null);
  const [startupError, setStartupError] = useState("");
  const [data, setData] = useState<DataState>(emptyData());
  const [store, setStore] = useState<CloudStore | null>(null);
  const [profileId, setProfileId] = useState("default");
  const [tab, setTab] = useState<Tab>("today");
  const [notice, setNotice] = useState("");
  const [selectedTask, setSelectedTask] = useState<Entity | null>(null);
  const [focusMinutes, setFocusMinutes] = useState(25);
  const [run, setRun] = useState<FocusRun | null>(null);
  const [tick, setTick] = useState(Date.now());
  const runRef = useRef<FocusRun | null>(null);
  const finishing = useRef(false);
  const wakeLock = useRef<WakeLockSentinel | null>(null);
  const audioRef = useRef<AudioContext | null>(null);

  useEffect(() => {
    let unsubscribe = () => {};
    loadFirebase()
      .then(({ auth: readyAuth, db: readyDb }) => {
        setAuth(readyAuth);
        setDb(readyDb);
        checkRedirect(readyAuth).catch((error) => setNotice(error.message));
        unsubscribe = watchAuth(readyAuth, setUser);
      })
      .catch((error) => setStartupError(error.message));
    return () => unsubscribe();
  }, []);
  useEffect(() => {
    if (!db || !user) {
      setStore(null);
      setData(emptyData());
      return;
    }
    const next = new CloudStore(db, user.uid);
    setStore(next);
    next.start(setData);
    return () => next.stop();
  }, [db, user?.uid]);
  const profiles = data.profiles
    .map((row) => row.entity)
    .filter((entity) => !entity.archived);
  useEffect(() => {
    if (
      profiles.length &&
      !profiles.some((profile) => profile.id === profileId)
    )
      setProfileId(stringValue(profiles[0].id));
  }, [data.profiles, profileId]);
  useEffect(() => {
    runRef.current = run;
  }, [run]);
  useEffect(() => {
    if (!run || run.paused) {
      wakeLock.current?.release().catch(() => {});
      wakeLock.current = null;
      return;
    }
    const request = async () => {
      if ("wakeLock" in navigator && document.visibilityState === "visible") {
        try {
          wakeLock.current = await navigator.wakeLock.request("screen");
        } catch {
          /* unsupported or denied */
        }
      }
    };
    request();
    const visibility = () => {
      if (
        document.visibilityState === "visible" &&
        runRef.current &&
        !runRef.current.paused
      )
        request();
    };
    document.addEventListener("visibilitychange", visibility);
    return () => {
      document.removeEventListener("visibilitychange", visibility);
      wakeLock.current?.release().catch(() => {});
      wakeLock.current = null;
    };
  }, [run?.paused, run?.startedAt]);
  useEffect(() => {
    if (!run) return;
    const interval = window.setInterval(() => {
      const now = Date.now();
      setTick(now);
      const current = runRef.current;
      if (
        current &&
        !current.paused &&
        current.elapsedBefore + (now - current.startedAt) / 1000 >=
          current.totalSeconds &&
        !finishing.current
      )
        finishFocus(true);
    }, 250);
    return () => window.clearInterval(interval);
  }, [run?.startedAt, run?.paused]);

  function report(error: unknown) {
    setNotice(error instanceof Error ? error.message : String(error));
  }
  async function write(
    name: "tasks" | "routines" | "completions" | "sessions",
    entity: Entity,
    deleted = false,
  ) {
    if (!store) return;
    try {
      await store.write(name, entity, deleted);
      setNotice(deleted ? "Đã xoá mục." : "Đã lưu và đồng bộ.");
    } catch (error) {
      report(error);
    }
  }
  function startFocus(
    task: Entity,
    minutes = numberValue(task.focusDurationMinutes),
  ) {
    finishing.current = false;
    try {
      audioRef.current = new AudioContext();
      audioRef.current.resume().catch(() => {});
    } catch {
      audioRef.current = null;
    }
    setSelectedTask(task);
    setRun({
      task,
      startedAt: Date.now(),
      elapsedBefore: 0,
      totalSeconds: minutes * 60,
      paused: false,
    });
    setTab("focus");
  }
  function pauseFocus() {
    setRun((old) =>
      old && !old.paused
        ? {
            ...old,
            elapsedBefore:
              old.elapsedBefore + (Date.now() - old.startedAt) / 1000,
            paused: true,
          }
        : old,
    );
  }
  function resumeFocus() {
    setRun((old) =>
      old && old.paused
        ? { ...old, startedAt: Date.now(), paused: false }
        : old,
    );
  }
  function playDone() {
    try {
      const audio = audioRef.current;
      if (!audio) return;
      const oscillator = audio.createOscillator();
      const gain = audio.createGain();
      oscillator.connect(gain);
      gain.connect(audio.destination);
      oscillator.frequency.value = 740;
      gain.gain.setValueAtTime(0.08, audio.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.001, audio.currentTime + 0.45);
      oscillator.start();
      oscillator.stop(audio.currentTime + 0.45);
      setTimeout(() => audio.close(), 600);
      audioRef.current = null;
    } catch {
      /* sound is optional */
    }
  }
  async function finishFocus(completed: boolean) {
    const current = runRef.current;
    if (!current || finishing.current) return;
    finishing.current = true;
    const elapsed =
      current.elapsedBefore +
      (current.paused ? 0 : (Date.now() - current.startedAt) / 1000);
    const session: Entity = {
      id: reference.newId(),
      durationSeconds: Math.min(
        current.totalSeconds,
        Math.max(0, Math.floor(elapsed)),
      ),
      isFocus: true,
      timestampMillis: Date.now(),
      childProfileId: stringValue(current.task.childProfileId),
      taskId: stringValue(current.task.id),
      taskTitle: stringValue(current.task.name),
      completed,
    };
    setRun(null);
    runRef.current = null;
    if (completed) {
      playDone();
      if ("Notification" in window && Notification.permission === "granted")
        new Notification("KidFocus", {
          body: `Hoàn thành ${session.taskTitle}`,
        });
    } else {
      audioRef.current?.close().catch(() => {});
      audioRef.current = null;
    }
    await write("sessions", session);
    setTab("history");
  }

  if (startupError)
    return (
      <main class="setup">
        <div class="brand">
          <span class="mark">K</span> KidFocus
        </div>
        <h1>Chưa thể mở ứng dụng web</h1>
        <p>{startupError}</p>
        <p class="muted">
          Bản web cần cấu hình Firebase public của dự án. Ứng dụng Android vẫn
          hoạt động bình thường.
        </p>
      </main>
    );
  if (!auth)
    return (
      <main class="setup">
        <div class="brand">
          <span class="mark">K</span> KidFocus
        </div>
        <p>Đang mở ứng dụng…</p>
      </main>
    );
  if (!user) return <Login auth={auth} notice={notice} onError={report} />;

  const currentProfile = profiles.find((profile) => profile.id === profileId);
  return (
    <div class="app-shell">
      <header class="topbar">
        <div class="brand">
          <span class="mark">K</span>
          <span>KidFocus</span>
        </div>
        <button class="text-button" onClick={() => logout(auth).catch(report)}>
          Đăng xuất
        </button>
      </header>
      <div class="top-controls">
        <label class="profile-picker">
          Hồ sơ bé
          <select
            value={profileId}
            onChange={(event) => setProfileId(event.currentTarget.value)}
          >
            {profiles.length ? (
              profiles.map((profile) => (
                <option
                  key={stringValue(profile.id)}
                  value={stringValue(profile.id)}
                >
                  {stringValue(profile.avatarEmoji)} {stringValue(profile.name)}
                </option>
              ))
            ) : (
              <option value="default">Bé của tôi</option>
            )}
          </select>
        </label>
        <button
          class={`sync-chip ${data.sync === "Lỗi" ? "error" : ""}`}
          onClick={() => store?.retry()}
          title={data.error || "Chạm để đồng bộ lại"}
        >
          {data.sync === "Lỗi"
            ? "↻ "
            : data.sync === "Đã đồng bộ"
              ? "✓ "
              : "◌ "}
          {data.sync}
        </button>
      </div>
      {data.warning && (
        <aside class="warning">
          <strong>
            Một máy đang dùng bản app cũ — hãy cập nhật để đồng bộ.
          </strong>
          <button onClick={() => store?.reimport().catch(report)}>
            Nhập lại bản cũ
          </button>
        </aside>
      )}
      {notice && (
        <div role="status" class="toast">
          {notice}
          <button aria-label="Đóng thông báo" onClick={() => setNotice("")}>
            ×
          </button>
        </div>
      )}
      <main>
        {tab === "today" && (
          <Today
            data={data}
            profileId={profileId}
            onFocus={(task) => startFocus(task)}
            onComplete={(entity) => write("completions", entity)}
          />
        )}
        {tab === "week" && (
          <Week data={data} profileId={profileId} onWrite={write} />
        )}
        {tab === "focus" && (
          <section class="page">
            <div class="eyebrow">TẬP TRUNG</div>
            <h1>{run ? stringValue(run.task.name) : "Chọn hoạt động"}</h1>
            <p class="muted">
              Trên web không có báo thức khi đóng tab — dùng app để nhắc giờ.
            </p>
            {run ? (
              <>
                <div class="timer" aria-live="off">
                  {durationLabel(
                    Math.max(
                      0,
                      Math.ceil(
                        run.totalSeconds -
                          run.elapsedBefore -
                          (run.paused ? 0 : (tick - run.startedAt) / 1000),
                      ),
                    ),
                  )}
                </div>
                <p class="center">
                  {run.paused ? "Đã tạm dừng" : "Đang tập trung"} ·{" "}
                  {Math.ceil(run.totalSeconds / 60)} phút
                </p>
                <div class="actions center">
                  {run.paused ? (
                    <button class="primary" onClick={resumeFocus}>
                      Tiếp tục
                    </button>
                  ) : (
                    <button onClick={pauseFocus}>Tạm dừng</button>
                  )}
                  <button class="danger" onClick={() => finishFocus(false)}>
                    Dừng sớm
                  </button>
                </div>
              </>
            ) : (
              <>
                <div class="card">
                  <label>
                    Hoạt động
                    <select
                      value={stringValue(selectedTask?.id ?? "")}
                      onChange={(event) => {
                        const task =
                          data.tasks.find(
                            (row) =>
                              stringValue(row.entity.id) ===
                              event.currentTarget.value,
                          )?.entity ?? null;
                        setSelectedTask(task);
                        if (task)
                          setFocusMinutes(
                            numberValue(task.focusDurationMinutes),
                          );
                      }}
                    >
                      <option value="">Chọn hoạt động</option>
                      {alive(data.tasks, profileId)
                        .filter((row) => row.entity.enabled)
                        .map((row) => (
                          <option value={stringValue(row.entity.id)}>
                            {stringValue(row.entity.emoji)}{" "}
                            {stringValue(row.entity.name)}
                          </option>
                        ))}
                    </select>
                  </label>
                  <label>
                    Thời lượng phiên (phút)
                    <input
                      type="number"
                      min="1"
                      max="120"
                      value={focusMinutes}
                      onInput={(event) =>
                        setFocusMinutes(Number(event.currentTarget.value))
                      }
                    />
                  </label>
                  <button
                    class="primary full"
                    disabled={
                      !selectedTask || focusMinutes < 1 || focusMinutes > 120
                    }
                    onClick={() =>
                      selectedTask && startFocus(selectedTask, focusMinutes)
                    }
                  >
                    Bắt đầu
                  </button>
                </div>
                {!alive(data.tasks, profileId).length && (
                  <p class="muted">
                    Chưa có hoạt động. Thêm trong mục Lịch trước nhé.
                  </p>
                )}
              </>
            )}
            {"Notification" in window &&
              Notification.permission === "default" && (
                <button
                  class="text-button"
                  onClick={() => Notification.requestPermission()}
                >
                  Cho phép thông báo khi xong
                </button>
              )}
          </section>
        )}
        {tab === "history" && <History data={data} profileId={profileId} />}
      </main>
      <nav class="bottom-nav" aria-label="Điều hướng">
        <NavButton
          tab="today"
          current={tab}
          icon="☀️"
          label="Hôm nay"
          onClick={setTab}
        />
        <NavButton
          tab="week"
          current={tab}
          icon="🗓️"
          label="Lịch"
          onClick={setTab}
        />
        <NavButton
          tab="focus"
          current={tab}
          icon="⏱️"
          label="Tập trung"
          onClick={setTab}
        />
        <NavButton
          tab="history"
          current={tab}
          icon="🏅"
          label="Đã xong"
          onClick={setTab}
        />
      </nav>
    </div>
  );
}

function Login({
  auth,
  notice,
  onError,
}: {
  auth: Auth;
  notice: string;
  onError: (error: unknown) => void;
}) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [trusted, setTrusted] = useState(
    localStorage.getItem("kidfocus-trusted-device") === "yes",
  );
  async function submit(event: Event) {
    event.preventDefault();
    setBusy(true);
    try {
      await loginEmail(auth, email.trim(), password);
    } catch (error) {
      onError(error);
    } finally {
      setBusy(false);
    }
  }
  return (
    <main class="login">
      <div class="brand">
        <span class="mark">K</span> KidFocus
      </div>
      <section class="login-card">
        <div class="eyebrow">CÙNG CON BẮT ĐẦU</div>
        <h1>Lịch sinh hoạt rõ ràng hơn mỗi ngày</h1>
        <p class="muted">Đăng nhập tài khoản KidFocus của gia đình.</p>
        {notice && (
          <p role="alert" class="error-text">
            {notice}
          </p>
        )}
        <form onSubmit={submit}>
          <label>
            Email
            <input
              type="email"
              required
              autocomplete="email"
              value={email}
              onInput={(event) => setEmail(event.currentTarget.value)}
            />
          </label>
          <label>
            Mật khẩu
            <input
              type="password"
              required
              autocomplete="current-password"
              value={password}
              onInput={(event) => setPassword(event.currentTarget.value)}
            />
          </label>
          <button class="primary full" disabled={busy}>
            Đăng nhập
          </button>
        </form>
        <button
          class="google full"
          onClick={() => loginGoogle(auth).catch(onError)}
        >
          Tiếp tục với Google
        </button>
        <button
          class="text-button full"
          onClick={() => {
            if (!email.trim()) {
              onError(new Error("Nhập email trước để đặt lại mật khẩu."));
              return;
            }
            resetPassword(auth, email.trim())
              .then(() => onError(new Error("Đã gửi email đặt lại mật khẩu.")))
              .catch(onError);
          }}
        >
          Quên mật khẩu?
        </button>
        <label class="trust">
          <input
            type="checkbox"
            checked={trusted}
            onChange={(event) => {
              const value = event.currentTarget.checked;
              setTrusted(value);
              localStorage.setItem(
                "kidfocus-trusted-device",
                value ? "yes" : "no",
              );
              location.reload();
            }}
          />{" "}
          Đây là máy của tôi — lưu dữ liệu để dùng khi mất mạng
        </label>
        <p class="shared-note">
          Dùng máy chung? Để ô trên tắt và nhớ đăng xuất sau khi dùng. Dữ liệu
          chỉ mở với tài khoản của bạn.
        </p>
      </section>
    </main>
  );
}

function NavButton({
  tab,
  current,
  icon,
  label,
  onClick,
}: {
  tab: Tab;
  current: Tab;
  icon: string;
  label: string;
  onClick: (tab: Tab) => void;
}) {
  return (
    <button
      aria-current={tab === current ? "page" : undefined}
      onClick={() => onClick(tab)}
    >
      <span aria-hidden="true">{icon}</span>
      <small>{label}</small>
    </button>
  );
}

function Today({
  data,
  profileId,
  onFocus,
  onComplete,
}: {
  data: DataState;
  profileId: string;
  onFocus: (task: Entity) => void;
  onComplete: (entity: Entity) => void;
}) {
  const today = new Date();
  const key = dateKey(today);
  const items = [
    ...alive(data.tasks, profileId)
      .filter((row) => taskOnDate(row.entity, today))
      .map((row) => ({
        type: "task" as const,
        minutes:
          numberValue(row.entity.hour) * 60 + numberValue(row.entity.minute),
        entity: row.entity,
      })),
    ...alive(data.routines, profileId)
      .filter((row) => routineOnDate(row.entity, today))
      .map((row) => ({
        type: "routine" as const,
        minutes: numberValue(row.entity.deadlineMinutes),
        entity: row.entity,
      })),
  ].sort((a, b) => a.minutes - b.minutes);
  return (
    <section class="page">
      <div class="eyebrow">
        HÔM NAY · {labelDate(today).toLocaleUpperCase("vi-VN")}
      </div>
      <h1>Việc của con hôm nay</h1>
      <p class="muted">Chọn hoạt động theo nhịp của con; lịch chỉ là gợi ý.</p>
      {items.length ? (
        <div class="timeline">
          {items.map((item) => {
            const e = item.entity;
            const completion =
              item.type === "routine"
                ? data.completions.find(
                    (row) =>
                      row.entity.routineId === e.id &&
                      row.entity.occurrenceDate === key,
                  )
                : null;
            return (
              <article class="timeline-item" key={`${item.type}-${e.id}`}>
                <span class="when">{timeLabel(item.minutes)}</span>
                <div class="task-card">
                  <div class="task-heading">
                    <span class="emoji">{stringValue(e.emoji)}</span>
                    <div>
                      <strong>
                        {stringValue(item.type === "task" ? e.name : e.title)}
                      </strong>
                      <small>
                        {item.type === "task"
                          ? `${e.focusDurationMinutes} phút tập trung`
                          : completion
                            ? completion.entity.status === "ON_TIME"
                              ? "✓ Đúng giờ"
                              : "✓ Đã hoàn thành"
                            : "Việc hằng ngày"}
                      </small>
                    </div>
                  </div>
                  {item.type === "task" ? (
                    <button class="primary" onClick={() => onFocus(e)}>
                      Bắt đầu tập trung
                    </button>
                  ) : (
                    !completion && (
                      <button
                        onClick={() => {
                          const now = Date.now();
                          const deadline = atMinutes(today, item.minutes);
                          onComplete({
                            routineId: e.id,
                            occurrenceDate: key,
                            scheduledDeadlineMillis: deadline,
                            completedAtMillis: now,
                            status: now <= deadline ? "ON_TIME" : "LATE",
                          });
                        }}
                      >
                        Đánh dấu xong
                      </button>
                    )
                  )}
                </div>
              </article>
            );
          })}
        </div>
      ) : (
        <div class="empty card">
          🌱 Chưa có hoạt động hôm nay. Mở Lịch để thêm nhé.
        </div>
      )}
    </section>
  );
}

function Week({
  data,
  profileId,
  onWrite,
}: {
  data: DataState;
  profileId: string;
  onWrite: (
    name: "tasks" | "routines",
    entity: Entity,
    deleted?: boolean,
  ) => void;
}) {
  const [editTask, setEditTask] = useState<Entity | null>(null);
  const [editRoutine, setEditRoutine] = useState<Entity | null>(null);
  const [weekShift, setWeekShift] = useState(0);
  const start = monday(new Date());
  start.setDate(start.getDate() + weekShift * 7);
  const days = Array.from({ length: 7 }, (_, i) => {
    const day = new Date(start);
    day.setDate(day.getDate() + i);
    return day;
  });
  return (
    <section class="page">
      <div class="eyebrow">LỊCH TUẦN CHUNG</div>
      <h1>Mỗi tuần một nhịp</h1>
      <p class="muted">Lịch này lặp lại cho mọi tuần.</p>
      <div class="week-arrows">
        <button
          onClick={() => setWeekShift((value) => value - 1)}
          aria-label="Tuần trước"
        >
          ←
        </button>
        <strong>
          {dateKey(start)} – {dateKey(days[6])}
        </strong>
        <button
          onClick={() => setWeekShift((value) => value + 1)}
          aria-label="Tuần sau"
        >
          →
        </button>
      </div>
      <div class="week-grid">
        {days.map((day, index) => (
          <article class="day-card" key={dateKey(day)}>
            <h2>
              {DAY_LABELS[day.getDay()]} <span>{day.getDate()}</span>
            </h2>
            {alive(data.tasks, profileId)
              .filter((row) => taskOnDate(row.entity, day))
              .map((row) => (
                <button
                  class="mini-item"
                  onClick={() => setEditTask({ ...row.entity })}
                >
                  <span>
                    {timeLabel(
                      numberValue(row.entity.hour) * 60 +
                        numberValue(row.entity.minute),
                    )}{" "}
                    {stringValue(row.entity.emoji)}
                  </span>
                  <strong>{stringValue(row.entity.name)}</strong>
                </button>
              ))}
            {alive(data.routines, profileId)
              .filter((row) => routineOnDate(row.entity, day))
              .map((row) => (
                <button
                  class="mini-item routine"
                  onClick={() => setEditRoutine({ ...row.entity })}
                >
                  <span>
                    {timeLabel(numberValue(row.entity.deadlineMinutes))}{" "}
                    {stringValue(row.entity.emoji)}
                  </span>
                  <strong>{stringValue(row.entity.title)}</strong>
                </button>
              ))}
            {!data.tasks.some(
              (row) =>
                row.entity.childProfileId === profileId &&
                taskOnDate(row.entity, day),
            ) &&
              !data.routines.some(
                (row) =>
                  row.entity.childProfileId === profileId &&
                  routineOnDate(row.entity, day),
              ) && <p class="empty-day">Nghỉ ngơi</p>}
          </article>
        ))}
      </div>
      <div class="actions">
        <button
          class="primary"
          onClick={() => setEditTask(blankTask(profileId))}
        >
          + Hoạt động
        </button>
        <button onClick={() => setEditRoutine(blankRoutine(profileId))}>
          + Việc hằng ngày
        </button>
      </div>
      {editTask && (
        <TaskForm
          value={editTask}
          onClose={() => setEditTask(null)}
          onSave={(value) => {
            onWrite("tasks", value);
            setEditTask(null);
          }}
          onDelete={(value) => {
            onWrite("tasks", value, true);
            setEditTask(null);
          }}
        />
      )}
      {editRoutine && (
        <RoutineForm
          value={editRoutine}
          onClose={() => setEditRoutine(null)}
          onSave={(value) => {
            onWrite("routines", value);
            setEditRoutine(null);
          }}
          onDelete={(value) => {
            onWrite("routines", value, true);
            setEditRoutine(null);
          }}
        />
      )}
    </section>
  );
}

function DayCheckboxes({
  selected,
  onChange,
}: {
  selected: number[];
  onChange: (days: number[]) => void;
}) {
  return (
    <fieldset class="days">
      <legend>Ngày lặp</legend>
      {DAY_LABELS.map((day, index) => (
        <label key={day}>
          <input
            type="checkbox"
            checked={selected.includes(index + 1)}
            onChange={(event) => {
              const days = event.currentTarget.checked
                ? [...selected, index + 1]
                : selected.filter((value) => value !== index + 1);
              onChange(days.sort());
            }}
          />
          {day}
        </label>
      ))}
    </fieldset>
  );
}
function TaskForm({
  value,
  onClose,
  onSave,
  onDelete,
}: {
  value: Entity;
  onClose: () => void;
  onSave: (value: Entity) => void;
  onDelete: (value: Entity) => void;
}) {
  const [draft, setDraft] = useState(value);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const days = stringValue(draft.daysOfWeek)
    .split(",")
    .map(Number)
    .filter(Boolean);
  const update = (key: string, value: unknown) =>
    setDraft((old) => ({ ...old, [key]: value }));
  const scheduled = timeLabel(
    numberValue(draft.hour) * 60 + numberValue(draft.minute),
  );
  return (
    <div class="modal-backdrop" onClick={onClose}>
      <section
        class="modal"
        role="dialog"
        aria-modal="true"
        aria-label="Hoạt động"
        onClick={(event) => event.stopPropagation()}
      >
        <div class="modal-top">
          <h2>Hoạt động</h2>
          <button aria-label="Đóng" onClick={onClose}>
            ×
          </button>
        </div>
        <form
          onSubmit={(event) => {
            event.preventDefault();
            if (!days.length) return;
            onSave(draft);
          }}
        >
          <label>
            Tên hoạt động
            <input
              maxlength={80}
              required
              value={stringValue(draft.name)}
              onInput={(event) => update("name", event.currentTarget.value)}
            />
          </label>
          <div class="form-row">
            <label>
              Loại
              <select
                value={stringValue(draft.taskType)}
                onChange={(event) => {
                  const type = TASK_TYPES.find(
                    (item) => item[0] === event.currentTarget.value,
                  );
                  setDraft((old) => ({
                    ...old,
                    taskType: type?.[0] ?? "CUSTOM",
                    emoji: type?.[2] ?? "⭐",
                    isCustom: type?.[0] === "CUSTOM",
                  }));
                }}
              >
                {TASK_TYPES.map(([key, name]) => (
                  <option value={key}>{name}</option>
                ))}
              </select>
            </label>
            <label>
              Giờ bắt đầu
              <input
                type="time"
                required
                value={scheduled}
                onInput={(event) => {
                  const [hour, minute] = event.currentTarget.value
                    .split(":")
                    .map(Number);
                  setDraft((old) => ({ ...old, hour, minute }));
                }}
              />
            </label>
          </div>
          <div class="form-row">
            <label>
              Tập trung (phút)
              <input
                type="number"
                min="5"
                max="120"
                required
                value={numberValue(draft.focusDurationMinutes)}
                onInput={(event) =>
                  update(
                    "focusDurationMinutes",
                    Number(event.currentTarget.value),
                  )
                }
              />
            </label>
            <label>
              Nghỉ (phút)
              <input
                type="number"
                min="1"
                max="30"
                required
                value={numberValue(draft.breakDurationMinutes)}
                onInput={(event) =>
                  update(
                    "breakDurationMinutes",
                    Number(event.currentTarget.value),
                  )
                }
              />
            </label>
          </div>
          <DayCheckboxes
            selected={days}
            onChange={(value) => update("daysOfWeek", value.join(","))}
          />
          <label class="inline">
            <input
              type="checkbox"
              checked={Boolean(draft.enabled)}
              onChange={(event) =>
                update("enabled", event.currentTarget.checked)
              }
            />{" "}
            Đang dùng
          </label>
          <div class="actions">
            <button class="primary" disabled={!days.length}>
              Lưu hoạt động
            </button>
            <button type="button" onClick={onClose}>
              Huỷ
            </button>
          </div>
        </form>
        <button
          class="text-button danger-text"
          onClick={() =>
            confirmDelete ? onDelete(draft) : setConfirmDelete(true)
          }
        >
          {confirmDelete ? "Xác nhận xoá hoạt động" : "Xoá hoạt động"}
        </button>
      </section>
    </div>
  );
}
function RoutineForm({
  value,
  onClose,
  onSave,
  onDelete,
}: {
  value: Entity;
  onClose: () => void;
  onSave: (value: Entity) => void;
  onDelete: (value: Entity) => void;
}) {
  const [draft, setDraft] = useState(value);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const selected = Array.from({ length: 7 }, (_, i) => i + 1).filter(
    (calendarDay) => {
      const iso = calendarDay === 1 ? 7 : calendarDay - 1;
      return (numberValue(draft.repeatDaysMask) & (1 << (iso - 1))) !== 0;
    },
  );
  const update = (key: string, value: unknown) =>
    setDraft((old) => ({ ...old, [key]: value }));
  return (
    <div class="modal-backdrop" onClick={onClose}>
      <section
        class="modal"
        role="dialog"
        aria-modal="true"
        aria-label="Việc hằng ngày"
        onClick={(event) => event.stopPropagation()}
      >
        <div class="modal-top">
          <h2>Việc hằng ngày</h2>
          <button aria-label="Đóng" onClick={onClose}>
            ×
          </button>
        </div>
        <form
          onSubmit={(event) => {
            event.preventDefault();
            if (!selected.length) return;
            onSave(draft);
          }}
        >
          <label>
            Tên việc
            <input
              maxlength={80}
              required
              value={stringValue(draft.title)}
              onInput={(event) => update("title", event.currentTarget.value)}
            />
          </label>
          <div class="form-row">
            <label>
              Biểu tượng
              <input
                maxlength={8}
                value={stringValue(draft.emoji)}
                onInput={(event) => update("emoji", event.currentTarget.value)}
              />
            </label>
            <label>
              Giờ cần xong
              <input
                type="time"
                required
                value={timeLabel(numberValue(draft.deadlineMinutes))}
                onInput={(event) => {
                  const [hour, minute] = event.currentTarget.value
                    .split(":")
                    .map(Number);
                  update("deadlineMinutes", hour * 60 + minute);
                }}
              />
            </label>
          </div>
          <div class="form-row">
            <label>
              Nhắc trước (phút)
              <input
                type="number"
                min="0"
                max="180"
                required
                value={numberValue(draft.reminderMinutesBefore)}
                onInput={(event) =>
                  update(
                    "reminderMinutesBefore",
                    Number(event.currentTarget.value),
                  )
                }
              />
            </label>
            <label>
              Hẹn giờ kèm theo
              <input
                type="number"
                min="5"
                max="120"
                value={
                  draft.linkedTimerMinutes == null
                    ? ""
                    : numberValue(draft.linkedTimerMinutes)
                }
                onInput={(event) =>
                  update(
                    "linkedTimerMinutes",
                    event.currentTarget.value
                      ? Number(event.currentTarget.value)
                      : null,
                  )
                }
              />
            </label>
          </div>
          <DayCheckboxes
            selected={selected}
            onChange={(days) =>
              update(
                "repeatDaysMask",
                days.reduce(
                  (mask, day) => mask | (1 << ((day === 1 ? 7 : day - 1) - 1)),
                  0,
                ),
              )
            }
          />
          <label class="inline">
            <input
              type="checkbox"
              checked={Boolean(draft.enabled)}
              onChange={(event) =>
                update("enabled", event.currentTarget.checked)
              }
            />{" "}
            Đang dùng
          </label>
          <div class="actions">
            <button class="primary" disabled={!selected.length}>
              Lưu việc
            </button>
            <button type="button" onClick={onClose}>
              Huỷ
            </button>
          </div>
        </form>
        <button
          class="text-button danger-text"
          onClick={() =>
            confirmDelete ? onDelete(draft) : setConfirmDelete(true)
          }
        >
          {confirmDelete ? "Xác nhận xoá việc" : "Xoá việc"}
        </button>
      </section>
    </div>
  );
}

function History({ data, profileId }: { data: DataState; profileId: string }) {
  const today = new Date();
  const first = monday(today);
  const end = new Date(first);
  end.setDate(end.getDate() + 7);
  const sessions = alive(data.sessions, profileId)
    .filter((row) => row.entity.isFocus)
    .sort(
      (a, b) =>
        numberValue(b.entity.timestampMillis) -
        numberValue(a.entity.timestampMillis),
    );
  const bars = Array.from({ length: 7 }, (_, index) => {
    const date = new Date(first);
    date.setDate(date.getDate() + index);
    const next = new Date(date);
    next.setDate(next.getDate() + 1);
    return Math.floor(
      sessions
        .filter(
          (row) =>
            numberValue(row.entity.timestampMillis) >= date.getTime() &&
            numberValue(row.entity.timestampMillis) < next.getTime(),
        )
        .reduce(
          (total, row) => total + numberValue(row.entity.durationSeconds),
          0,
        ) / 60,
    );
  });
  const max = Math.max(1, ...bars);
  const routineMap = new Map(
    alive(data.routines, profileId).map((row) => [
      numberValue(row.entity.id),
      stringValue(row.entity.title),
    ]),
  );
  const completed = data.completions
    .filter((row) => routineMap.has(numberValue(row.entity.routineId)))
    .map((row) => ({
      title:
        routineMap.get(numberValue(row.entity.routineId)) ?? "Việc hằng ngày",
      time: numberValue(row.entity.completedAtMillis),
      detail: row.entity.status === "ON_TIME" ? "Đúng giờ" : "Hoàn thành",
    }));
  const entries = [
    ...sessions.map((row) => ({
      title: stringValue(row.entity.taskTitle || "Tập trung"),
      time: numberValue(row.entity.timestampMillis),
      detail: `${Math.floor(numberValue(row.entity.durationSeconds) / 60)} phút · ${row.entity.completed ? "Hoàn thành" : "Dừng sớm"}`,
    })),
    ...completed,
  ].sort((a, b) => b.time - a.time);
  return (
    <section class="page">
      <div class="eyebrow">NHÌN LẠI MỖI TUẦN</div>
      <h1>Đã hoàn thành</h1>
      <div class="stats">
        <div>
          <strong>{bars.reduce((a, b) => a + b, 0)}</strong>
          <span>phút tuần này</span>
        </div>
        <div>
          <strong>
            {
              sessions.filter(
                (row) =>
                  numberValue(row.entity.timestampMillis) >= first.getTime() &&
                  numberValue(row.entity.timestampMillis) < end.getTime(),
              ).length
            }
          </strong>
          <span>lượt tập trung</span>
        </div>
      </div>
      <div class="card">
        <h2>7 ngày gần đây</h2>
        <div class="bars">
          {bars.map((minutes, index) => (
            <div class="bar-cell" key={index}>
              <span>{minutes ? `${minutes}′` : ""}</span>
              <div class="bar-track">
                <div
                  style={{
                    height: `${Math.max(minutes ? 8 : 0, (minutes / max) * 100)}%`,
                  }}
                />
              </div>
              <small>{DAY_LABELS[(index + 1) % 7]}</small>
            </div>
          ))}
        </div>
      </div>
      <h2>Nhật ký</h2>
      {entries.length ? (
        <div class="history-list">
          {entries.map((entry) => (
            <article class="history-row" key={`${entry.time}-${entry.title}`}>
              <span class="history-icon">✓</span>
              <div>
                <strong>{entry.title}</strong>
                <small>
                  {new Date(entry.time).toLocaleString("vi-VN")} ·{" "}
                  {entry.detail}
                </small>
              </div>
            </article>
          ))}
        </div>
      ) : (
        <div class="empty card">
          Chưa có lượt nào. Bắt đầu một hoạt động để lưu vào đây.
        </div>
      )}
    </section>
  );
}

render(<App />, document.getElementById("app")!);
