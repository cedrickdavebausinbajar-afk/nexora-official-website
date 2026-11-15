# NEXORA FIT — Android Studio project

This isolated Android project packages the NEXORA FIT web app inside the APK. The original website and its default branch are not modified by this project.

## App identity
- Application ID: `com.nexora.fit`
- Version: `1.3.9` (versionCode 42)
- Minimum Android: 6.0 (API 23)
- App content: bundled from `app/src/main/assets/fit/index.html`; it does not need to load the app shell from the hosted website.
- The NEXORA SVG logo is bundled locally for the in-app header and favicon.
- Native launcher icon and splash screen use the official blue-and-cyan NEXORA mark.
- Remote images and web fonts still require an internet connection.

## Android integration
- WebView JavaScript and DOM storage enabled for the existing app.
- Native file picker wired to HTML file inputs (JSON backup restore and food-image selection).
- Back navigation, WebView pause/resume, and saved navigation state.
- Top-level external HTTP(S) links are handed to the device browser; the canonical NEXORA GitHub Pages domain stays inside the app. Subframe navigation is not redirected to external apps.
- The APK is built as debug and signed release artifacts by GitHub Actions.

## Build
Open the `android-studio-project` folder in Android Studio, allow Gradle and SDK setup, then use **Build → Build Bundle(s) / APK(s)**. Or run the repository GitHub Actions workflow.

## Important release checks
- A successful build is not the same as a complete device QA pass. Test file selection, backup restore, photo preview, local workout history, rotation, and external links on a real Android device.
- Installing over an existing app requires the same application ID and signing certificate. Compare its SHA-256 certificate fingerprint with the certificate of the APK already installed before updating.
- Existing data saved by the hosted website/browser is not automatically imported into the Android app's local WebView storage.
- Lite/Pro subscription checkout, 3-day trial enrollment, recurring payment processing, server-side subscription records, webhooks, and cancellation portal are not implemented in this Android project. Do not advertise paid billing as active until end-to-end sandbox and live tests pass.
- These branding changes are source changes only until an APK is built, signed, and tested.
