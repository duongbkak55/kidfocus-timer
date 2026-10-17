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
  where,
  writeBatch,
  type Firestore,
  type Unsubscribe,
} from "firebase/firestore";
import {
  reference,
  type CollectionName,
  type Entity,
  type Parsed,
} from "./model";

export type SyncState = "Đang đồng bộ" | "Đã đồng bộ" | "Lỗi";
export type DataState = { [K in CollectionName]: Parsed[] } & {
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
export const logout = (auth: Auth) => signOut(auth);
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
  const existing = await runTransaction(db, (tx) => tx.get(meta));
  if (existing.data()?.schemaVersion === 2)
    return Number(existing.data()?.migratedAtMillis ?? 0);
  const backup = await getDoc(doc(root, "backups", "current"));
  const snapshot = backup.data();
  const nowMillis = Date.now();
  const migrated =
    snapshot?.schemaVersion === 1
      ? reference.migrateV1(snapshot, { nowMillis, deviceId: deviceId() })
      : null;
  const writes = [
    ...(migrated?.docs ?? []).map(
      (item) => [doc(root, item.collection, item.id), item.data] as const,
    ),
  ];
  if (migrated?.settings)
    writes.push([doc(root, "sync", "settings"), migrated.settings]);
  for (let i = 0; i < writes.length; i += 450) {
    const batch = writeBatch(db);
    for (const [ref, data] of writes.slice(i, i + 450)) batch.set(ref, data);
    await batch.commit();
  }
  const migratedAtMillis = Number(snapshot?.updatedAtMillis ?? nowMillis);
  await runTransaction(db, async (tx) => {
    const latest = await tx.get(meta);
    if (latest.data()?.schemaVersion !== 2)
      tx.set(meta, {
        schemaVersion: 2,
        migratedAtMillis,
        migratedBy: deviceId(),
      });
  });
  return migratedAtMillis;
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
      const settingsRef = doc(this.root(), "sync", "settings");
      await runTransaction(this.db, async (tx) => {
        const current = await tx.get(settingsRef);
        const remote = current.data();
        const next = imported.settings!;
        if (
          !remote ||
          Number(next.updatedAtMillis) > Number(remote.updatedAtMillis ?? 0) ||
          (Number(next.updatedAtMillis) === Number(remote.updatedAtMillis) &&
            String(next.deviceId) > String(remote.deviceId ?? ""))
        )
          tx.set(settingsRef, next);
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
