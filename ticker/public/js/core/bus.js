/**
 * A ten-line event bus.
 *
 * The icon pack solves "screen A needs to tell screen B something" with
 * Android's own component model. A web app has no such thing, so the previous
 * version of this app was one 1,340-line file where every function could call
 * every other — which is why the module split kept getting deferred.
 *
 * Three events are enough, and they map to the three reasons the UI changes:
 *
 *   slate   new listings arrived                -> re-render everything
 *   state   a setting changed                   -> re-apply chrome, re-render
 *   filter  the search box or a league changed  -> re-render the board only
 *
 * `toast` and `alert` are separate: they are notifications, not state.
 */

const listeners = new Map();

export function on(event, fn) {
  if (!listeners.has(event)) listeners.set(event, new Set());
  listeners.get(event).add(fn);
  return () => listeners.get(event)?.delete(fn);
}

export function emit(event, payload) {
  const set = listeners.get(event);
  if (!set) return;
  for (const fn of [...set]) {
    try {
      fn(payload);
    } catch (err) {
      console.error(`core-line: listener for "${event}" threw`, err);
    }
  }
}
