/**
 * Every `@JavascriptInterface` method runs on the WebView's JavaBridge
 * thread, not the UI thread, and Android throws when a WebView method is
 * called from there ("A WebView method was called on thread 'JavaBridge'").
 *
 * That is how the floating ticker shipped broken: `startOverlay()` read the
 * edge with `webView.evaluateJavascript(...)`, the call threw before the
 * service was started, and the toggle did nothing on every device. Nothing
 * caught it — the emulator check grants the overlay permission but never
 * turns the ticker on — so this pins the rule at the source: a MainActivity
 * method the bridge calls may not touch `webView` except inside
 * `runOnUiThread { … }`.
 */

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const SRC = new URL('../android/app/src/main/java/dev/corebuilds/line/', import.meta.url);
const bridge = readFileSync(new URL('LineBridge.kt', SRC), 'utf8');
const activity = readFileSync(new URL('MainActivity.kt', SRC), 'utf8');

/** The body of `fun name(` in `src`, braces matched; null for an expression body or no match. */
function body(src, name) {
  const at = src.search(new RegExp(`\\bfun ${name}\\(`));
  if (at < 0) return null;
  const sig = src.indexOf(')', at);
  const after = src.slice(sig + 1).match(/^[^{=]*([{=])/);
  if (!after || after[1] === '=') {
    // Expression body: up to the end of the line is enough for a one-liner.
    return src.slice(at, src.indexOf('\n', sig));
  }
  const open = sig + 1 + after[0].length - 1;
  let depth = 0;
  for (let i = open; i < src.length; i += 1) {
    if (src[i] === '{') depth += 1;
    else if (src[i] === '}' && (depth -= 1) === 0) return src.slice(open, i + 1);
  }
  return null;
}

/** `webView.` uses that are not inside a `runOnUiThread { … }` block. */
function offThreadWebViewCalls(code) {
  let rest = code;
  for (;;) {
    const at = rest.indexOf('runOnUiThread');
    if (at < 0) break;
    const open = rest.indexOf('{', at);
    let depth = 0;
    let end = open;
    for (; end < rest.length; end += 1) {
      if (rest[end] === '{') depth += 1;
      else if (rest[end] === '}' && (depth -= 1) === 0) break;
    }
    rest = rest.slice(0, at) + rest.slice(end + 1);
  }
  return rest.match(/\bwebView\./g) || [];
}

const called = [...new Set([...bridge.matchAll(/\bactivity\.(\w+)\(/g)].map((m) => m[1]))];

test('the bridge calls into MainActivity at all (the scan is looking at real code)', () => {
  assert.ok(called.length >= 10, `only ${called.length} activity calls found in LineBridge.kt`);
  assert.ok(called.includes('startOverlay'));
});

test('no MainActivity method the bridge calls touches the WebView off the UI thread', () => {
  const offenders = called.filter((name) => {
    const code = body(activity, name);
    return code && offThreadWebViewCalls(code).length > 0;
  });
  assert.deepEqual(offenders, [], `these run on the JavaBridge thread and call webView.*: ${offenders.join(', ')}`);
});

test('startOverlay starts the service from the stored edge, not from the page', () => {
  const code = body(activity, 'startOverlay');
  assert.ok(code, 'startOverlay not found');
  assert.match(code, /OverlayService\.startTicker\(this, OverlayPrefs\(this\)\.tickerPosition\)/);
  assert.doesNotMatch(code, /evaluateJavascript/);
});

test('setOverlayEdge persists the edge the next start will use', () => {
  const code = body(activity, 'setOverlayEdge');
  assert.match(code, /OverlayPrefs\(this\)\.tickerPosition\s*=/);
});

test('the drawer hands over the edge before starting, and trusts a true start', () => {
  const settings = readFileSync(new URL('../public/js/ui/settings.js', import.meta.url), 'utf8');
  const handler = settings.slice(settings.indexOf("$('overlayEnabled')?.addEventListener"));
  const edgeAt = handler.indexOf('setOverlayEdge');
  const startAt = handler.indexOf('startOverlay');
  assert.ok(edgeAt > 0 && startAt > edgeAt, 'setOverlayEdge must come before startOverlay');
  assert.match(handler, /e\.target\.checked = started \|\|/);
});
