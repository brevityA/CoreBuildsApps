export class Ticker {
  constructor(container, { speed = 80 } = {}) {
    this._container = container;
    this._trackA = container.querySelector('#crawlA') || container.children[0];
    this._trackB = container.querySelector('#crawlB') || container.children[1];
    this._speed = 80;
    this.speed = speed;
    this._offset = 0;
    this._raf = null;
    this._lastTime = 0;
    this._trackWidth = 0;
    this._running = false;
    this._observer = null;
  }

  get speed() { return this._speed; }
  set speed(v) { this._speed = Math.max(1, Number(v) || 80); }

  get offset() { return this._offset; }

  measure() {
    this._trackWidth = this._trackA ? this._trackA.offsetWidth : 0;
  }

  start() {
    if (this._running) return;
    this._running = true;
    this.measure();
    this._lastTime = 0;
    this._observer = typeof ResizeObserver !== 'undefined'
      ? new ResizeObserver(() => this.measure())
      : null;
    if (this._observer && this._trackA) this._observer.observe(this._trackA);
    this._tick(performance.now());
  }

  stop() {
    this._running = false;
    if (this._raf) cancelAnimationFrame(this._raf);
    this._raf = null;
    if (this._observer) this._observer.disconnect();
    this._observer = null;
  }

  reset() {
    this._offset = 0;
    this._apply();
  }

  _tick(now) {
    if (!this._running) return;
    if (this._lastTime > 0) {
      const dt = (now - this._lastTime) / 1000;
      if (dt > 0 && dt < 1) {
        this._offset += this._speed * dt;
      }
    }
    this._lastTime = now;

    if (this._trackWidth > 0 && this._offset >= this._trackWidth) {
      this._offset %= this._trackWidth;
    }

    this._apply();
    this._raf = requestAnimationFrame((t) => this._tick(t));
  }

  _apply() {
    if (this._container) {
      this._container.style.transform = `translate3d(${-this._offset}px, 0, 0)`;
    }
  }
}
