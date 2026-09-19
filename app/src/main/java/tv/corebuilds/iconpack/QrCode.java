package tv.corebuilds.iconpack;

import java.nio.charset.StandardCharsets;

/**
 * A byte-mode QR encoder, ECC level M, versions 1-20.
 *
 * <p>Why this exists rather than a dependency: the request screen has to hand a
 * ~250 character prefilled issue URL to a phone, and a TV has no browser to open
 * it in. That makes the QR the only handoff, and a QR that is subtly wrong fails
 * silently — the camera simply never locks on, and there is nothing on screen to
 * say why. ZXing would be the obvious answer, but this app builds with
 * {@code isMinifyEnabled = false} (resource shrinking would strip drawables that
 * are only ever resolved by name), so its 594 KB would ship whole, in both packs,
 * to use about a twentieth of it.
 *
 * <p>Why it is Java in an otherwise Kotlin module, and why it imports nothing
 * from Android: so it compiles and runs under a plain JDK. That is what makes
 * {@code tools/check_qr.py} possible — it round-trips a corpus of real prefill
 * URLs through ZXing's <em>decoder</em> and fails on any mismatch. ZXing is the
 * test oracle and is never a dependency of the APK. The constant tables below
 * were dumped from ZXing rather than transcribed, for the same reason.
 *
 * <p>Scope is deliberately narrow. Byte mode only (a URL is mixed-case, so
 * alphanumeric mode never applies); ECC M only (~15% recovery, the usual choice
 * for a screen-to-camera scan); versions 1-20, which reach 666 bytes — well past
 * the 414 URI Too Long ceiling the prefill generator already enforces at 1500.
 * Anything longer returns null, and the caller shows the URL as text instead.
 */
final class QrCode {

    private QrCode() {
    }

    /** ECC M block structure per version: {ecPerBlock, n1, data1, n2, data2}. */
    private static final int[][] ECC_M = {
        {},                    // index 0 unused; versions are 1-based
        {10, 1, 16, 0, 0},     {16, 1, 28, 0, 0},     {26, 1, 44, 0, 0},
        {18, 2, 32, 0, 0},     {24, 2, 43, 0, 0},     {16, 4, 27, 0, 0},
        {18, 4, 31, 0, 0},     {22, 2, 38, 2, 39},    {22, 3, 36, 2, 37},
        {26, 4, 43, 1, 44},    {30, 1, 50, 4, 51},    {22, 6, 36, 2, 37},
        {22, 8, 37, 1, 38},    {24, 4, 40, 5, 41},    {24, 5, 41, 5, 42},
        {28, 7, 45, 3, 46},    {28, 10, 46, 1, 47},   {26, 9, 43, 4, 44},
        {26, 3, 44, 11, 45},   {26, 3, 41, 13, 42},
    };

    /** Alignment pattern centre coordinates per version. */
    private static final int[][] ALIGNMENT = {
        {}, {},
        {6, 18}, {6, 22}, {6, 26}, {6, 30}, {6, 34},
        {6, 22, 38}, {6, 24, 42}, {6, 26, 46}, {6, 28, 50}, {6, 30, 54},
        {6, 32, 58}, {6, 34, 62},
        {6, 26, 46, 66}, {6, 26, 48, 70}, {6, 26, 50, 74}, {6, 30, 54, 78},
        {6, 30, 56, 82}, {6, 30, 58, 86}, {6, 34, 62, 90},
    };

    private static final int MAX_VERSION = 20;
    /** Format info ECC indicator for level M. */
    private static final int ECC_INDICATOR_M = 0;

    /**
     * Encodes {@code text} as a QR symbol.
     *
     * @return a {@code [size][size]} grid, true where the module is dark, or
     *     null when the text does not fit in version 20 at ECC M.
     */
    static boolean[][] encode(String text) {
        return encode(text, -1);
    }

    /**
     * As {@link #encode(String)}, but {@code forcedMask} 0-7 pins the mask
     * instead of choosing one by penalty. Only {@code tools/check_qr.py} passes
     * anything but -1: pinning lets it round-trip every payload under all eight
     * masks rather than the one the penalty rules happen to select, which is
     * what catches a placement bug that only some masks expose.
     */
    static boolean[][] encode(String text, int forcedMask) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        int version = smallestVersion(data.length);
        if (version == 0) {
            return null;
        }
        byte[] codewords = interleave(version, bitStream(version, data));
        return render(version, codewords, forcedMask);
    }

    /** Data codewords a version holds, across both block groups. */
    private static int dataCodewords(int version) {
        int[] spec = ECC_M[version];
        return spec[1] * spec[2] + spec[3] * spec[4];
    }

    /** Bits the byte-mode character count field occupies at this version. */
    private static int countBits(int version) {
        return version <= 9 ? 8 : 16;
    }

    /** Smallest version that fits {@code length} bytes, or 0 if none does. */
    private static int smallestVersion(int length) {
        for (int v = 1; v <= MAX_VERSION; v++) {
            int available = dataCodewords(v) * 8;
            if (4 + countBits(v) + length * 8 <= available) {
                return v;
            }
        }
        return 0;
    }

    /** Header, payload, terminator and pad bytes, as whole data codewords. */
    private static byte[] bitStream(int version, byte[] data) {
        int capacity = dataCodewords(version);
        BitBuffer bits = new BitBuffer(capacity * 8);
        bits.append(0b0100, 4);                       // byte mode
        bits.append(data.length, countBits(version));
        for (byte b : data) {
            bits.append(b & 0xFF, 8);
        }
        // Terminator: four zero bits, or fewer when the payload ends near the
        // capacity. Then zero-fill to the codeword boundary.
        int remaining = capacity * 8 - bits.length();
        bits.append(0, Math.min(4, remaining));
        bits.append(0, (8 - bits.length() % 8) % 8);

        byte[] out = new byte[capacity];
        System.arraycopy(bits.bytes(), 0, out, 0, bits.length() / 8);
        // The pad alternates 0xEC / 0x11 by specification, not by convention;
        // a decoder that hits a short block reports a data error.
        boolean ec = true;
        for (int i = bits.length() / 8; i < capacity; i++) {
            out[i] = (byte) (ec ? 0xEC : 0x11);
            ec = !ec;
        }
        return out;
    }

    /**
     * Splits the data into blocks, appends each block's Reed-Solomon codewords,
     * and interleaves the result the way the specification reads it back.
     */
    private static byte[] interleave(int version, byte[] data) {
        int[] spec = ECC_M[version];
        int ecPerBlock = spec[0];
        int blockCount = spec[1] + spec[3];

        byte[][] dataBlocks = new byte[blockCount][];
        byte[][] eccBlocks = new byte[blockCount][];
        int[] generator = rsGenerator(ecPerBlock);
        int offset = 0;
        for (int i = 0; i < blockCount; i++) {
            int size = i < spec[1] ? spec[2] : spec[4];
            byte[] block = new byte[size];
            System.arraycopy(data, offset, block, 0, size);
            offset += size;
            dataBlocks[i] = block;
            eccBlocks[i] = rsRemainder(block, generator);
        }

        byte[] out = new byte[data.length + blockCount * ecPerBlock];
        int at = 0;
        int longest = Math.max(spec[2], spec[3] > 0 ? spec[4] : 0);
        for (int i = 0; i < longest; i++) {
            for (byte[] block : dataBlocks) {
                if (i < block.length) {
                    out[at++] = block[i];
                }
            }
        }
        for (int i = 0; i < ecPerBlock; i++) {
            for (byte[] block : eccBlocks) {
                out[at++] = block[i];
            }
        }
        return out;
    }

    // ---- GF(256), primitive polynomial 0x11D ----

    private static final int[] EXP = new int[512];
    private static final int[] LOG = new int[256];

    static {
        int x = 1;
        for (int i = 0; i < 255; i++) {
            EXP[i] = x;
            LOG[x] = i;
            x <<= 1;
            if ((x & 0x100) != 0) {
                x ^= 0x11D;
            }
        }
        // Doubled so a product of two logs can be indexed without a modulo.
        for (int i = 255; i < 512; i++) {
            EXP[i] = EXP[i - 255];
        }
    }

    private static int mul(int a, int b) {
        if (a == 0 || b == 0) {
            return 0;
        }
        return EXP[LOG[a] + LOG[b]];
    }

    /** Generator polynomial of the given degree, low-order term last. */
    private static int[] rsGenerator(int degree) {
        int[] poly = new int[degree + 1];
        poly[0] = 1;
        int length = 1;
        for (int i = 0; i < degree; i++) {
            // Multiply the running product by (x - a^i).
            int[] next = new int[length + 1];
            for (int j = 0; j < length; j++) {
                next[j] ^= poly[j];
                next[j + 1] ^= mul(poly[j], EXP[i]);
            }
            System.arraycopy(next, 0, poly, 0, length + 1);
            length++;
        }
        return poly;
    }

    /** The block's error correction codewords. */
    private static byte[] rsRemainder(byte[] block, int[] generator) {
        int degree = generator.length - 1;
        int[] residue = new int[block.length + degree];
        for (int i = 0; i < block.length; i++) {
            residue[i] = block[i] & 0xFF;
        }
        for (int i = 0; i < block.length; i++) {
            int factor = residue[i];
            if (factor == 0) {
                continue;
            }
            for (int j = 0; j <= degree; j++) {
                residue[i + j] ^= mul(generator[j], factor);
            }
        }
        byte[] out = new byte[degree];
        for (int i = 0; i < degree; i++) {
            out[i] = (byte) residue[block.length + i];
        }
        return out;
    }

    // ---- Module placement ----

    private static boolean[][] render(int version, byte[] codewords, int forcedMask) {
        int size = version * 4 + 17;
        boolean[][] modules = new boolean[size][size];
        boolean[][] fixed = new boolean[size][size];

        finder(modules, fixed, 0, 0);
        finder(modules, fixed, 0, size - 7);
        finder(modules, fixed, size - 7, 0);
        separators(modules, fixed, size);
        timing(modules, fixed, size);
        alignment(modules, fixed, version, size);

        // The single always-dark module below the bottom-left finder.
        modules[size - 8][8] = true;
        fixed[size - 8][8] = true;
        reserveFormat(fixed, size);
        if (version >= 7) {
            reserveVersion(fixed, size);
        }

        placeData(modules, fixed, size, codewords);

        int best = 0;
        int bestPenalty = Integer.MAX_VALUE;
        boolean[][] bestGrid = null;
        for (int mask = 0; mask < 8; mask++) {
            if (forcedMask >= 0 && mask != forcedMask) {
                continue;
            }
            boolean[][] candidate = copy(modules);
            applyMask(candidate, fixed, size, mask);
            writeFormat(candidate, size, mask);
            if (version >= 7) {
                writeVersion(candidate, size, version);
            }
            int penalty = penalty(candidate, size);
            if (penalty < bestPenalty) {
                bestPenalty = penalty;
                best = mask;
                bestGrid = candidate;
            }
        }
        // `best` is read only through bestGrid; kept for readability of intent.
        if (bestGrid == null) {
            throw new IllegalStateException("no mask chosen for version " + version
                + " mask " + best);
        }
        return bestGrid;
    }

    private static boolean[][] copy(boolean[][] grid) {
        boolean[][] out = new boolean[grid.length][];
        for (int i = 0; i < grid.length; i++) {
            out[i] = grid[i].clone();
        }
        return out;
    }

    private static void finder(boolean[][] m, boolean[][] fixed, int row, int col) {
        for (int r = 0; r < 7; r++) {
            for (int c = 0; c < 7; c++) {
                boolean dark = r == 0 || r == 6 || c == 0 || c == 6
                    || (r >= 2 && r <= 4 && c >= 2 && c <= 4);
                m[row + r][col + c] = dark;
                fixed[row + r][col + c] = true;
            }
        }
    }

    private static void separators(boolean[][] m, boolean[][] fixed, int size) {
        for (int i = 0; i < 8; i++) {
            mark(m, fixed, 7, i);
            mark(m, fixed, i, 7);
            mark(m, fixed, 7, size - 1 - i);
            mark(m, fixed, i, size - 8);
            mark(m, fixed, size - 8, i);
            mark(m, fixed, size - 1 - i, 7);
        }
    }

    private static void mark(boolean[][] m, boolean[][] fixed, int r, int c) {
        m[r][c] = false;
        fixed[r][c] = true;
    }

    private static void timing(boolean[][] m, boolean[][] fixed, int size) {
        for (int i = 8; i < size - 8; i++) {
            boolean dark = i % 2 == 0;
            m[6][i] = dark;
            fixed[6][i] = true;
            m[i][6] = dark;
            fixed[i][6] = true;
        }
    }

    private static void alignment(boolean[][] m, boolean[][] fixed, int version, int size) {
        int[] centres = ALIGNMENT[version];
        for (int rc : centres) {
            for (int cc : centres) {
                // The three finder corners already own these positions.
                boolean atFinder = (rc <= 8 && cc <= 8)
                    || (rc <= 8 && cc >= size - 9)
                    || (rc >= size - 9 && cc <= 8);
                if (atFinder) {
                    continue;
                }
                for (int r = -2; r <= 2; r++) {
                    for (int c = -2; c <= 2; c++) {
                        boolean dark = Math.max(Math.abs(r), Math.abs(c)) != 1;
                        m[rc + r][cc + c] = dark;
                        fixed[rc + r][cc + c] = true;
                    }
                }
            }
        }
    }

    private static void reserveFormat(boolean[][] fixed, int size) {
        for (int i = 0; i < 9; i++) {
            fixed[8][i] = true;
            fixed[i][8] = true;
        }
        for (int i = 0; i < 8; i++) {
            fixed[8][size - 1 - i] = true;
            fixed[size - 1 - i][8] = true;
        }
    }

    private static void reserveVersion(boolean[][] fixed, int size) {
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 3; j++) {
                fixed[i][size - 11 + j] = true;
                fixed[size - 11 + j][i] = true;
            }
        }
    }

    private static void placeData(boolean[][] m, boolean[][] fixed, int size, byte[] codewords) {
        int bit = 0;
        int total = codewords.length * 8;
        boolean upward = true;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) {
                // Column 6 is the vertical timing pattern; step past it so the
                // pair becomes (5, 4) rather than straddling it.
                right = 5;
            }
            for (int vert = 0; vert < size; vert++) {
                for (int j = 0; j < 2; j++) {
                    int col = right - j;
                    int row = upward ? size - 1 - vert : vert;
                    if (fixed[row][col]) {
                        continue;
                    }
                    // Past the payload the remainder bits stay light.
                    boolean dark = false;
                    if (bit < total) {
                        dark = ((codewords[bit / 8] >> (7 - bit % 8)) & 1) == 1;
                        bit++;
                    }
                    m[row][col] = dark;
                }
            }
            upward = !upward;
        }
    }

    private static void applyMask(boolean[][] m, boolean[][] fixed, int size, int mask) {
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (fixed[r][c] || !maskAt(mask, r, c)) {
                    continue;
                }
                m[r][c] = !m[r][c];
            }
        }
    }

    private static boolean maskAt(int mask, int r, int c) {
        switch (mask) {
            case 0: return (r + c) % 2 == 0;
            case 1: return r % 2 == 0;
            case 2: return c % 3 == 0;
            case 3: return (r + c) % 3 == 0;
            case 4: return (r / 2 + c / 3) % 2 == 0;
            case 5: return (r * c) % 2 + (r * c) % 3 == 0;
            case 6: return ((r * c) % 2 + (r * c) % 3) % 2 == 0;
            case 7: return ((r + c) % 2 + (r * c) % 3) % 2 == 0;
            default: throw new IllegalArgumentException("mask " + mask);
        }
    }

    /** BCH(15,5) format information, XOR-masked and written in both copies. */
    private static void writeFormat(boolean[][] m, int size, int mask) {
        int value = (ECC_INDICATOR_M << 3) | mask;
        int bch = value << 10;
        for (int i = 4; i >= 0; i--) {
            if (((bch >> (i + 10)) & 1) != 0) {
                bch ^= 0x537 << i;
            }
        }
        int format = ((value << 10) | bch) ^ 0x5412;

        // Both copies run most-significant bit first. Placing bit 0 first
        // instead is a symbol that still scans as far as the format block, then
        // unmasks with whichever mask the reversed word happens to name — so it
        // fails as a checksum error rather than as anything that points here.
        for (int i = 0; i <= 5; i++) {
            m[8][i] = bitOf(format, 14 - i);
        }
        m[8][7] = bitOf(format, 8);
        m[8][8] = bitOf(format, 7);
        m[7][8] = bitOf(format, 6);
        for (int i = 9; i <= 14; i++) {
            m[14 - i][8] = bitOf(format, 14 - i);
        }

        // The second copy is 7 modules up the left column and 8 across the top
        // row — not 8 and 7. The extra module in that column is the always-dark
        // one at [size - 8][8], which is not part of the format information.
        for (int i = 0; i <= 6; i++) {
            m[size - 1 - i][8] = bitOf(format, 14 - i);
        }
        for (int i = 7; i <= 14; i++) {
            m[8][size - 15 + i] = bitOf(format, 14 - i);
        }
    }

    /** BCH(18,6) version information, for versions 7 and up. */
    private static void writeVersion(boolean[][] m, int size, int version) {
        int bch = version << 12;
        for (int i = 5; i >= 0; i--) {
            if (((bch >> (i + 12)) & 1) != 0) {
                bch ^= 0x1F25 << i;
            }
        }
        int info = (version << 12) | bch;
        for (int i = 0; i < 18; i++) {
            boolean dark = bitOf(info, i);
            m[i / 3][size - 11 + i % 3] = dark;
            m[size - 11 + i % 3][i / 3] = dark;
        }
    }

    private static boolean bitOf(int value, int index) {
        return ((value >> index) & 1) == 1;
    }

    /**
     * The four specified penalty rules. Only the relative ordering matters: any
     * mask decodes correctly as long as the format bits name the one applied, so
     * this steers scan reliability rather than correctness.
     */
    private static int penalty(boolean[][] m, int size) {
        int score = 0;

        // Rule 1: runs of five or more of the same shade.
        for (int i = 0; i < size; i++) {
            score += runPenalty(m, size, i, true);
            score += runPenalty(m, size, i, false);
        }

        // Rule 2: every 2x2 block of one shade.
        for (int r = 0; r < size - 1; r++) {
            for (int c = 0; c < size - 1; c++) {
                boolean v = m[r][c];
                if (v == m[r][c + 1] && v == m[r + 1][c] && v == m[r + 1][c + 1]) {
                    score += 3;
                }
            }
        }

        // Rule 3: the finder-like 1:1:3:1:1 sequence with four light modules.
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (c + 11 <= size && (finderLike(m, r, c, true, false)
                    || finderLike(m, r, c, true, true))) {
                    score += 40;
                }
                if (r + 11 <= size && (finderLike(m, r, c, false, false)
                    || finderLike(m, r, c, false, true))) {
                    score += 40;
                }
            }
        }

        // Rule 4: drift away from an even split of dark and light.
        int dark = 0;
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (m[r][c]) {
                    dark++;
                }
            }
        }
        int percent = dark * 100 / (size * size);
        score += Math.abs(percent - 50) / 5 * 10;
        return score;
    }

    private static int runPenalty(boolean[][] m, int size, int line, boolean horizontal) {
        int score = 0;
        int run = 1;
        for (int i = 1; i < size; i++) {
            boolean prev = horizontal ? m[line][i - 1] : m[i - 1][line];
            boolean cur = horizontal ? m[line][i] : m[i][line];
            if (cur == prev) {
                run++;
                continue;
            }
            if (run >= 5) {
                score += 3 + (run - 5);
            }
            run = 1;
        }
        if (run >= 5) {
            score += 3 + (run - 5);
        }
        return score;
    }

    private static final boolean[] FINDER_RUN = {
        true, false, true, true, true, false, true,
        false, false, false, false,
    };

    private static boolean finderLike(boolean[][] m, int r, int c,
                                      boolean horizontal, boolean reversed) {
        for (int i = 0; i < 11; i++) {
            boolean want = FINDER_RUN[reversed ? 10 - i : i];
            boolean got = horizontal ? m[r][c + i] : m[r + i][c];
            if (got != want) {
                return false;
            }
        }
        return true;
    }

    /** A most-significant-bit-first bit accumulator over a fixed byte budget. */
    private static final class BitBuffer {
        private final byte[] data;
        private int bits;

        BitBuffer(int capacityBits) {
            this.data = new byte[(capacityBits + 7) / 8];
        }

        void append(int value, int count) {
            for (int i = count - 1; i >= 0; i--) {
                if (((value >> i) & 1) != 0) {
                    data[bits / 8] |= (byte) (1 << (7 - bits % 8));
                }
                bits++;
            }
        }

        int length() {
            return bits;
        }

        byte[] bytes() {
            return data;
        }
    }
}
