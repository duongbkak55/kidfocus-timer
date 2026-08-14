# KidFocus Learning Hub

## Architecture

The canonical, cross-platform learning content lives in `shared/learning/www`. Android packages
that directory as app assets and serves it from `https://appassets.androidplatform.net` with
`WebViewAssetLoader`. Do not copy the generated assets into `app/src/main/assets`; having one
source prevents Android, iOS, and the standalone PWA from drifting apart.

The embedded page never needs network access. External navigation, file access, content access,
cookies, mixed content, and JavaScript popups are disabled by the Android host. The service worker
is registered only in standalone PWA mode.

## Native bridge

The page sends JSON through `window.KidFocusNative.postMessage(...)`:

- `ready`: content is ready to receive native configuration.
- `screenChanged`: the native shell can implement predictable Back behavior.
- `profileChanged`: persists the selected age/grade in DataStore and cloud settings.
- `learningResult`: stores game, age band, score, question count, and duration.
- `speak`, `stopSpeech`, `installVoice`: use the platform TTS engine.

The native shell calls `window.KidFocusHost.applyConfig(...)` and
`window.KidFocusHost.handleBack()`. The contract deliberately avoids Android-specific types so the
same content can be hosted by `WKWebView` on iOS.

## Persistence and sync

Room database version 4 adds `learning_attempts`. Attempts use UUID primary keys so retry uploads
are idempotent. Anonymous attempts stay local. On the first authenticated session, unowned local
attempts are assigned to that account and uploaded to:

`users/{uid}/learningAttempts/{attemptId}`

Attempts are kept separate from `users/{uid}/backups/current`, avoiding Firestore's per-document
size limit. Existing Firestore rules already restrict the entire user subtree to its owner.

## Store release work

- Android currently builds and tests with the locally installed API 34 SDK. Before a Play release,
  install API 36, update AGP/Kotlin as one reviewed toolchain change, set `compileSdk` and
  `targetSdk` to 36, then rerun unit, lint, instrumentation, and Pixel/tablet tests.
- KidFocusTimer currently has no iOS application target. Create the full iOS host with Xcode 26 and
  iOS 26 SDK, implement this bridge with `WKScriptMessageHandler`, and reuse
  `shared/learning/www`. Authentication, Firestore, RevenueCat, parental gate, timer, routines, and
  notifications must be ported as native iOS features; the Learning Hub itself does not need a
  rewrite.
- If distributing in Apple's Kids Category, subscription and external-link entry points must remain
  behind the existing parental gate, and child data collection/third-party SDK use must be declared
  and reviewed.
