/**
 * Constant-speed chyron ribbon.
 *
 * Replaces the old CSS `@keyframes crawl` (which moved at "seconds per half
 * ribbon", so pixel speed varied with content length, and which seam-jumped
 * when item widths changed mid-animation).
 *
 * This loop moves the ribbon at a fixed px/s via a composited translate3d,
 * wraps at one copy's width, and re-measures on content change + resize, so
 * swapping in fresh slate content never teleports the ribbon. GPU-cheap:
 * only the transform of a single compositor-promoted element is written.
 */

export class Ticker {
  constructor({ track, seqA, seqB, speed = 52, mask = null }) {
    this.track = track; // #crawl — the moving element (two identical copies)
    this.seqA = seqA;   // #crawlA — first copy
    this.seqB = seqB;   // #crawlB — duplicate for a seamless wrap
    this.mask = mask;   // #crawl-mask — clip region
    this.speed = speed; // px per second
    this.offset = 0;
    this.seqWidth = 0;
    this.running = false;
    this._raf = 0;
    this._last = 0;
    this._resizeObserver = null;

    if (typeof ResizeObserver !== 'undefined') {
      this._resizeObserver = new ResizeObserver(() => this.measure());
      this._resizeObserver.observe(this.seqA);
    }
  }

  setSpeed(px) {
    this.speed = Number(px) || 0;
  }

  /** Replace the ribbon content (same HTML in both copies). */
  setItems(html) {
    this.seqA.innerHTML = html;
    this.seqB.innerHTML = html;
    this.measure();
  }

  measure() {
    const width = this.seqA.getBoundingClientRect().width || this.seqA.offsetWidth || 0;
    // Guarantee each copy is at least as wide as the visible window so a
    // short slate still covers the mask edge-to-edge.
    if (this.mask) {
      const visible = this.mask.clientWidth;
      if (width < visible) {
        this.seqA.style.minWidth = `${visible}px`;
        this.seqB.style.minWidth = `${visible}px`;
        this.seqWidth = visible;
        return;
      }
    }
    this.seqWidth = width;
  }

  /** Readout for the watchdog (px offset; changes whenever the loop is alive). */
  progress() {
    return this.offset;
  }

  start() {
    if (this.running || this.speed <= 0) return;
    this.running = true;
    this.measure();
    this._last = performance.now();
    this._raf = requestAnimationFrame((t) => this._tick(t));
  }

  stop() {
    this.running = false;
    cancelAnimationFrame(this._raf);
  }

  /** Restart from the current position (used by the watchdog on stall). */
  restart() {
    this.stop();
    this.start();
  }

  _tick(now) {
    if (!this.running) return;
    const dt = Math.min((now - this._last) / 1000, 0.1); // clamp background-tab jumps
    this._last = now;
    this.offset += this.speed * dt;
    if (this.seqWidth > 0 && this.offset >= this.seqWidth) {
      this.offset -= this.seqWidth;
    }
    this.track.style.transform = `translate3d(${-this.offset}px,0,0)`;
    this._raf = requestAnimationFrame((t) => this._tick(t));
  }

  destroy() {
    this.stop();
    if (this._resizeObserver) {
      this._resizeObserver.disconnect();
      this._resizeObserver = null;
    }
  }
}
