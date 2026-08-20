/**
 * Minimal QR Code encoder — byte mode, EC level L, versions 1–6.
 * Self-contained, zero dependencies. Generates a data-URI PNG via Canvas.
 */

const EXP = new Uint8Array(256);
const LOG = new Uint8Array(256);
(() => {
  let v = 1;
  for (let i = 0; i < 255; i++) {
    EXP[i] = v;
    LOG[v] = i;
    v = (v << 1) ^ (v >= 128 ? 0x11d : 0);
  }
  EXP[255] = EXP[0];
})();

function gfMul(a, b) {
  return a === 0 || b === 0 ? 0 : EXP[(LOG[a] + LOG[b]) % 255];
}

function rsEncode(data, ecLen) {
  const gen = [1];
  for (let i = 0; i < ecLen; i++) {
    const next = new Array(gen.length + 1).fill(0);
    for (let j = 0; j < gen.length; j++) {
      next[j] ^= gen[j];
      next[j + 1] ^= gfMul(gen[j], EXP[i]);
    }
    gen.splice(0, gen.length, ...next);
  }
  const msg = new Uint8Array(data.length + ecLen);
  msg.set(data);
  for (let i = 0; i < data.length; i++) {
    const coef = msg[i];
    if (coef === 0) continue;
    for (let j = 0; j < gen.length; j++) {
      msg[i + j] ^= gfMul(gen[j], coef);
    }
  }
  return msg.slice(data.length);
}

const VERSIONS = [
  null,
  { size: 21, dataCw: 19, ecCw: 7 },
  { size: 25, dataCw: 34, ecCw: 10 },
  { size: 29, dataCw: 55, ecCw: 15 },
  { size: 33, dataCw: 80, ecCw: 20 },
  { size: 37, dataCw: 108, ecCw: 26 },
  { size: 41, dataCw: 136, ecCw: 18 },
];

const ALIGN = [null, [], [6, 18], [6, 22], [6, 26], [6, 30], [6, 34]];

const FORMAT_BITS = [
  0x77c4, 0x72f3, 0x7daa, 0x789d, 0x662f, 0x6318, 0x6c41, 0x6976,
];

function pickVersion(len) {
  for (let v = 1; v <= 6; v++) {
    if (len <= VERSIONS[v].dataCw - 2) return v;
  }
  return 0;
}

function encodeData(text, ver) {
  const { dataCw, ecCw } = VERSIONS[ver];
  const totalCw = dataCw + ecCw;
  const bytes = new TextEncoder().encode(text);
  const bits = [];
  const push = (val, n) => { for (let i = n - 1; i >= 0; i--) bits.push((val >> i) & 1); };

  push(0b0100, 4);
  push(bytes.length, ver >= 10 ? 16 : 8);
  for (const b of bytes) push(b, 8);
  push(0, Math.min(4, dataCw * 8 - bits.length));
  while (bits.length % 8) bits.push(0);
  while (bits.length < dataCw * 8) {
    push(0xec, 8);
    if (bits.length < dataCw * 8) push(0x11, 8);
  }

  const data = new Uint8Array(dataCw);
  for (let i = 0; i < dataCw; i++) {
    let byte = 0;
    for (let b = 0; b < 8; b++) byte = (byte << 1) | (bits[i * 8 + b] || 0);
    data[i] = byte;
  }

  const ec = rsEncode(data, ecCw);
  const stream = new Uint8Array(totalCw);
  stream.set(data);
  stream.set(ec, dataCw);
  return stream;
}

function createMatrix(ver) {
  const n = VERSIONS[ver].size;
  const m = Array.from({ length: n }, () => new Int8Array(n));
  const reserved = Array.from({ length: n }, () => new Uint8Array(n));

  function finderPattern(r, c) {
    for (let dr = -1; dr <= 7; dr++) {
      for (let dc = -1; dc <= 7; dc++) {
        const rr = r + dr, cc = c + dc;
        if (rr < 0 || rr >= n || cc < 0 || cc >= n) continue;
        const outer = dr === -1 || dr === 7 || dc === -1 || dc === 7;
        const ring = dr === 0 || dr === 6 || dc === 0 || dc === 6;
        const inner = dr >= 2 && dr <= 4 && dc >= 2 && dc <= 4;
        m[rr][cc] = (ring || inner) && !outer ? 1 : 0;
        reserved[rr][cc] = 1;
      }
    }
  }

  finderPattern(0, 0);
  finderPattern(0, n - 7);
  finderPattern(n - 7, 0);

  for (let i = 8; i < n - 8; i++) {
    m[6][i] = m[i][6] = i % 2 === 0 ? 1 : 0;
    reserved[6][i] = reserved[i][6] = 1;
  }

  const aligns = ALIGN[ver];
  for (const ar of aligns) {
    for (const ac of aligns) {
      if (reserved[ar]?.[ac]) continue;
      for (let dr = -2; dr <= 2; dr++) {
        for (let dc = -2; dc <= 2; dc++) {
          const abs = Math.max(Math.abs(dr), Math.abs(dc));
          m[ar + dr][ac + dc] = abs === 1 ? 0 : 1;
          reserved[ar + dr][ac + dc] = 1;
        }
      }
    }
  }

  for (let i = 0; i < 8; i++) {
    for (const [r, c] of [[8, i], [i, 8], [8, n - 1 - i], [n - 1 - i, 8]]) {
      if (!reserved[r]?.[c]) reserved[r][c] = 1;
    }
  }
  reserved[8][8] = 1;
  m[n - 8][8] = 1;
  reserved[n - 8][8] = 1;

  return { m, reserved, n };
}

function placeData(matrix, stream) {
  const { m, reserved, n } = matrix;
  let bitIdx = 0;
  const totalBits = stream.length * 8;
  for (let right = n - 1; right >= 1; right -= 2) {
    if (right === 6) right = 5;
    for (let vert = 0; vert < n; vert++) {
      for (let j = 0; j < 2; j++) {
        const col = right - j;
        const row = ((Math.floor((n - 1 - right) / 2)) % 2 === 0) ? n - 1 - vert : vert;
        if (reserved[row][col]) continue;
        if (bitIdx < totalBits) {
          m[row][col] = (stream[bitIdx >> 3] >> (7 - (bitIdx & 7))) & 1;
          bitIdx++;
        }
      }
    }
  }
}

const MASKS = [
  (r, c) => (r + c) % 2 === 0,
  (r, c) => r % 2 === 0,
  (r, c) => c % 3 === 0,
  (r, c) => (r + c) % 3 === 0,
  (r, c) => (Math.floor(r / 2) + Math.floor(c / 3)) % 2 === 0,
  (r, c) => (r * c) % 2 + (r * c) % 3 === 0,
  (r, c) => ((r * c) % 2 + (r * c) % 3) % 2 === 0,
  (r, c) => ((r + c) % 2 + (r * c) % 3) % 2 === 0,
];

function applyMask(matrix, maskIdx) {
  const { m, reserved, n } = matrix;
  const out = m.map((row) => Int8Array.from(row));
  const fn = MASKS[maskIdx];
  for (let r = 0; r < n; r++) {
    for (let c = 0; c < n; c++) {
      if (!reserved[r][c] && fn(r, c)) out[r][c] ^= 1;
    }
  }
  return out;
}

function penalty(grid) {
  const n = grid.length;
  let score = 0;
  for (let r = 0; r < n; r++) {
    for (let c = 0; c < n - 4; c++) {
      const v = grid[r][c];
      if (grid[r][c+1] === v && grid[r][c+2] === v && grid[r][c+3] === v && grid[r][c+4] === v) score += 3;
    }
  }
  for (let c = 0; c < n; c++) {
    for (let r = 0; r < n - 4; r++) {
      const v = grid[r][c];
      if (grid[r+1][c] === v && grid[r+2][c] === v && grid[r+3][c] === v && grid[r+4][c] === v) score += 3;
    }
  }
  return score;
}

function addFormatInfo(grid, maskIdx) {
  const n = grid.length;
  const bits = FORMAT_BITS[maskIdx];
  for (let i = 0; i < 6; i++) grid[8][i] = (bits >> (14 - i)) & 1;
  grid[8][7] = (bits >> 8) & 1;
  grid[8][8] = (bits >> 7) & 1;
  grid[7][8] = (bits >> 6) & 1;
  for (let i = 0; i < 6; i++) grid[5 - i][8] = (bits >> (5 - i)) & 1;
  for (let i = 0; i < 7; i++) grid[n - 1 - i][8] = (bits >> (14 - i)) & 1;
  for (let i = 0; i < 8; i++) grid[8][n - 8 + i] = (bits >> (7 - i)) & 1;
}

export function qrDataUrl(text, scale = 8) {
  const ver = pickVersion(new TextEncoder().encode(text).length);
  if (!ver) return '';
  const stream = encodeData(text, ver);
  const matrix = createMatrix(ver);
  placeData(matrix, stream);

  let bestGrid = null, bestPen = Infinity;
  for (let mi = 0; mi < 8; mi++) {
    const grid = applyMask(matrix, mi);
    addFormatInfo(grid, mi);
    const pen = penalty(grid);
    if (pen < bestPen) { bestPen = pen; bestGrid = grid; }
  }

  const n = bestGrid.length;
  const quiet = 4;
  const total = (n + quiet * 2) * scale;
  const canvas = document.createElement('canvas');
  canvas.width = canvas.height = total;
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = '#ffffff';
  ctx.fillRect(0, 0, total, total);
  ctx.fillStyle = '#000000';
  for (let r = 0; r < n; r++) {
    for (let c = 0; c < n; c++) {
      if (bestGrid[r][c]) ctx.fillRect((c + quiet) * scale, (r + quiet) * scale, scale, scale);
    }
  }
  return canvas.toDataURL('image/png');
}
