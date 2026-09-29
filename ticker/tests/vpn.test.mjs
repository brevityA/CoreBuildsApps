import { test } from 'node:test';
import assert from 'node:assert/strict';

import {
  normalizeVpnStatus,
  transportLabel,
  vpnDotVisible,
  vpnDotTone,
  vpnHeadline,
  vpnDetail,
  vpnDotConfig,
  VPN_OPACITY,
} from '../lib/vpn.mjs';

/* ---- the snapshot ------------------------------------------------------ */

test('an explicit state wins when it is one of the three', () => {
  const s = normalizeVpnStatus({ state: 'partial', tunnelUp: true, covering: false });
  assert.equal(s.state, 'partial');
});

test('a missing or unknown state is derived from the booleans', () => {
  assert.equal(normalizeVpnStatus({ tunnelUp: true, covering: true }).state, 'ok');
  assert.equal(normalizeVpnStatus({ tunnelUp: true, covering: false }).state, 'partial');
  assert.equal(normalizeVpnStatus({ tunnelUp: false, covering: false }).state, 'down');
  assert.equal(normalizeVpnStatus({ state: 'banana', tunnelUp: true, covering: true }).state, 'ok');
});

test('a tunnel that is up but not covering this app is not reported as ok', () => {
  // This is the clean and partial split: "VPN on" and "your traffic is in it"
  // are different claims, and split tunnelling makes them disagree.
  const s = normalizeVpnStatus({ tunnelUp: true, covering: false, validated: true });
  assert.equal(s.state, 'partial');
  assert.equal(s.tunnelUp, true);
  assert.equal(s.covering, false);
});

test('nothing at all means down, never a crash', () => {
  for (const raw of [undefined, null, {}, 'nonsense', 0]) {
    assert.equal(normalizeVpnStatus(raw).state, 'down');
    assert.equal(normalizeVpnStatus(raw).transport, 'unknown');
  }
});

test('string booleans from a bridge are not treated as true', () => {
  const s = normalizeVpnStatus({ tunnelUp: 'true', covering: 'false' });
  assert.equal(s.tunnelUp, false);
  assert.equal(s.state, 'down');
});

test('validated is carried, not folded into the state', () => {
  // The dot must not go amber on the platform probe alone: local VPNs report
  // VALIDATED with no upstream connection at all.
  const s = normalizeVpnStatus({ tunnelUp: true, covering: true, validated: false });
  assert.equal(s.state, 'ok');
  assert.equal(s.validated, false);
});

/* ---- what the viewer reads --------------------------------------------- */

test('headlines name the state in plain words', () => {
  assert.equal(vpnHeadline({ state: 'ok' }), 'VPN on');
  assert.match(vpnHeadline({ state: 'partial' }), /not in the tunnel/);
  assert.equal(vpnHeadline({ state: 'down' }), 'VPN off');
});

test('the down detail names the transport underneath', () => {
  assert.equal(vpnDetail({ state: 'down', transport: 'wifi' }), 'No VPN tunnel on this device on Wi-Fi');
});

test('an unconfirmed tunnel is said out loud, not coloured', () => {
  const confirmed = vpnDetail({ state: 'ok', validated: true, transport: 'ethernet' });
  assert.equal(confirmed.includes('unconfirmed'), false);
  const unconfirmed = vpnDetail({ state: 'ok', validated: false, transport: 'ethernet' });
  assert.match(unconfirmed, /unconfirmed/);
});

test('transport labels fall back rather than printing a raw token', () => {
  assert.equal(transportLabel('wifi'), 'Wi-Fi');
  assert.equal(transportLabel('ethernet'), 'Ethernet');
  assert.equal(transportLabel('satellite'), 'your network');
  assert.equal(transportLabel(undefined), 'your network');
});

/* ---- visibility and tone ----------------------------------------------- */

test('the dot only shows when it is switched on', () => {
  assert.equal(vpnDotVisible({ state: 'ok' }, { vpnDot: false }), false);
  assert.equal(vpnDotVisible({ state: 'ok' }, { vpnDot: true }), true);
});

test('hide-when-ok hides the healthy dot but never the fault', () => {
  const prefs = { vpnDot: true, vpnDotHideWhenOk: true };
  assert.equal(vpnDotVisible({ state: 'ok' }, prefs), false);
  assert.equal(vpnDotVisible({ state: 'partial' }, prefs), true);
  assert.equal(vpnDotVisible({ state: 'down' }, prefs), true);
});

test('tones map to the three status colours and nothing else', () => {
  assert.equal(vpnDotTone('ok'), 'ok');
  assert.equal(vpnDotTone('partial'), 'warn');
  assert.equal(vpnDotTone('down'), 'down');
  assert.equal(vpnDotTone(undefined), 'down');
});

/* ---- the payload the native dot is drawn from --------------------------- */

test('config carries the viewer settings and defaults the rest', () => {
  const config = vpnDotConfig({
    vpnDotCorner: 'top_left',
    vpnDotOpacity: 40,
    vpnDotHideWhenOk: true,
    vpnDotBlink: false,
  }, { overscanPx: 48, devicePixelRatio: 1 });
  assert.deepEqual(config, {
    corner: 'top_left',
    opacity: 40,
    hideWhenOk: true,
    blink: false,
    marginDp: 64,
  });
});

test('an unknown corner falls back to the bottom right', () => {
  assert.equal(vpnDotConfig({ vpnDotCorner: 'middle' }).corner, 'bottom_right');
  assert.equal(vpnDotConfig({}).corner, 'bottom_right');
});

test('opacity is clamped into the range the native view accepts', () => {
  assert.equal(vpnDotConfig({ vpnDotOpacity: 0 }).opacity, VPN_OPACITY.min);
  assert.equal(vpnDotConfig({ vpnDotOpacity: 500 }).opacity, VPN_OPACITY.max);
  assert.equal(vpnDotConfig({ vpnDotOpacity: 'nope' }).opacity, VPN_OPACITY.default);
});

test('margin folds the panel overscan into dp and never goes negative', () => {
  // 48px of calibration on a 1x television is 48dp of safe area plus the 16dp
  // breathing room the dot keeps on every screen.
  assert.equal(vpnDotConfig({}, { overscanPx: 48, devicePixelRatio: 1 }).marginDp, 64);
  // A 2x phone with a 24px gutter: 12dp of gutter + 16dp.
  assert.equal(vpnDotConfig({}, { overscanPx: 24, devicePixelRatio: 2 }).marginDp, 28);
  assert.equal(vpnDotConfig({}, { overscanPx: -50, devicePixelRatio: 1 }).marginDp, 16);
  assert.equal(vpnDotConfig({}, { overscanPx: 'x', devicePixelRatio: 0 }).marginDp, 16);
  assert.equal(vpnDotConfig({}, {}).marginDp, 16);
});

test('blink defaults on, and only an explicit false turns it off', () => {
  assert.equal(vpnDotConfig({}).blink, true);
  assert.equal(vpnDotConfig({ vpnDotBlink: undefined }).blink, true);
  assert.equal(vpnDotConfig({ vpnDotBlink: false }).blink, false);
});
