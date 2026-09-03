export function startWatchdog(ticker, { interval = 3000, onStall } = {}) {
  let lastOffset = -1;
  let timer = null;
  let running = true;

  function check() {
    if (!running) return;
    const current = ticker.offset;
    if (lastOffset >= 0 && current === lastOffset && ticker.speed > 0) {
      if (typeof onStall === 'function') onStall();
    }
    lastOffset = current;
  }

  timer = setInterval(check, interval);

  function onVisibility() {
    if (document.visibilityState === 'visible') {
      lastOffset = -1;
      ticker.measure();
    }
  }

  function onPageShow(e) {
    if (e.persisted) {
      lastOffset = -1;
      ticker.measure();
    }
  }

  function onFocus() {
    lastOffset = -1;
  }

  if (typeof document !== 'undefined') {
    document.addEventListener('visibilitychange', onVisibility);
    window.addEventListener('pageshow', onPageShow);
    window.addEventListener('focus', onFocus);
  }

  return function stop() {
    running = false;
    clearInterval(timer);
    if (typeof document !== 'undefined') {
      document.removeEventListener('visibilitychange', onVisibility);
      window.removeEventListener('pageshow', onPageShow);
      window.removeEventListener('focus', onFocus);
    }
  };
}
