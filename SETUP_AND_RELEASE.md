# Simran Tution Classes — Firebase & Signed Release APK Guide

## 1. Where to Configure the Authorized Teacher Gmail

To lock the app and Cloud Firestore exclusively to Ma'am's Google account:

1. **Client App Lock (`.env` or AI Studio Secrets Panel):**
   - Set `AUTHORIZED_TEACHER_EMAIL` in `.env` (or in the **Secrets** panel of AI Studio) to Ma'am's exact Gmail address:
     ```dotenv
     AUTHORIZED_TEACHER_EMAIL=maam.email@gmail.com
     ```
   - `TeacherAccessConfig.kt` reads `BuildConfig.AUTHORIZED_TEACHER_EMAIL` at build time and restricts app entry strictly to that verified Google account.

2. **Cloud Firestore Server Lock (`firestore.rules`):**
   - Open `firestore.rules` at the workspace root and update `configuredTeacherEmail()`:
     ```javascript
     function configuredTeacherEmail() {
       return "maam.email@gmail.com";
     }
     ```
   - Redeploy `firestore.rules` to your Firebase project (`firebase deploy --only firestore:rules` or via AI Studio).

---

## 2. Firebase Setup Summary

- **Package / Application ID:** `com.simrantutionclasses.app`
- **Firebase Authentication:** Enable **Google** sign-in provider in Firebase Console → Authentication → Sign-in method.
- **SHA-1 & SHA-256 Fingerprints:** Register both your debug keystore and your release/upload keystore SHA-1 and SHA-256 fingerprints in Firebase Console → Project Settings → Your Android App (`com.simrantutionclasses.app`) so Credential Manager Google Sign-In works in release builds.
- **Named Firestore Database:** Configured via `app/src/main/res/values/firebase_applet_config.xml` (`R.string.firestore_database_id`).
- **Config File:** Place your project's `app/google-services.json` in `app/` (never commit secrets or API keys in Kotlin source files).

---

## 3. Building a Signed Release APK (`SimranTutionClasses.apk`) in Android Studio

1. Open the project folder in **Android Studio**.
2. Wait for **Gradle Sync** to finish.
3. Verify your release signing keystore environment variables (or configure Android Studio's signing dialog):
   - Via Android Studio UI:
     1. Click **Build** → **Generate Signed App Bundle / APK...**
     2. Choose **APK** and click **Next**.
     3. Select **Create new...** (if you don't have a keystore yet) or **Choose existing...** for your `.jks` keystore, and enter your Key store password, Key alias, and Key password.
     4. Choose **release** build variant and click **Create**.
   - Or via command line using the existing `release` signing config in `app/build.gradle.kts`:
     ```bash
     export KEYSTORE_PATH=/path/to/your-release-key.jks
     export STORE_PASSWORD=your_store_password
     export KEY_PASSWORD=your_key_password
     gradle :app:assembleRelease
     ```
4. Locate the generated `SimranTutionClasses.apk` in `app/build/outputs/apk/release/` (generated automatically when running `assembleRelease`; if using Android Studio's Generate Signed APK wizard directly, rename `app-release.apk` to `SimranTutionClasses.apk`).
5. **Important:** Copy the SHA-1 and SHA-256 fingerprints of your release keystore (`keytool -list -v -keystore your-release-key.jks`) into **Firebase Console → Project Settings → Android App** and re-download `app/google-services.json` if needed before distributing the release APK.
