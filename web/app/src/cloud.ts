import {
  initializeApp,
  type FirebaseApp,
  type FirebaseOptions,
} from "firebase/app";
import {
  connectAuthEmulator,
  getAuth,
  GoogleAuthProvider,
  onAuthStateChanged,
  sendPasswordResetEmail,
  signInWithEmailAndPassword,
  signInWithPopup,
  signInWithRedirect,
  getRedirectResult,
  signOut,
  type Auth,
  type User,
} from "firebase/auth";
import {
  collection,
  connectFirestoreEmulator,
  doc,
  getDoc,
  initializeFirestore,
  memoryLocalCache,
  persistentLocalCache,
  onSnapshot,
  query,
  runTransaction,
  terminate,
  clearIndexedDbPersistence,
  where,
  type Firestore,
  type Unsubscribe,
} from "firebase/firestore";
import {
  reference,
  type CollectionName,
  type Entity,
  type Parsed,
} from "./model";
import * as extraSync from "./extra-sync.mjs";
import * as migrationLease from "./migration-lease.mjs";

export type ExtraCollection = "settings" | "dayLogs" | "learning";

export type SyncState = "Đang đồng bộ" | "Đã đồng bộ" | "Lỗi" | "Chỉ trên máy";
export type DataState = { [K in CollectionName]: Parsed[] } & {
  dayLogs: Parsed[];
  learning: Parsed[];
  settings: Entity | null;
  warning: boolean;
  sync: SyncState;
  error: string;
};
export const emptyData = (): DataState => ({
  tasks: [],
  routines: [],
  completions: [],
  sessions: [],
  profiles: [],
  dayLogs: [],
  learning: [],
  settings: null,
  warning: false,
  sync: "Đang đồng bộ",
  error: "",
});

const collections: CollectionName[] = [
  "profiles",
  "tasks",
  "routines",
  "completions",
  "sessions",
];
const deviceStorageKey = "kidfocus-web-device-id";
function deviceId(): string {
  let value = localStorage.getItem(deviceStorageKey);
  if (!value) {
    value = crypto.randomUUID();
    localStorage.setItem(deviceStorageKey, value);
  }
  return value;
}

export async function loadFirebase(): Promise<{
  app: FirebaseApp;
  auth: Auth;
  db: Firestore;
}> {
  const response = await fetch("/app/firebase-config.json", {
    cache: "no-store",
  });
  if (!response.ok)
    throw new Error(
      "Chưa có cấu hình Firebase web. Cần thêm web/app/firebase-config.json trước khi chạy.",
    );
  const options = (await response.json()) as FirebaseOptions;
  if (
    !options.apiKey ||
    !options.projectId ||
    !options.appId ||
    options.apiKey === "PUBLIC_WEB_API_KEY"
  ) {
    throw new Error("Cấu hình Firebase web chưa hợp lệ.");
  }
  const app = initializeApp(options);
  const auth = getAuth(app);
  const trusted = localStorage.getItem("kidfocus-trusted-device") === "yes";
  const db = initializeFirestore(app, {
    localCache: trusted ? persistentLocalCache() : memoryLocalCache(),
  });
  if (
    (location.hostname === "localhost" || location.hostname === "127.0.0.1") &&
    new URLSearchParams(location.search).has("emulator")
  ) {
    connectAuthEmulator(auth, "http://127.0.0.1:9099", {
      disableWarnings: true,
    });
    connectFirestoreEmulator(db, "127.0.0.1", 8080);
  }
  return { app, auth, db };
}

export const watchAuth = (
  auth: Auth,
  listener: (user: User | null) => void,
): Unsubscribe => onAuthStateChanged(auth, listener);
export const loginEmail = (auth: Auth, email: string, password: string) =>
  signInWithEmailAndPassword(auth, email, password);
export const resetPassword = (auth: Auth, email: string) =>
  sendPasswordResetEmail(auth, email);
export async function logout(auth: Auth, db?: Firestore): Promise<void> {
  await signOut(auth);
  if (db) await clearPrivateCache(db);
}

export async function clearPrivateCache(db: Firestore): Promise<void> {
  await terminate(db);
  await clearIndexedDbPersistence(db);
}
export async function loginGoogle(auth: Auth): Promise<void> {
  const provider = new GoogleAuthProvider();
  if (matchMedia("(max-width: 600px)").matches) {
    await signInWithRedirect(auth, provider);
    return;
  }
  try {
    await signInWithPopup(auth, provider);
  } catch (error) {
    const code = (error as { code?: string }).code ?? "";
    if (
      code === "auth/popup-blocked" ||
      code === "auth/operation-not-supported-in-this-environment" ||
      code === "auth/cancelled-popup-request"
    ) {
      await signInWithRedirect(auth, provider);
    } else throw error;
  }
}
export const checkRedirect = (auth: Auth) => getRedirectResult(auth);

/** The web client shares the Android migration protocol. Only the first client sets meta. */
export async function ensureV2(db: Firestore, uid: string): Promise<number> {
  const root = doc(db, "users", uid);
  const meta = doc(root, "sync", "meta");
  const worker = deviceId();
  for (;;) {
    const nowMillis = Date.now();
    const decision = await runTransaction(db, async tx => {
      const current = await tx.get(meta);
      const result = migrationLease.decide(current.data(), nowMillis, worker);
      if (result.kind === "claim") tx.set(meta, migrationLease.claim(nowMillis, worker));
      return result;
    });
    if (decision.kind === "ready") return Number(decision.migratedAtMillis ?? 0);
    if (decision.kind === "wait") {
      await new Promise(resolve => setTimeout(resolve, Math.min(1000, Math.max(100, Number(decision.untilMillis ?? nowMillis) - nowMillis))));
      continue;
    }
    const backup = await getDoc(doc(root, "backups", "current"));
    const snapshot = backup.data();
    const migrated = snapshot?.schemaVersion === 1
      ? reference.migrateV1(snapshot, { nowMillis, deviceId: worker }) : null;
    const writes = [
      ...(migrated?.docs ?? []).map(item => ({
        ref: doc(root, item.collection, item.id), collection: item.collection, id: item.id, data: item.data,
      })),
      ...(migrated?.settings ? [{ ref: doc(root, "sync", "settings"), collection: "settings",
        id: "settings", data: migrated.settings }] : []),
    ];
    let leaseLost = false;
    for (let index = 0; index < writes.length; index++) {
      if (index > 0 && index % 40 === 0) {
        const renewed = await runTransaction(db, async tx => {
          const current = await tx.get(meta);
          if (current.data()?.state !== "migrating" || current.data()?.by !== worker) return false;
          tx.set(meta, migrationLease.claim(Date.now(), worker));
          return true;
        });
        if (!renewed) { leaseLost = true; break; }
      }
      const item = writes[index];
      const itemCollection = item.collection as CollectionName;
      await runTransaction(db, async tx => {
        const current = await tx.get(item.ref);
        if (item.collection === "settings") {
          const local = extraSync.fromDoc("settings", item.id, item.data);
          const remote = current.exists() ? extraSync.fromDoc("settings", item.id, current.data()) : null;
          if (extraSync.pickWinner("settings", local, remote) === "local") tx.set(item.ref, item.data);
        } else {
          const local = reference.fromDoc(itemCollection, item.id, item.data);
          const remote = current.exists() ? reference.fromDoc(itemCollection, item.id, current.data()) : null;
          if (reference.pickWinner(itemCollection, local, remote) === "local") tx.set(item.ref, item.data);
        }
      });
    }
    if (leaseLost) continue;
    const migratedAtMillis = Number(snapshot?.updatedAtMillis ?? nowMillis);
    const finished = await runTransaction(db, async tx => {
      const current = await tx.get(meta);
      if (current.data()?.schemaVersion === 2)
        return Number(current.data()?.migratedAtMillis ?? migratedAtMillis);
      if (current.data()?.state !== "migrating" || current.data()?.by !== worker) return null;
      tx.set(meta, { schemaVersion: 2, migratedAtMillis, migratedBy: worker });
      return migratedAtMillis;
    });
    if (finished !== null) return finished;
  }
}

export class CloudStore {
  readonly db: Firestore;
  readonly uid: string;
  private listeners: Unsubscribe[] = [];
  private data = emptyData();
  private subscriber: ((state: DataState) => void) | null = null;
  private migratedAtMillis = 0;
  constructor(db: Firestore, uid: string) {
    this.db = db;
    this.uid = uid;
  }
  private root() {
    return doc(this.db, "users", this.uid);
  }
  private emit() {
    this.subscriber?.({ ...this.data });
  }
  private fail(error: unknown) {
    this.data.sync = "Lỗi";
    this.data.error = error instanceof Error ? error.message : String(error);
    this.emit();
  }
  async start(subscriber: (state: DataState) => void): Promise<void> {
    this.subscriber = subscriber;
    this.emit();
    try {
      this.migratedAtMillis = await ensureV2(this.db, this.uid);
      for (const name of collections) {
        const ref = collection(this.root(), name);
        const source =
          name === "sessions"
            ? query(
                ref,
                where("timestampMillis", ">=", Date.now() - 400 * 86400000),
              )
            : ref;
        this.listeners.push(
          onSnapshot(
            source,
            (snapshot) => {
              this.data[name] = snapshot.docs
                .map((item) => reference.fromDoc(name, item.id, item.data()))
                .filter((item): item is Parsed => !!item && !item.deleted);
              this.data.sync = snapshot.metadata.hasPendingWrites
                ? "Đang đồng bộ"
                : "Đã đồng bộ";
              this.data.error = "";
              this.emit();
            },
            (error) => this.fail(error),
          ),
        );
      }
      this.listeners.push(
        onSnapshot(
          doc(this.root(), "sync", "settings"),
          (snapshot) => {
            this.data.settings = snapshot.exists()
              ? (extraSync.fromDoc("settings", "settings", snapshot.data())
                  ?.entity ?? null)
              : null;
            this.emit();
          },
          (error) => this.fail(error),
        ),
      );
      for (const name of ["dayLogs", "learning"] as const) {
        this.listeners.push(
          onSnapshot(
            collection(this.root(), name),
            (snapshot) => {
              this.data[name] = snapshot.docs
                .map(
                  (item) =>
                    extraSync.fromDoc(
                      name,
                      item.id,
                      item.data(),
                    ) as Parsed | null,
                )
                .filter((item): item is Parsed => !!item && !item.deleted);
              this.emit();
            },
            (error) => this.fail(error),
          ),
        );
      }
      this.listeners.push(
        onSnapshot(
          doc(this.root(), "backups", "current"),
          (snapshot) => {
            this.data.warning =
              Number(snapshot.data()?.updatedAtMillis ?? 0) >
              this.migratedAtMillis;
            this.emit();
          },
          (error) => this.fail(error),
        ),
      );
    } catch (error) {
      this.fail(error);
    }
  }
  stop() {
    for (const listener of this.listeners) listener();
    this.listeners = [];
    this.subscriber = null;
  }
  async retry() {
    const subscriber = this.subscriber;
    this.stop();
    if (subscriber) await this.start(subscriber);
  }

  /** Transactions always parse the remote item and use the reference's winner policy. */
  async write(
    name: CollectionName,
    entity: Entity,
    deleted = false,
  ): Promise<void> {
    const candidate = reference.toDoc(name, entity, {
      nowMillis: Date.now(),
      deviceId: deviceId(),
      deleted,
    });
    if (!candidate)
      throw new Error(
        "Dữ liệu không hợp lệ. Vui lòng kiểm tra tên và thời gian.",
      );
    const item = doc(this.root(), name, candidate.id);
    this.data.sync = "Đang đồng bộ";
    this.emit();
    try {
      await runTransaction(this.db, async (tx) => {
        const snapshot = await tx.get(item);
        const remote = snapshot.exists()
          ? reference.fromDoc(name, candidate.id, snapshot.data())
          : null;
        const local = reference.fromDoc(name, candidate.id, candidate.data);
        if (reference.pickWinner(name, local, remote) === "local")
          tx.set(item, candidate.data);
      });
      this.data.sync = "Đã đồng bộ";
      this.emit();
    } catch (error) {
      this.fail(error);
      throw error;
    }
  }
  async writeExtra(
    name: ExtraCollection,
    entity: Entity,
    deleted = false,
  ): Promise<void> {
    const candidate = extraSync.toDoc(name, entity, {
      nowMillis: Date.now(),
      deviceId: deviceId(),
      deleted,
    });
    if (!candidate)
      throw new Error("Dữ liệu không hợp lệ. Kiểm tra tên, ngày và thời gian.");
    const item =
      name === "settings"
        ? doc(this.root(), "sync", "settings")
        : doc(this.root(), name, candidate.id);
    this.data.sync = "Đang đồng bộ";
    this.emit();
    try {
      await runTransaction(this.db, async (tx) => {
        const snapshot = await tx.get(item);
        const remote = snapshot.exists()
          ? extraSync.fromDoc(name, candidate.id, snapshot.data())
          : null;
        const local = extraSync.fromDoc(name, candidate.id, candidate.data);
        if (extraSync.pickWinner(name, local, remote) === "local")
          tx.set(item, candidate.data);
      });
      this.data.sync = "Đã đồng bộ";
      this.emit();
    } catch (error) {
      this.fail(error);
      throw error;
    }
  }
  async importGuestLearning(entity: Entity): Promise<void> {
    const candidate = extraSync.toDoc("learning", entity, {
      nowMillis: Date.now(), deviceId: deviceId(),
    });
    if (!candidate) throw new Error("Tiến bộ trên máy không hợp lệ.");
    const item = doc(this.root(), "learning", candidate.id);
    await runTransaction(this.db, async tx => {
      const remote = await tx.get(item);
      if (remote.exists()) throw new Error("Bé đã có tiến bộ đồng bộ; không ghi đè.");
      tx.set(item, candidate.data);
    });
  }
  async reimport(): Promise<void> {
    const backup = await getDoc(doc(this.root(), "backups", "current"));
    if (backup.data()?.schemaVersion !== 1) return;
    const imported = reference.migrateV1(backup.data() as Entity, {
      nowMillis: Date.now(),
      deviceId: deviceId(),
    });
    for (const item of imported.docs)
      await this.write(item.collection, item.data);
    if (imported.settings) {
      const candidate = extraSync.toDoc("settings", imported.settings, {
        nowMillis: Number(imported.settings.updatedAtMillis),
        deviceId: String(imported.settings.deviceId),
      });
      if (!candidate) throw new Error("Cài đặt nhập lại không hợp lệ.");
      const settingsRef = doc(this.root(), "sync", "settings");
      await runTransaction(this.db, async (tx) => {
        const current = await tx.get(settingsRef);
        const remote = current.exists()
          ? extraSync.fromDoc("settings", "settings", current.data())
          : null;
        const local = extraSync.fromDoc("settings", "settings", candidate.data);
        if (extraSync.pickWinner("settings", local, remote) === "local")
          tx.set(settingsRef, candidate.data);
      });
    }
    this.migratedAtMillis = Number(backup.data()?.updatedAtMillis ?? 0);
    await runTransaction(this.db, async (tx) => {
      const meta = doc(this.root(), "sync", "meta");
      await tx.get(meta);
      tx.set(meta, {
        schemaVersion: 2,
        migratedAtMillis: this.migratedAtMillis,
        migratedBy: deviceId(),
      });
    });
    this.data.warning = false;
    this.emit();
  }
}

export type AppStore = Pick<CloudStore,
  "write" | "writeExtra" | "retry" | "reimport" | "importGuestLearning">;
