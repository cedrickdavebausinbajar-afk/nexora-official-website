# NEXORA FIT — Android Studio starter project

This isolated Android Studio project wraps the existing official NEXORA FIT website in an Android WebView.

## App identity
- Application ID: `com.nexora.fit`
- Version: `1.2.8` (versionCode 28)
- Minimum Android: 6.0 (API 23)
- Website loaded: https://cedrickdavebausinbajar-afk.github.io/nexora-official-website/fit/

## Open and build
1. Download this project folder or clone the branch containing it.
2. Open the `android-studio-project` folder in Android Studio.
3. Allow Android Studio to install the required Android SDK and Gradle components.
4. Use **Build → Build Bundle(s) / APK(s) → Build APK(s)**.

## Important limitations
- This is a starter Android wrapper, not a verified release APK.
- It loads the current hosted website; it does not add the requested Lite/Pro subscription checkout to that website.
- No payment provider, recurring billing, trial enrollment, server-side subscription records, webhook handling, or cancellation portal is connected.
- Do not publish this build as a paid subscription app until those systems are implemented and tested.
- The original website and existing APK release have not been changed by this branch.
- Although the application ID is set to `com.nexora.fit`, installing over an existing APK requires the exact same signing certificate. The signing key was not available for verification, so update compatibility is not confirmed.
