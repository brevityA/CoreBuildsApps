import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.google.zxing.qrcode.decoder.Decoder;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.decoder.Mode;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;
import com.google.zxing.qrcode.encoder.QRCode;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Verifies {@code QrCode.java} against ZXing, which is the oracle and never a
 * dependency of the APK. Driven by {@code tools/check_qr.py}.
 *
 * <p>Three gates, because they answer different questions.
 *
 * <p><b>Symbol.</b> Every payload length the encoder supports, under all eight
 * masks, decoded straight from the module matrix with
 * {@code Decoder.decode(BitMatrix)} — no image, no binarizer, no detector. This
 * is purely "is this a valid QR symbol carrying exactly these bytes", and a bug
 * anywhere in the bit stream, Reed-Solomon, interleaving, placement, masking or
 * format information surfaces here.
 *
 * <p><b>Parity.</b> For the URLs the request screen really builds, our matrix
 * must be identical, module for module, to what ZXing's own encoder produces for
 * the same text at the same mask. This is the strongest statement available: not
 * "ours decodes" but "ours is the reference output". Restricted to payloads
 * ZXing encodes in byte mode with no ECI segment, since anything else is a
 * different encoding rather than a disagreement.
 *
 * <p><b>Scan.</b> The same URLs rendered as images and put through the whole
 * reader, at several module sizes. Stated as a comparison rather than an
 * absolute, deliberately: ZXing's <em>detector</em> misjudges roughly one in a
 * hundred pixel-exact synthetic symbols around version 10 and up, and it does
 * that to symbols its own encoder produced just as often as to ours — softening
 * the image the way a lens would does not change it. An absolute threshold here
 * would therefore be measuring the detector and would fail on input that is
 * provably correct, so the gate is that ours scans wherever ZXing's own output
 * scans.
 */
public class QrRoundTrip {

    private static final int QUIET = 4;
    private static final int[] SCAN_SCALES = {3, 4, 6};
    /** Version the request screen keeps URLs inside; see check_qr.py's BUDGET. */
    private static final int MAX_SHIPPED_VERSION = 11;
    private static final Decoder DECODER = new Decoder();

    private static Method encode;
    private static int failures = 0;
    private static int symbolChecks = 0;
    private static int parityChecks = 0;
    private static int scanChecks = 0;

    /** Renders the grid as 8-bit greyscale with the mandatory quiet zone. */
    private static final class GridSource extends LuminanceSource {
        private final byte[] pixels;

        GridSource(boolean[][] grid, int scale) {
            super((grid.length + QUIET * 2) * scale, (grid.length + QUIET * 2) * scale);
            int width = getWidth();
            pixels = new byte[width * getHeight()];
            Arrays.fill(pixels, (byte) 0xFF);
            for (int r = 0; r < grid.length; r++) {
                for (int c = 0; c < grid.length; c++) {
                    if (!grid[r][c]) {
                        continue;
                    }
                    for (int dy = 0; dy < scale; dy++) {
                        int y = (r + QUIET) * scale + dy;
                        for (int dx = 0; dx < scale; dx++) {
                            pixels[y * width + (c + QUIET) * scale + dx] = 0;
                        }
                    }
                }
            }
        }

        @Override
        public byte[] getRow(int y, byte[] row) {
            int width = getWidth();
            if (row == null || row.length < width) {
                row = new byte[width];
            }
            System.arraycopy(pixels, y * width, row, 0, width);
            return row;
        }

        @Override
        public byte[] getMatrix() {
            return pixels;
        }
    }

    private static boolean[][] encode(String text, int mask) throws Exception {
        return (boolean[][]) encode.invoke(null, text, mask);
    }

    private static String decodeMatrix(boolean[][] grid) throws Exception {
        BitMatrix matrix = new BitMatrix(grid.length, grid.length);
        for (int r = 0; r < grid.length; r++) {
            for (int c = 0; c < grid.length; c++) {
                if (grid[r][c]) {
                    matrix.set(c, r);
                }
            }
        }
        return DECODER.decode(matrix).getText();
    }

    private static boolean scans(boolean[][] grid, int scale, String want) {
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        try {
            return want.equals(new QRCodeReader()
                .decode(new BinaryBitmap(new HybridBinarizer(new GridSource(grid, scale))),
                    hints)
                .getText());
        } catch (Exception e) {
            return false;
        }
    }

    private static void fail(String message) {
        failures++;
        if (failures <= 25) {
            System.out.println("  FAIL " + message);
        }
    }

    private static String describe(String text) {
        String head = text.length() <= 45 ? text : text.substring(0, 42) + "...";
        return "[" + text.getBytes(StandardCharsets.UTF_8).length + "B] " + head;
    }

    private static void symbolGate(List<String> payloads) throws Exception {
        for (String text : payloads) {
            for (int mask = 0; mask < 8; mask++) {
                boolean[][] grid = encode(text, mask);
                if (grid == null) {
                    fail("symbol: encoder returned null for " + describe(text));
                    continue;
                }
                symbolChecks++;
                try {
                    if (!text.equals(decodeMatrix(grid))) {
                        fail("symbol: " + describe(text) + " mask=" + mask
                            + " decoded differently");
                    }
                } catch (Exception e) {
                    fail("symbol: " + describe(text) + " mask=" + mask + " -> "
                        + e.getClass().getSimpleName());
                }
            }
        }
    }

    private static void parityGate(List<String> urls) throws Exception {
        for (String url : urls) {
            QRCode reference = Encoder.encode(url, ErrorCorrectionLevel.M);
            if (reference.getMode() != Mode.BYTE) {
                // A different mode is a different encoding, not a disagreement.
                continue;
            }
            ByteMatrix expected = reference.getMatrix();
            boolean[][] ours = encode(url, reference.getMaskPattern());
            parityChecks++;
            if (ours == null || ours.length != expected.getWidth()) {
                fail("parity: " + describe(url) + " is "
                    + (ours == null ? "null" : ours.length + " modules")
                    + ", ZXing makes it " + expected.getWidth());
                continue;
            }
            int version = (ours.length - 17) / 4;
            if (version > MAX_SHIPPED_VERSION) {
                fail("parity: " + describe(url) + " needs version " + version
                    + "; the request screen is supposed to shed parameters to stay"
                    + " inside version " + MAX_SHIPPED_VERSION);
            }
            for (int r = 0; r < ours.length; r++) {
                for (int c = 0; c < ours.length; c++) {
                    if (ours[r][c] != (expected.get(c, r) == 1)) {
                        fail("parity: " + describe(url) + " differs from ZXing's own"
                            + " output at row " + r + ", column " + c);
                        r = ours.length;
                        break;
                    }
                }
            }
        }
    }

    private static void scanGate(List<String> urls) throws Exception {
        int oursScanned = 0;
        int referenceScanned = 0;
        for (String url : urls) {
            boolean[][] ours = encode(url, -1);
            if (ours == null) {
                fail("scan: no symbol for " + describe(url));
                continue;
            }
            QRCode reference = Encoder.encode(url, ErrorCorrectionLevel.M);
            ByteMatrix rm = reference.getMatrix();
            boolean[][] theirs = new boolean[rm.getWidth()][rm.getWidth()];
            for (int r = 0; r < rm.getWidth(); r++) {
                for (int c = 0; c < rm.getWidth(); c++) {
                    theirs[r][c] = rm.get(c, r) == 1;
                }
            }
            for (int scale : SCAN_SCALES) {
                scanChecks++;
                if (scans(ours, scale, url)) {
                    oursScanned++;
                }
                if (scans(theirs, scale, url)) {
                    referenceScanned++;
                }
            }
        }
        System.out.println("  ours scanned " + oursScanned + "/" + scanChecks
            + ", ZXing's own output " + referenceScanned + "/" + scanChecks);
        if (oursScanned < referenceScanned) {
            fail("scan: ours scanned " + (referenceScanned - oursScanned)
                + " fewer times than ZXing's own symbols for the same URLs");
        }
    }

    public static void main(String[] args) throws Exception {
        encode = Class.forName("tv.corebuilds.iconpack.QrCode")
            .getDeclaredMethod("encode", String.class, int.class);
        encode.setAccessible(true);

        // Every payload length the encoder claims to support, so both character
        // count widths and all twenty versions are exercised at their edges.
        List<String> lengths = new ArrayList<>();
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            + "-._~:/?#[]@!$&'()*+,;=%";
        for (int len = 1; len <= 666; len++) {
            Random rnd = new Random(len * 2654435761L);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < len; i++) {
                sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
            }
            lengths.add(sb.toString());
        }
        // Repetitive input, which is what makes one mask score far better than
        // the rest, and multi-byte input, because a launcher label is UTF-8.
        lengths.add("A".repeat(400));
        lengths.add(" ".repeat(300));
        lengths.add("1234567890".repeat(40));
        lengths.add("Смотрёшка ТВ");
        lengths.add("テレビ東京");
        lengths.add("قناة الجزيرة");
        lengths.add("Télé-Loisirs");

        List<String> urls = Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8);
        urls.removeIf(String::isBlank);

        System.out.println("symbol gate: " + lengths.size() + " payloads x 8 masks");
        symbolGate(lengths);
        System.out.println("parity gate: " + urls.size() + " real prefill URLs vs ZXing's encoder");
        parityGate(urls);
        System.out.println("scan gate:   " + urls.size() + " URLs x " + SCAN_SCALES.length
            + " module sizes, ours against ZXing's own symbols");
        scanGate(urls);

        System.out.println();
        System.out.println(symbolChecks + " symbol decodes, " + parityChecks
            + " matrices compared, " + scanChecks + " scans each way, "
            + failures + " failed");
        if (failures > 25) {
            System.out.println("(" + (failures - 25) + " further failures not shown)");
        }
        System.exit(failures == 0 ? 0 : 1);
    }
}
