(() => {
  'use strict';

  if (typeof window === 'undefined' || window.__NEXORA_MONITORING_INSTALLED) return;
  window.__NEXORA_MONITORING_INSTALLED = true;

  const config = window.NEXORA_ERROR_MONITORING || {};
  const queue = [];
  const MAX_QUEUE = 10;
  const REPORT_INTERVAL_MS = 15000;
  let submitError = null;
  let lastSentAt = 0;
  let initializationPromise = null;

  function clean(value, maxLength) {
    return String(value ?? '')
      .replace(/Bearer\\s+[^\\s]+/gi, 'Bearer [REDACTED]')
      .replace(/((?:api[_-]?key|access[_-]?token|refresh[_-]?token|password|secret)=)[^&\\s]+/gi, '$1[REDACTED]')
      .replace(/[\\r\\n\\t]/g, ' ')
      .slice(0, maxLength);
  }

  function bounded(promise, timeoutMs, label) {
    let timer;
    return Promise.race([
      promise,
      new Promise((_, reject) => {
        timer = window.setTimeout(() => reject(new Error(label + '_timeout')), timeoutMs);
      })
    ]).finally(() => window.clearTimeout(timer));
  }

  function buildPayload(error, title) {
    const value = error instanceof Error ? error : new Error(clean(error, 2000));
    return {
      source: 'website',
      severity: 'error',
      title: clean(title || value.name || 'Website error', 120),
      message: clean(value.message || value, 2000),
      stack: clean(value.stack || '', 4000),
      version: clean(config.version || 'unknown', 60),
      location: clean(window.location.pathname, 200),
      device: clean(navigator.userAgent, 120),
      occurredAt: new Date().toISOString()
    };
  }

  async function send(payload, force = false) {
    if (!submitError) {
      if (queue.length < MAX_QUEUE) queue.push(payload);
      return false;
    }
    const now = Date.now();
    if (!force && now - lastSentAt < REPORT_INTERVAL_MS) return false;
    lastSentAt = now;
    try {
      await bounded(submitError(payload), 12000, 'firebase_report');
      return true;
    } catch (error) {
      console.warn('[NEXORA monitoring] Firebase report failed:', clean(error?.message || error, 240));
      return false;
    }
  }

  function report(error, title) {
    void send(buildPayload(error, title));
  }

  async function initializeMonitoring() {
    const required = ['apiKey', 'authDomain', 'projectId', 'appId', 'appCheckSiteKey'];
    if (!required.every(key => typeof config[key] === 'string' && config[key].trim())) {
      console.warn('[NEXORA monitoring] Firebase reporting is disabled: required public web configuration is missing.');
      return false;
    }

    try {
      const modules = await bounded(Promise.all([
        import('https://www.gstatic.com/firebasejs/11.10.0/firebase-app.js'),
        import('https://www.gstatic.com/firebasejs/11.10.0/firebase-app-check.js'),
        import('https://www.gstatic.com/firebasejs/11.10.0/firebase-functions.js')
      ]), 12000, 'firebase_sdk_load');

      const [appSdk, appCheckSdk, functionsSdk] = modules;
      const appName = 'nexora-official-site-monitor';
      const existing = appSdk.getApps().find(item => item.name === appName);
      const app = existing || appSdk.initializeApp({
        apiKey: config.apiKey,
        authDomain: config.authDomain,
        projectId: config.projectId,
        appId: config.appId
      }, appName);

      const appCheck = appCheckSdk.initializeAppCheck(app, {
        provider: new appCheckSdk.ReCaptchaV3Provider(config.appCheckSiteKey),
        isTokenAutoRefreshEnabled: true
      });
      const functions = functionsSdk.getFunctions(app, 'us-central1');
      const callable = functionsSdk.httpsCallable(functions, 'reportClientError', {timeout: 10000});

      submitError = async payload => {
        await bounded(appCheckSdk.getToken(appCheck, false), 8000, 'app_check_token');
        return callable(payload);
      };

      if (queue.length) {
        const first = queue.shift();
        queue.length = 0;
        await send(first, true);
      }
      console.info('[NEXORA monitoring] Firebase error reporting initialized.');
      return true;
    } catch (error) {
      console.warn('[NEXORA monitoring] Initialization failed; browser diagnostics remain active:', clean(error?.message || error, 300));
      return false;
    }
  }

  window.addEventListener('error', event => {
    report(event.error || event.message, 'Uncaught website JavaScript error');
  });
  window.addEventListener('unhandledrejection', event => {
    report(event.reason, 'Unhandled website promise rejection');
  });

  initializationPromise = initializeMonitoring();
  window.NEXORA_MONITORING_READY = initializationPromise;
})();
