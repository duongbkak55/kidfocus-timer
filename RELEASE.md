# Release Process

This document explains how to set up signed release builds via GitHub Actions.

## 1. Generate a Keystore

Run the following command on your local machine to create a new keystore file:

```bash
keytool -genkey -v -keystore kidfocus.jks -alias kidfocus -keyalg RSA -keysize 2048 -validity 10000
```

You will be prompted for:
- A keystore password (`SIGNING_STORE_PASSWORD`)
- A key password (`SIGNING_KEY_PASSWORD`)
- Distinguished name fields (name, org, country, etc.)

Keep the generated `kidfocus.jks` file safe. Do **not** commit it to the repository.

## 2. Encode the Keystore for GitHub Secrets

Base64-encode the keystore file so it can be stored as a GitHub secret:

```bash
base64 -i kidfocus.jks | tr -d '\n'
```

Copy the entire output — this becomes the value for `SIGNING_KEY_BASE64`.

## 3. Add Secrets to GitHub Repository Settings

Navigate to your repository on GitHub:
**Settings > Secrets and variables > Actions > New repository secret**

Add the following four secrets:

| Secret name              | Value                                              |
|--------------------------|----------------------------------------------------|
| `SIGNING_KEY_BASE64`     | Base64-encoded content of `kidfocus.jks` (step 2) |
| `SIGNING_KEY_ALIAS`      | The alias used when generating the keystore (e.g. `kidfocus`) |
| `SIGNING_STORE_PASSWORD` | The keystore password chosen during `keytool`      |
| `SIGNING_KEY_PASSWORD`   | The key password chosen during `keytool`           |

## 4. Triggering a Release Build

The workflow runs automatically on every push to the `main` branch, or manually via:

**Actions > Build Release APK > Run workflow**

The signed APK is uploaded as the `kidfocus-release` artifact and is retained for 30 days.
