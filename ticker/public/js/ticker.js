/**
 * Constant-speed chyron ribbon.
 *
 * Replaces the old CSS `@keyframes crawl` (which moved at "seconds per half
 * ribbon", so pixel speed varied with content length, and which seam-jumped
 * when item widths changed mid-animation).
 *
 * The ribbon moves at a fixed px/s, wraps at one copy's width, and re-measures
 * on content change + resize, so swapping in fresh slate content never
 * teleports it.
 *
 * Where the Web Animations API exists (every Android System WebView this app
 * supports), the motion is one infinite linear `transform` animation that the
 * compositor runs on its own: the main thread does no work per frame. That
 * matters on a TV. The previous loop wrote `style.transform` from
 * requestAnimationFrame, which forced a full main-thread frame sixty times a
 * second — measured at about half the main thread at a 6x CPU slowdown, for a
 * strip that only ever moves sideways — and competed with the D-pad for it.
 * The animation is rebuilt from the current position whenever the width or the
 * speed changes, so the constant-speed and no-teleport rules still hold.
 *
 * Without `animate()` (Node's tests, an old engine) the requestAnimationFrame
 * loop below is the fallback, unchanged.
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
    this._anim = null;      // the compositor animation, when there is one
    this._animSpeed = 0;    // the speed and width it was built with, which
    this._animWidth = 0;    // are what its currentTime has to be read against
    this._resizeObserver = null;

    if (typeof ResizeObserver !== 'undefined') {
      this._resizeObserver = new ResizeObserver(() => this.measure());
      this._resizeObserver.observe(this.seqA);
    }
  }

  /** True when the compositor can run the motion (Web Animations API). */
  get compositor() {
    return typeof this.track?.animate === 'function';
  }

  setSpeed(px) {
    this._sync();
    this.speed = Number(px) || 0;
    if (this.running && this.compositor) this._play();
  }

  /** Replace the ribbon content (same HTML in both copies). */
  setItems(html) {
    this.seqA.innerHTML = html;
    this.seqB.innerHTML = html;
    this.measure();
  }

  measure() {
    const before = this.seqWidth;
    this._sync();
    const width = this.seqA.getBoundingClientRect().width || this.seqA.offsetWidth || 0;
    // Guarantee each copy is at least as wide as the visible window so a
    // short slate still covers the mask edge-to-edge.
    let next = width;
    if (this.mask) {
      const visible = this.mask.clientWidth;
      if (width < visible) {
        this.seqA.style.minWidth = `${visible}px`;
        this.seqB.style.minWidth = `${visible}px`;
        next = visible;
      }
    }
    this.seqWidth = next;
    if (this.seqWidth > 0 && this.offset >= this.seqWidth) this.offset %= this.seqWidth;
    if (this.running && this.compositor && next !== before) this._play();
  }

  /** Readout for the watchdog (px offset; changes whenever the loop is alive). */
  progress() {
    this._sync();
    return this.offset;
  }

  start() {
    if (this.running || this.speed <= 0) return;
    this.running = true;
    this.measure();
    if (this.compositor) {
      this._play();
      return;
    }
    this._last = performance.now();
    this._raf = requestAnimationFrame((t) => this._tick(t));
  }

  stop() {
    this._sync();
    this.running = false;
    cancelAnimationFrame(this._raf);
    if (this._anim) {
      this._anim.cancel();
      this._anim = null;
      // Hold the ribbon where it was; a cancelled animation would otherwise
      // snap it back to the start.
      this.track.style.transform = `translate3d(${-this.offset}px,0,0)`;
    }
  }

  /** Restart from the current position (used by the watchdog on stall). */
  restart() {
    this.stop();
    this.start();
  }

  /** Read the compositor animation's position back into `offset`. */
  _sync() {
    const anim = this._anim;
    if (!anim || !(this._animWidth > 0) || !(this._animSpeed > 0)) return;
    const t = Number(anim.currentTime);
    if (!Number.isFinite(t)) return;
    this.offset = ((t / 1000) * this._animSpeed) % this._animWidth;
  }

  /** (Re)build the compositor animation from the current offset. */
  _play() {
    if (this._anim) {
      this._anim.cancel();
      this._anim = null;
    }
    if (!(this.seqWidth > 0) || !(this.speed > 0)) {
      // Nothing to play (speed 0, or an empty strip): hold the ribbon where
      // it was, as stop() does, rather than letting the cancelled animation
      // snap it back to the start.
      this.track.style.transform = `translate3d(${-this.offset}px,0,0)`;
      return;
    }
    const duration = (this.seqWidth / this.speed) * 1000;
    this._animWidth = this.seqWidth;
    this._animSpeed = this.speed;
    this._anim = this.track.animate(
      [
        { transform: 'translate3d(0px,0,0)' },
        { transform: `translate3d(${-this.seqWidth}px,0,0)` },
      ],
      { duration, iterations: Infinity, easing: 'linear' },
    );
    this._anim.currentTime = (this.offset / this.speed) * 1000;
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
