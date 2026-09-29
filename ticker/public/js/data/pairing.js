/**
 * Add-a-feed-from-your-phone.
 *
 * Typing an RSS URL with a remote is miserable, so the TV app runs a
 * code-gated LAN server, shows a QR code, and polls its own inbox. The feed
 * arrives from the phone; nothing is proxied through the internet.
 */

import { emit } from '../core/bus.js';
import { $, setHidden, setText } from '../core/dom.js';
import { qrDataUrl } from '../qr.js';
import { nativeBridge } from '../core/bridge.js';

let pairTimer = null;

export function startPair() {
  const bridge = nativeBridge();
  if (!bridge?.startPair) {
    emit('toast', 'Pairing is in the Android app — same Wi-Fi as the TV');
    return;
  }
  let info;
  try {
    info = JSON.parse(bridge.startPair());
  } catch {
    emit('toast', 'Could not start pairing');
    return;
  }
  if (!info.ok || !info.url) {
    emit('toast', info.error || 'No Wi-Fi address yet');
    return;
  }
  setHidden('pairCard', false);
  setText('pairUrl', info.url);
  setText('pairCode', info.code || '');
  const qr = $('pairQr');
  const dataUrl = qrDataUrl(info.url, 6);
  if (qr) {
    if (dataUrl) {
      qr.src = dataUrl;
      qr.style.display = 'block';
    } else {
      qr.style.display = 'none';
    }
  }
  clearInterval(pairTimer);
  pairTimer = setInterval(pollPairInbox, 1000);
}

export function stopPair() {
  clearInterval(pairTimer);
  pairTimer = null;
  try { nativeBridge()?.stopPair?.(); } catch { /* ignore */ }
  setHidden('pairCard', true);
}

export function pollPairInbox() {
  const bridge = nativeBridge();
  if (!bridge?.takeInbox) return;
  let payload = '';
  try { payload = bridge.takeInbox() || ''; } catch { return; }
  if (!payload) return;
  try {
    const feed = JSON.parse(payload);
    if (feed.url) {
      emit('pair:feed', feed);
      stopPair();
    }
  } catch { /* malformed payload — ignore */ }
}
