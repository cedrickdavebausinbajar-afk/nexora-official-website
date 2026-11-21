(() => {
  'use strict';

  if (typeof window === 'undefined' || window.__NEXORA_MONITORING_INSTALLED) return;
  window.__NEXORA_MONITORING_INSTALLED = true;

  const config = window.NEXORA_ERROR_MONITORING || {};
  const queue = [];
  const MAX_QUEUE = 10;
  const REPORT_INTERVAL_MS = 15000;
  let logException = null;
  let firebaseInitializationFinished = false;
  let lastSentAt = 0;

  function clean(value, maxLength) {
    return String(value ?? '')
      .replace(/Bearer\s+[^\s]+/gi, 'Bearer [REDACTED]')
      .replace(/((?:api[_-]?key|access[_-]?token|refresh[_-]?token|id[_-]?token|password|secret|signature|auth)=)[^&\s]+/gi, '$1[REDACTED]')
      .replace(/([?&](?:key|token|sig)=)[^&\s]+/gi, '$1[REDACTED]')
      .replace(/[\r\n\t]/g, ' ')
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
    const value = error instanceof Error ? error : new Error(clean(error, 1000));
    return {
      description: clean((title || value.name || 'Client error') + ': ' + (value.message || value), 150),
      error_name: clean(value.name || 'Error', 40),
      page: clean(window.location.pathname, 100),
      version: clean(config.version || 'unknown', 40)
    };
  }

  function reportToGoogleAnalytics(payload) {
    try {
      if (typeof window.gtag !== 'function') return false;
      window.gtag('event', 'exception', {
        description: payload.description,
        fatal: false,
        error_name: payload.error_name,
        page_path: payload.page,
        app_version: payload.version
      });
      return true;
    } catch (error) {
      console.warn('[NEXORA monitoring] GA4 fallback event failed:', clean(error?.message || error, 180));
      return false;
    }
  }

  function flushFallbackQueue() {
    const pending = queue.splice(0, MAX_QUEUE);
    pending.forEach(reportToGoogleAnalytics);
  }

  function report(error, title) {
    const payload = buildPayload(error, title);
    const now = Date.now();
    if (now - lastSentAt < REPORT_INTERVAL_MS) return;
    lastSentAt = now;
    if (!logException) {
      if (!firebaseInitializationFinished && queue.length < MAX_QUEUE) queue.push(payload);
      else reportToGoogleAnalytics(payload);
      return;
    }
    try {
      logException(payload);
    } catch (sendError) {
      console.warn('[NEXORA monitoring] Firebase Analytics event failed:', clean(sendError?.message || sendError, 180));
      reportToGoogleAnalytics(payload);
    }
  }

  async function initializeMonitoring() {
    const required = ['apiKey', 'authDomain', 'projectId', 'appId', 'measurementId'];
    if (!required.every(key => typeof config[key] === 'string' && config[key].trim())) {
      firebaseInitializationFinished = true;
      flushFallbackQueue();
      console.warn('[NEXORA monitoring] Firebase Analytics SDK is disabled until website Firebase settings are added; GA4 exception reporting remains active.');
      return false;
    }

    try {
      const [appSdk, analyticsSdk] = await bounded(Promise.all([
        import('https://www.gstatic.com/firebasejs/11.10.0/firebase-app.js'),
        import('https://www.gstatic.com/firebasejs/11.10.0/firebase-analytics.js')
      ]), 10000, 'firebase_sdk_load');
      if (!await bounded(analyticsSdk.isSupported(), 5000, 'analytics_support_check')) {
        firebaseInitializationFinished = true;
        flushFallbackQueue();
        console.info('[NEXORA monitoring] Firebase Analytics is unsupported in this browser; GA4 exception reporting remains active.');
        return false;
      }

      const appName = 'nexora-official-website-monitor';
      const existing = appSdk.getApps().find(item => item.name === appName);
      const app = existing || appSdk.initializeApp({
        apiKey: config.apiKey,
        authDomain: config.authDomain,
        projectId: config.projectId,
        appId: config.appId,
        measurementId: config.measurementId
      }, appName);
      const analytics = analyticsSdk.getAnalytics(app);
      logException = payload => analyticsSdk.logEvent(analytics, 'exception', {
        description: payload.description,
        fatal: false,
        error_name: payload.error_name,
        page_path: payload.page,
        app_version: payload.version
      });

      const pending = queue.splice(0, MAX_QUEUE);
      pending.forEach(payload => {
        try {
          logException(payload);
        } catch (error) {
          reportToGoogleAnalytics(payload);
          console.warn('[NEXORA monitoring] Queued Firebase event failed:', clean(error?.message || error, 180));
        }
      });
      firebaseInitializationFinished = true;
      console.info('[NEXORA monitoring] Firebase Analytics error reporting initialized.');
      return true;
    } catch (error) {
      firebaseInitializationFinished = true;
      flushFallbackQueue();
      console.warn('[NEXORA monitoring] Firebase initialization failed; GA4 exception reporting remains active:', clean(error?.message || error, 240));
      return false;
    }
  }

  window.addEventListener('error', event => {
    report(event.error || event.message, 'Uncaught website JavaScript error');
  });
  window.addEventListener('unhandledrejection', event => {
    report(event.reason, 'Unhandled website promise rejection');
  });

  window.NEXORA_MONITORING_READY = initializeMonitoring();
})();
