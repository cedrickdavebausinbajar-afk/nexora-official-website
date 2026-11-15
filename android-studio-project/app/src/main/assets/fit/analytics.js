(function () {
  'use strict';
  var measurementId = 'G-VYQZ6HT0RD';
  window.dataLayer = window.dataLayer || [];
  window.gtag = window.gtag || function () { window.dataLayer.push(arguments); };
  var tag = document.createElement('script');
  tag.async = true;
  tag.src = 'https://www.googletagmanager.com/gtag/js?id=' + measurementId;
  document.head.appendChild(tag);
  window.gtag('js', new Date());
  window.gtag('config', measurementId, {
    app_name: 'NEXORA FIT',
    app_platform: 'android'
  });
  window.gtag('event', 'app_open', {
    app_name: 'NEXORA FIT',
    app_platform: 'android'
  });
  try {
    if (!localStorage.getItem('nexora_fit_first_launch_v1')) {
      window.gtag('event', 'app_first_launch', {
        app_name: 'NEXORA FIT',
        app_platform: 'android'
      });
      localStorage.setItem('nexora_fit_first_launch_v1', '1');
    }
  } catch (_) {}
  document.addEventListener('click', function (event) {
    var tab = event.target.closest('.tab');
    if (tab) {
      window.gtag('event', 'app_tab_view', {
        app_name: 'NEXORA FIT',
        app_platform: 'android',
        tab_name: (tab.textContent || '').trim().slice(0, 60)
      });
    }
  });
})();
