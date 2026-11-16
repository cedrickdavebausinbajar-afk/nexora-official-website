# NEXORA FIT — Android Studio project

This isolated Android project packages NEXORA FIT inside the APK. Work on this branch is limited to the Android app; the public website and its default branch are not changed by Android source updates.

## App identity
- Application ID: `com.nexora.fit`
- Current source version: `1.3.25` (versionCode 59)
- Minimum Android: 6.0 (API 23)
- App content: bundled from `app/src/main/assets/fit/index.html`; the app shell does not need to load from the hosted website.
- The NEXORA logo, launcher icon, splash screen, and loading/launch styling are bundled with the Android app.
- The in-app update center reads `android-studio-project/version.json` from this isolated branch.
- Remote images and web fonts still require an internet connection.

## Android integration
- WebView JavaScript and DOM storage enabled for the existing app.
- Native file picker wired to HTML file inputs (JSON backup restore and food-image selection).
- Native update center checks release metadata, downloads the APK, verifies its package ID and signing certificate against the installed app, then hands installation to Android.
- Back navigation, WebView pause/resume, and saved navigation state.
- External HTTP(S) links are handed to the device browser; the canonical NEXORA GitHub Pages domain stays inside the app.

## Build
Open the `android-studio-project` folder in Android Studio, allow Gradle and SDK setup, then use **Build → Build Bundle(s) / APK(s)**. The signed release workflow is manually dispatched for a new version/build; its inputs must be higher than the current metadata version/build.

## Important release checks
- A successful build is not the same as a complete device QA pass. Test file selection, backup restore, photo preview, local workout history, rotation, updater download/install, and external links on a real Android device.
- Installing over an existing app requires the same application ID and signing certificate. The release workflow checks the certificate before publishing; never bypass that check.
- Existing data saved by the hosted website/browser is not automatically imported into the app's local WebView storage. Back up local workout data before testing an update.
- Lite/Pro subscription checkout, 3-day trial enrollment, recurring payment processing, server-side subscription records, webhooks, and cancellation portal are not implemented in this Android project. Do not advertise paid billing as active until end-to-end sandbox and live tests pass.
- Source branding and updater changes are not proof of a tested APK. Verify the published artifact and perform real-device QA before calling a release complete.
