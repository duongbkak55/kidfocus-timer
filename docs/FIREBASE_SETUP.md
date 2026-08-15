# Firebase sync setup

KidFocus keeps working with Room/DataStore when Firebase is not configured. To enable
cross-device sync, create one Firebase project and use its free Spark plan.

## Firebase Console

1. Create a Firebase project.
2. Add an Android app for the build you are installing:
   - Debug on the Pixel: `com.kidfocusstudio.timer.debug`
   - Release/Google Play: `com.kidfocusstudio.timer`
3. Add the SHA-1 and SHA-256 certificate fingerprints for every signing source you use. For the
   Google Play build, use the fingerprints under **Play Console > App signing > App signing key**;
   the upload-key fingerprint alone is not sufficient.
4. In **Authentication > Sign-in method**, enable **Email/Password** and **Google**.
5. Create a **Cloud Firestore Standard** database.
6. Publish the rules from the repository's `firestore.rules` file. They restrict every backup to
   the matching authenticated user ID.

## Local development

Copy the three values from Firebase project/app settings into `local.properties` (never commit
this file):

```properties
FIREBASE_API_KEY=...
# Firebase app for com.kidfocusstudio.timer
FIREBASE_APP_ID=1:...:android:...
# Firebase app for com.kidfocusstudio.timer.debug (optional)
FIREBASE_DEBUG_APP_ID=1:...:android:...
FIREBASE_PROJECT_ID=...
FIREBASE_WEB_CLIENT_ID=...apps.googleusercontent.com
```

Then rebuild and reinstall the app. Open **Cài đặt phụ huynh > Đồng bộ nhiều thiết bị**, create an
account, and use that same email/password on the other device.

Google Sign-In uses the project's Web OAuth client ID and the Android app SHA-1. The Google
provider must be enabled in Firebase Authentication. Without any signed-in Firebase user, cloud
sync is disabled and the app remains local-only.

For CI/release builds, provide the same names as environment variables instead of
`local.properties`.

AI quota and Premium setup are documented in [`AI_AND_SUBSCRIPTIONS.md`](AI_AND_SUBSCRIPTIONS.md).
Firestore sync still works on Spark; deploying the AI gateway requires Blaze because Firebase
Cloud Functions needs Secret Manager.

## Data and conflict behavior

- Synced: up to 2,000 recent timer sessions, study schedules, daily routines, completion history,
  theme, focus/break lengths, daily goal, sound and vibration.
- Device-only: parental PIN, onboarding state and AI API key.
- Firestore stores one compact snapshot per account. The newest completed write wins if two
  devices change data concurrently.
- Firestore's Android disk cache is enabled by default. Local Room data remains available even
  when Firebase is unavailable.
