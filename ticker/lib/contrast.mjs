/**
 * League colours are brand colours, chosen for white jerseys and printed
 * logos, not for 12px text on a dark card. NFL #013369, MLB #002d72, EPL
 * #38003c and UCL #0e1a4a all land near 1.3:1 against the board's card
 * surface: the tag is there, and nobody can read it.
 *
 * readableOn() keeps the hue and lifts it toward white, in small steps, until
 * it clears WCAG AA for text (4.5:1) against the given surface. The square
 * marker next to the tag keeps the untouched brand colour, so the league's
 * identity survives; only the words get the lift. Pure and DOM-free so Node
 * can test it.
 */

const HEX = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i;

function rgb(hex) {
  let h = hex.slice(1);
  if (h.length === 3) h = h.split('').map((c) => c + c).join('');
  return [0, 2, 4].map((i) => parseInt(h.slice(i, i + 2), 16));
}

function luminance([r, g, b]) {
  const lin = (v) => {
    const c = v / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b);
}

export function contrastRatio(a, b) {
  const la = luminance(rgb(a));
  const lb = luminance(rgb(b));
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
}

function toHex(c) {
  return `#${c.map((v) => Math.round(v).toString(16).padStart(2, '0')).join('')}`;
}

/**
 * [color] if it already reads on [surface]; otherwise the same hue mixed
 * toward white just far enough to reach [min]. Anything that is not a hex
 * colour (a CSS variable, say) is returned unchanged.
 */
export function readableOn(color, surface = '#1F2736', min = 4.5) {
  if (typeof color !== 'string' || !HEX.test(color) || !HEX.test(surface)) return color;
  if (contrastRatio(color, surface) >= min) return color.toLowerCase();
  const base = rgb(color);
  for (let t = 0.05; t <= 1.0001; t += 0.05) {
    const mixed = toHex(base.map((v) => v + (255 - v) * t));
    if (contrastRatio(mixed, surface) >= min) return mixed;
  }
  return '#ffffff';
}
