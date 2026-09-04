/**
 * Frozen-ribbon watchdog (AUDIT.md C1).
 *
 * A broadcast chyron that runs for days must notice when its own scroll
 * loop has stalled (WebView compositor pause after sleep/resume, a dropped
 * animation frame, an aggressive memory reclaim) and restart it.
 *
 * Strategy: sample the ticker's scroll offset every SAMPLES_MS. If it has
 * not moved across two consecutive samples while the page is visible and the
 * loop should be running, fire onStall (the app restarts the loop and shows
 * a quiet "restarted ribbon" notice). Also kicks the loop on
 * visibilitychange → visible and on pageshow, which is what a TV WebView
 * delivers after resume.
 */

const SAMPLE_MS = 5000;
const STALL_SAMPLES = 2;

export function startWatchdog({ getProgress, isRunning, onStall, onWake }) {
  let lastProgress = null;
  let unchanged = 0;

  const sample = () => {
    const running = isRunning ? isRunning() : true;
    const hidden = typeof document !== 'undefined' && document.hidden;
    if (!running || hidden) {
      lastProgress = null;
      unchanged = 0;
      return;
    }
    const p = getProgress();
    if (lastProgress != null && Math.abs(p - lastProgress) < 0.01) {
      unchanged += 1;
      if (unchanged >= STALL_SAMPLES) {
        unchanged = 0;
        lastProgress = null;
        if (onStall) onStall();
        return;
      }
    } else {
      unchanged = 0;
    }
    lastProgress = p;
  };

  const timer = setInterval(sample, SAMPLE_MS);

  const wake = () => {
    lastProgress = null;
    unchanged = 0;
    if (onWake) onWake();
  };
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden) wake();
  });
  window.addEventListener('pageshow', wake);
  window.addEventListener('focus', wake);

  return () => {
    clearInterval(timer);
    document.removeEventListener('visibilitychange', wake);
    window.removeEventListener('pageshow', wake);
    window.removeEventListener('focus', wake);
  };
}
