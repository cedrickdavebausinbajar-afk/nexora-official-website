# NEXORA website browser error monitoring

The official website includes a small browser-side error monitor in `nexora-error-monitoring.js`. It listens for uncaught JavaScript errors and unhandled promise rejections and records a limited diagnostic event in Firebase Analytics when the website Firebase Web App is configured. If that configuration is absent or Firebase Analytics cannot initialize, it falls back to the site's GA4 event queue.

This is **analytics-based diagnostics**, not a secure server-side error inbox or a callable Firebase Functions endpoint. It does not authenticate users, inspect form contents, or submit application data.

## Optional Firebase Analytics setup

The GitHub Pages deployment workflow reads these repository Actions secrets and generates `nexora-monitoring-config.js` during deployment:

- `NEXORA_FIREBASE_API_KEY`
- `NEXORA_FIREBASE_AUTH_DOMAIN`
- `NEXORA_FIREBASE_PROJECT_ID`
- `NEXORA_FIREBASE_APP_ID`
- `NEXORA_FIREBASE_MEASUREMENT_ID`

Use a dedicated Firebase Web App for the NEXORA official website. Firebase Web configuration values, including the web API key, are client-side identifiers and are not service-account credentials; protect the Firebase project with appropriate authorized domains, API restrictions where supported, and security rules for any other Firebase products.

Never add service-account JSON, private keys, Admin SDK credentials, or private tokens to this public repository or to client-side JavaScript.

If these five values are not configured, the workflow intentionally leaves Firebase Analytics initialization disabled. The GA4 fallback remains available on pages where the GA4 tag is loaded.

## What the monitor records

- Short error description and error name
- A truncated stack trace
- The current page pathname (not the query string)
- A website version label

The monitor redacts common bearer and token-like values, limits payload sizes, caps the initialization queue, and rate-limits repeated reports. Browser analytics can be blocked by consent settings, tracking protection, network failures, or ad blockers, so it is not guaranteed crash reporting.

## Verification

1. Confirm the deployment workflow completes successfully.
2. Open the official website and inspect the browser console for the monitoring initialization message.
3. In a controlled test environment only, trigger a test JavaScript error.
4. Check the GA4/Firebase Analytics DebugView or events report for an `exception` event.
5. Repeat with Firebase configuration blank to verify that the GA4 fallback path is used.

Do not deliberately trigger test errors for normal visitors.
