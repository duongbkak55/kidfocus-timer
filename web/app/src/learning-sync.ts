import { initializeApp, type FirebaseOptions } from "firebase/app";
import {
  connectAuthEmulator,
  getAuth,
  onAuthStateChanged,
} from "firebase/auth";
import {
  connectFirestoreEmulator,
  doc,
  getDoc,
  initializeFirestore,
  memoryLocalCache,
  onSnapshot,
  runTransaction,
  type Unsubscribe,
} from "firebase/firestore";
import * as extraSync from "./extra-sync.mjs";

type Store = { stats: object; review: unknown[] };
declare global {
  interface Window {
    TuDuyUI: {
      setCloudMode: (onChanged: (store: Store) => void) => void;
      applyStore: (store: Store) => boolean;
      useLocalStore: () => void;
    };
    KidFocusTuDuySetBand?: (band: string) => void;
  }
}

const status = document.getElementById("webSyncStatus");
const say = (message: string) => {
  if (status) status.textContent = message;
};
const profileId =
  new URLSearchParams(location.search).get("profile") || "default";
const validProfile = profileId.length <= 64 && !profileId.includes("/");
const deviceKey = "kidfocus-web-device-id";
let deviceId = localStorage.getItem(deviceKey);
if (!deviceId) {
  deviceId = crypto.randomUUID();
  localStorage.setItem(deviceKey, deviceId);
}

async function loadLearningFirebase() {
  const response = await fetch("/app/firebase-config.json", {
    cache: "no-store",
  });
  if (!response.ok) throw new Error("Firebase web chưa có cấu hình.");
  const options = (await response.json()) as FirebaseOptions;
  if (
    !options.apiKey ||
    !options.projectId ||
    !options.appId ||
    options.apiKey === "PUBLIC_WEB_API_KEY"
  )
    throw new Error("Firebase web chưa có cấu hình.");
  const app = initializeApp(options);
  const auth = getAuth(app);
  const db = initializeFirestore(app, { localCache: memoryLocalCache() });
  if (
    ["localhost", "127.0.0.1"].includes(location.hostname) &&
    new URLSearchParams(location.search).has("emulator")
  ) {
    connectAuthEmulator(auth, "http://127.0.0.1:9099", {
      disableWarnings: true,
    });
    connectFirestoreEmulator(db, "127.0.0.1", 8080);
  }
  return { auth, db };
}

if (!validProfile) say("Hồ sơ không hợp lệ.");
else
  loadLearningFirebase()
    .then(({ auth, db }) => {
      let unsubscribe: Unsubscribe | null = null;
      let generation = 0;
      onAuthStateChanged(auth, async (user) => {
        unsubscribe?.();
        unsubscribe = null;
        const current = ++generation;
        if (!user) {
          window.TuDuyUI.useLocalStore();
          say("Chơi thử trên thiết bị này. Đăng nhập tại /app/ để đồng bộ.");
          return;
        }
        try {
          const root = doc(db, "users", user.uid);
          const profile = await getDoc(doc(root, "profiles", profileId));
          if (current !== generation) return;
          if (!profile.exists() || profile.data()?.archived) {
            window.TuDuyUI.useLocalStore();
            say("Hồ sơ bé không còn hoạt động.");
            return;
          }
          const ageBand = String(profile.data()?.ageBand ?? "5-6");
          if (["5-6", "l1", "l2", "l3"].includes(ageBand))
            window.KidFocusTuDuySetBand?.(ageBand === "5-6" ? "5-6" : "l1");
          const ref = doc(root, "learning", `${profileId}_tuduy`);
          let applied: ReturnType<typeof extraSync.fromDoc> = null;
          window.TuDuyUI.setCloudMode(async (store) => {
            if (current !== generation) return;
            const candidate = extraSync.toDoc(
              "learning",
              { profileId, stats: store.stats, review: store.review },
              { nowMillis: Date.now(), deviceId },
            );
            if (!candidate) {
              say("Không lưu được tiến bộ.");
              return;
            }
            say("Đang đồng bộ tiến bộ…");
            try {
              const winner = await runTransaction(db, async (tx) => {
                const snapshot = await tx.get(ref);
                const remote = snapshot.exists()
                  ? extraSync.fromDoc("learning", ref.id, snapshot.data())
                  : null;
                const local = extraSync.fromDoc(
                  "learning",
                  ref.id,
                  candidate.data,
                );
                if (
                  extraSync.pickWinner("learning", local, remote) === "local"
                ) {
                  tx.set(ref, candidate.data);
                  return local;
                }
                return remote;
              });
              if (current !== generation) return;
              if (
                winner &&
                extraSync.pickWinner("learning", winner, applied) === "local"
              ) {
                applied = winner;
                window.TuDuyUI.applyStore(winner.entity as Store);
              }
              say("Đã đồng bộ tiến bộ.");
            } catch {
              if (current === generation)
                say("Chưa đồng bộ được. Hãy thử lại khi có mạng.");
            }
          });
          unsubscribe = onSnapshot(
            ref,
            (snapshot) => {
              if (current !== generation) return;
              const remote = snapshot.exists()
                ? extraSync.fromDoc("learning", ref.id, snapshot.data())
                : null;
              if (
                remote &&
                extraSync.pickWinner("learning", remote, applied) === "local"
              ) {
                applied = remote;
                window.TuDuyUI.applyStore(remote.entity as Store);
              } else if (!remote && !applied) {
                window.TuDuyUI.applyStore({ stats: {}, review: [] });
              }
              say("Đã đồng bộ tiến bộ.");
            },
            () => {
              if (current === generation) say("Mất kết nối đồng bộ.");
            },
          );
        } catch {
          if (current === generation)
            say("Chưa mở được đồng bộ; thử lại khi có mạng.");
        }
      });
    })
    .catch(() => say("Chơi thử trên thiết bị này; chưa có cấu hình đồng bộ."));
