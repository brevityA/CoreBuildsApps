/**
 * The VPN dot's model.
 *
 * Native (`VpnState.kt`) reads the device and hands back a small snapshot;
 * this module turns that snapshot and the viewer's preferences into what the
 * settings pane says and what the native dot is told to draw. It is pure — no
 * DOM, no bridge, no clock — so the mapping rules are unit-tested rather than
 * eyeballed on a television, which is the same reason search matching lives in
 * `lib/query.mjs` and alert diffing in `lib/alerts.mjs`.
 *
 * Two claims are deliberately kept apart, because conflating them is how the
 * competing dots lie to people:
 *
 * - "a tunnel is up on this device" (what every VPN app's own screen shows);
 * - "this app's traffic goes through it" (per-app VPN / split tunnelling means
 *   the two can disagree, and the second is the one that matters while you
 *   watch something).
 *
 * A third, `validated`, is reported but never used to raise a fault. The
 * platform's reachability probe is documented to report success under local
 * VPNs (NetGuard, AdGuard) with no upstream connection at all, so a colour
 * driven by it would be wrong precisely when it mattered.
 */

/** The three states the dot can draw. */
export const VPN_STATES = ['ok', 'partial', 'down'];

export const VPN_CORNERS = ['bottom_right', 'bottom_left', 'top_right', 'top_left'];

export const VPN_OPACITY = { min: 15, max: 100, default: 55 };

export const VPN_CORNER_LABELS = {
  bottom_right: 'Bottom right',
  bottom_left: 'Bottom left',
  top_right: 'Top right',
  top_left: 'Top left',
};

const TRANSPORT_LABELS = {
  wifi: 'Wi-Fi',
  ethernet: 'Ethernet',
  cellular: 'mobile data',
  bluetooth: 'Bluetooth',
  vpn: 'VPN',
  none: 'no network',
  other: 'your network',
  unknown: 'your network',
};

/**
 * Coerce whatever the bridge returned into the shape the UI trusts.
 *
 * An explicit `state` wins when it is one of the three; otherwise it is
 * derived from the booleans, so a payload from an older or newer shell still
 * renders as something true rather than blank.
 */
export function normalizeVpnStatus(raw) {
  const tunnelUp = raw?.tunnelUp === true;
  const covering = raw?.covering === true;
  const validated = raw?.validated === true;
  const transport = typeof raw?.transport === 'string' ? raw.transport : 'unknown';
  const derived = !tunnelUp ? 'down' : covering ? 'ok' : 'partial';
  const state = VPN_STATES.includes(raw?.state) ? raw.state : derived;
  return { state, tunnelUp, covering, validated, transport };
}

export function transportLabel(transport) {
  return TRANSPORT_LABELS[String(transport)] || TRANSPORT_LABELS.unknown;
}

/** Is the dot on screen, given the current state and the viewer's preferences? */
export function vpnDotVisible(status, prefs) {
  if (!prefs?.vpnDot) return false;
  if (prefs.vpnDotHideWhenOk && normalizeVpnStatus(status).state === 'ok') return false;
  return true;
}

/** Which token family the preview swatch uses: ok | warn | down. */
export function vpnDotTone(state) {
  const clean = VPN_STATES.includes(state) ? state : 'down';
  return clean === 'ok' ? 'ok' : clean === 'partial' ? 'warn' : 'down';
}

export function vpnHeadline(raw) {
  const { state } = normalizeVpnStatus(raw);
  if (state === 'ok') return 'VPN on';
  if (state === 'partial') return 'VPN on — this app is not in the tunnel';
  return 'VPN off';
}

export function vpnDetail(raw) {
  const { state, validated, transport } = normalizeVpnStatus(raw);
  const under = `on ${transportLabel(transport)}`;
  if (state === 'down') return `No VPN tunnel on this device ${under}`;
  if (state === 'partial') {
    return `A tunnel is up ${under}, but Core Line's own traffic is not routed through it (per-app VPN).`;
  }
  // The kill-switch case: the tunnel is up, the probe did not confirm the
  // path. Said out loud, never turned into a colour we cannot justify.
  const caveat = validated ? '' : ' Reachability through the tunnel is unconfirmed.';
  return `All of Core Line's traffic is tunnelled ${under}.${caveat}`;
}

function clampInt(value, min, max, fallback) {
  const n = Number(value);
  if (!Number.isFinite(n)) return fallback;
  return Math.max(min, Math.min(max, Math.round(n)));
}

/**
 * The payload the native side persists and draws from.
 *
 * `marginDp` is where the panel's own calibration meets the dot: the web side
 * knows the overscan the viewer dialled in (see `currentOverscan()`), the
 * native side knows the pixel density. Fold the first into dp here so the dot
 * lands inside the safe area of a cropping panel instead of in the 3% a set
 * throws away.
 */
export function vpnDotConfig(prefs, { overscanPx = 0, devicePixelRatio = 1 } = {}) {
  const dpr = Number(devicePixelRatio);
  const scale = Number.isFinite(dpr) && dpr > 0 ? dpr : 1;
  const overscan = Number.isFinite(Number(overscanPx)) ? Number(overscanPx) : 0;
  return {
    corner: VPN_CORNERS.includes(prefs?.vpnDotCorner) ? prefs.vpnDotCorner : 'bottom_right',
    opacity: clampInt(prefs?.vpnDotOpacity, VPN_OPACITY.min, VPN_OPACITY.max, VPN_OPACITY.default),
    hideWhenOk: prefs?.vpnDotHideWhenOk === true,
    blink: prefs?.vpnDotBlink !== false,
    // 16dp of breathing room on every screen, plus the panel's own safe area.
    // A negative or corrupt overscan never eats the breathing room.
    marginDp: Math.min(200, Math.max(16, Math.round(overscan / scale) + 16)),
  };
}
