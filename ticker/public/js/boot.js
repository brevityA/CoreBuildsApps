/*
 * Boot guard — a classic script, not a module, and deliberately so.
 *
 * This is the first thing the page runs and it must run on WebViews too old
 * for ES modules, which is the whole reason it exists: if `js/app.js` never
 * reports in — an ancient WebView, a broken asset, a module that threw during
 * evaluation — the timer below reveals the upgrade message in the markup
 * instead of leaving the viewer on a blank screen.
 *
 * It used to live inline in `index.html`. A `script-src 'self'` policy
 * forbids inline script, and the choice was between keeping the guard inline
 * and giving up the policy, or moving the guard out. Moving it out costs
 * nothing the guard cares about: everything that can run an inline classic
 * script can fetch and run an external one, and this file is served the same
 * way as the CSS and the app module beside it. Raising the inline-script
 * allowance to keep two `<script>` tags would have meant that any injected
 * inline handler anywhere in the app would also execute.
 */
window.CORELINE_NATIVE = window.CORELINE_NATIVE || false;
window.CORELINE_TV = window.CORELINE_TV || false;

window.__CORELINE_BOOT = { ready: false, t: 0 };
(function () {
  var timer = setTimeout(function () {
    if (!window.__CORELINE_BOOT.ready) {
      var d = document.getElementById('bootfallback');
      if (d) d.hidden = false;
    }
  }, 4000);
  window.__CORELINE_BOOT.t = timer;
})();
