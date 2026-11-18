import { initializeApp } from 'https://www.gstatic.com/firebasejs/11.10.0/firebase-app.js';
import { initializeAppCheck, ReCaptchaV3Provider, getToken } from 'https://www.gstatic.com/firebasejs/11.10.0/firebase-app-check.js';
import { getFunctions, httpsCallable } from 'https://www.gstatic.com/firebasejs/11.10.0/firebase-functions.js';

const config = window.NEXORA_ERROR_MONITORING || {};
let submitError = null;
let lastSentAt = 0;
const clean = (value, max) => String(value ?? '').replace(/[\r\n\t]/g, ' ').slice(0, max);

try {
  if (config.apiKey && config.authDomain && config.projectId && config.appId && config.appCheckSiteKey) {
    const app = initializeApp({
      apiKey: config.apiKey,
      authDomain: config.authDomain,
      projectId: config.projectId,
      appId: config.appId
    }, 'nexora-official-site-monitor');
    const appCheck = initializeAppCheck(app, {
      provider: new ReCaptchaV3Provider(config.appCheckSiteKey),
      isTokenAutoRefreshEnabled: true
    });
    const reportClientError = httpsCallable(getFunctions(app, 'us-central1'), 'reportClientError');
    submitError = async payload => {
      await getToken(appCheck, false);
      return reportClientError(payload);
    };
  } else {
    console.info('[NEXORA monitoring] Configure nexora-monitoring-config.js to enable reports.');
  }
} catch (error) {
  console.warn('[NEXORA monitoring] Initialization failed.', error);
}

function report(error, title) {
  if (!submitError || Date.now() - lastSentAt < 15000) return;
  lastSentAt = Date.now();
  const value = error instanceof Error ? error : new Error(clean(error, 2000));
  submitError({
    source: 'website',
    severity: 'error',
    title: clean(title || value.name || 'Website error', 120),
    message: clean(value.message || value, 2000),
    stack: clean(value.stack || '', 8000),
    version: clean(config.version || 'unknown', 60),
    location: clean(window.location.pathname, 300),
    device: clean(navigator.userAgent, 120)
  }).catch(err => console.warn('[NEXORA monitoring] Report submission failed.', err));
}

window.addEventListener('error', event => report(event.error || event.message, 'Uncaught website JavaScript error'));
window.addEventListener('unhandledrejection', event => report(event.reason, 'Unhandled website promise rejection'));
