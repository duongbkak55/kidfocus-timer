# AI gateway and subscriptions

The Android app never calls OpenRouter with a bundled secret. It calls the `aiChat` Firebase
callable function in `asia-southeast1`; the function reserves quota in a Firestore transaction,
calls OpenRouter, then completes or refunds the reservation.

## Deploy AI

Cloud Functions and Secret Manager require the Firebase project to use the Blaze plan. OpenRouter
credit does not cover Firebase infrastructure. After upgrading `kid-focus-app`:

```bash
printf '%s' "$OPENROUTER_API_KEY" | firebase functions:secrets:set OPENROUTER_API_KEY --data-file=-
firebase functions:secrets:set REVENUECAT_WEBHOOK_AUTH
firebase deploy --only functions,firestore:rules,remoteconfig
```

Remote Config is the global admin surface. The repository template publishes these defaults:

- `ai_enabled=true`
- 10 questions/day for guest and signed-in free users
- 60 credits/day for Premium
- 200 credits/day globally across all devices
- model IDs, per-question credit cost, and per-model daily limits in `ai_models_json`

The app already initializes Firebase App Check with Debug Provider in debug builds and Play
Integrity in release builds. Register both apps in Firebase App Check, add the debug token shown in
Logcat, then change `enforceAppCheck` to `true` in `functions/index.js` and deploy Functions again.

## Configure RevenueCat and Google Play

1. Create entitlement `premium`.
2. Create the Google Play subscription `kidfocus_premium` with base plans `monthly` and
   `annual`; recommended starting prices are 59,000 VND/month and 499,000 VND/year. RevenueCat
   product identifiers are `kidfocus_premium:monthly` and `kidfocus_premium:annual`. Attach them
   to the `$rc_monthly` and `$rc_annual` packages in the current `default` Offering.
3. Add the RevenueCat Android public SDK key to local/CI configuration:

   ```properties
   REVENUECAT_ANDROID_API_KEY=goog_...
   ```

   Local debug builds can use RevenueCat Test Store without a Play-installed package:

   ```properties
   REVENUECAT_TEST_API_KEY=test_...
   ```

   Release builds always use `REVENUECAT_ANDROID_API_KEY`; debug builds prefer
   `REVENUECAT_TEST_API_KEY` and fall back to the Android key when it is absent.

4. Upload Google Play service-account credentials to the RevenueCat Play Store app so RevenueCat
   can validate products and transactions, then configure Google developer notifications.
5. Configure RevenueCat's webhook URL with the deployed `revenueCatWebhook` URL. Its Authorization
   header must exactly match the `REVENUECAT_WEBHOOK_AUTH` Firebase secret.
6. Purchases use the signed-in Firebase UID as RevenueCat App User ID. The webhook writes the
   server-only `entitlements/{uid}` document used by the AI gateway.
7. Add license testers in Play Console and test monthly, annual, cancel, restore, expiration, and
   cross-device login before production.

For iOS, create equivalent monthly/annual products in App Store Connect and attach them to the same
RevenueCat entitlement. StoreKit—not Apple Pay directly—must unlock digital Premium features.
