# NEXORA ADMIN error monitoring client setup

The app now contains automatic browser/WebView error capture. It sends uncaught JavaScript errors and unhandled promise rejections to the NEXORA ADMIN callable function `reportClientError`. Reports are rate-limited client-side and server-side.

## Firebase configuration required

The monitoring Firebase project is `nexora-admin-6313f`. Register a **Web App** in that project and enable Firebase App Check for that Web App using reCAPTCHA v3. Add the website domain `cedrickdavebausinbajar-afk.github.io` to the reCAPTCHA allowed domains.

For the NEXORA AI DRAMA Android Capacitor build, set the same public Firebase Web App settings in the GitHub Actions repository secrets:
- `VITE_NEXORA_FIREBASE_API_KEY`
- `VITE_NEXORA_FIREBASE_AUTH_DOMAIN`
- `VITE_NEXORA_FIREBASE_PROJECT_ID` (must be `nexora-admin-6313f`)
- `VITE_NEXORA_FIREBASE_APP_ID` (the Web App ID)
- `VITE_NEXORA_APPCHECK_RECAPTCHA_SITE_KEY`

Set these as build-time environment values for local builds too. Never add service-account JSON, private keys, or Firebase Admin credentials to this repository.

## Android attestation limitation

The current Capacitor integration identifies Android reports from the native runtime but obtains App Check through the registered Firebase Web App's reCAPTCHA v3 provider in its WebView. This is not Play Integrity attestation. A production-hardening follow-up should register the Android package in Firebase and add a native Play Integrity App Check provider/plugin. Until the Web App and App Check registrations are configured, reporting remains disabled and the server will reject un-attested requests.

## Verify after configuration

1. Deploy the NEXORA ADMIN Functions containing `reportClientError`.
2. Configure App Check and the environment values above.
3. Build and install the Android APK; open the website.
4. In a controlled test build, trigger an uncaught test error on each client.
5. Confirm Error Center shows one report with source `android` and one with source `website`.

No error report is sent when the Firebase settings are blank. The client deliberately does not collect page contents, form values, authentication tokens, or network request bodies.
