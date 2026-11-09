package slsk;

import java.io.InputStream;
import java.util.Vector;
import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Image;

/**
 * How chat looks: nickname colours (own / online / offline), the chat style (our own view coloured
 * from the phone theme, or the phone's native form), and the background (theme, a colour, or an image
 * that fills, stretches or tiles). Saved in RMS.
 */
final class ChatColors {
    static final String[] NAMES = { "Blue", "Green", "Red", "Orange", "Purple", "Teal", "Pink", "Brown", "Gray", "Black" };
    static final int[] VALUES = { 0x1d6fa5, 0x2e8b3a, 0xc0392b, 0xd9822b, 0x7d3c98, 0x138d90, 0xc2185b, 0x8d6e63, 0x8a8a8a, 0x000000 };

    static final int STYLE_THEME = 0, STYLE_NATIVE = 1;
    static final int BG_THEME = 0, BG_COLOR = 1, BG_IMAGE = 2;
    static final int FIT_FILL = 0, FIT_STRETCH = 1, FIT_TILE = 2;

    /** Palette indexes: own, online, offline. */
    static int own = 0, online = 1, offline = 8;
    static int style = STYLE_THEME;
    static int bgMode = BG_THEME;
    static int bgColor = 0x202830;
    static int fit = FIT_FILL;
    static String bgImage = "";
    /** How much the image is faded (percent, 0-90) and toward what: 0 auto, 1 darker, 2 lighter. */
    static int dim = 50, dimDir = 0;

    static Display display;

    private static Image source;          // the picked image, decoded
    private static String sourceFor = ""; // URL it came from
    private static Image scaled;          // ready-to-draw background for the last size asked
    private static int scaledW, scaledH, scaledFit = -1, scaledDim = -1, scaledDir = -1;
    private static int imageTone = -1;    // average colour of the dimmed image, for picking text colour
    static String imageError;

    static void load() {
        Vector v = new Vector();
        SlskMIDlet.loadStrings("slskme-col", v);
        if (v.size() >= 3) {
            own = idx((String) v.elementAt(0), own, VALUES.length);
            online = idx((String) v.elementAt(1), online, VALUES.length);
            offline = idx((String) v.elementAt(2), offline, VALUES.length);
        }
        if (v.size() >= 7) {
            style = idx((String) v.elementAt(3), style, 2);
            bgMode = idx((String) v.elementAt(4), bgMode, 3);
            try { bgColor = Integer.parseInt((String) v.elementAt(5)) & 0xffffff; } catch (Exception e) {}
            bgImage = (String) v.elementAt(6);
        }
        if (v.size() >= 8) fit = idx((String) v.elementAt(7), fit, 3);
        if (v.size() >= 10) {
            dim = idx((String) v.elementAt(8), dim, 91);
            dimDir = idx((String) v.elementAt(9), dimDir, 3);
        }
    }

    static void save() {
        Vector v = new Vector();
        v.addElement("" + own);
        v.addElement("" + online);
        v.addElement("" + offline);
        v.addElement("" + style);
        v.addElement("" + bgMode);
        v.addElement("" + bgColor);
        v.addElement(bgImage == null ? "" : bgImage);
        v.addElement("" + fit);
        v.addElement("" + dim);
        v.addElement("" + dimDir);
        SlskMIDlet.saveStrings("slskme-col", v);
    }

    private static int idx(String s, int def, int n) {
        try {
            int i = Integer.parseInt(s);
            return i >= 0 && i < n ? i : def;
        } catch (Exception e) { return def; }
    }

    // ---------------------------------------------------------------- colours

    private static int theme(int which, int def) {
        try { if (display != null) return display.getColor(which) & 0xffffff; } catch (Throwable e) {}
        return def;
    }

    /** Background behind the text (for an image: its average tone after dimming). */
    static int background() {
        if (bgMode == BG_COLOR) return bgColor;
        if (bgMode == BG_IMAGE && imageTone >= 0) return imageTone;
        return theme(Display.COLOR_BACKGROUND, 0xffffff);
    }

    /** Message text colour: the theme's, unless that's unreadable on our background. */
    static int text() {
        int bg = background();
        if (bgMode == BG_THEME) return readable(theme(Display.COLOR_FOREGROUND, 0x000000), bg);
        return lum(bg) < 128 ? 0xf0f0f0 : 0x101010;
    }

    static int name(int paletteIndex) { return readable(VALUES[paletteIndex], background()); }

    static int lum(int c) {
        return (((c >> 16) & 255) * 299 + ((c >> 8) & 255) * 587 + (c & 255) * 114) / 1000;
    }

    /** Lighten (on dark backgrounds) or darken (on light ones) until fg stands out from bg. */
    static int readable(int fg, int bg) {
        boolean darkBg = lum(bg) < 128;
        int c = fg;
        // only as far as needed, in small steps, so the colour keeps its hue
        for (int i = 0; i < 12 && Math.abs(lum(c) - lum(bg)) < 80; i++) c = mix(c, darkBg ? 0xffffff : 0x000000, 15);
        return c;
    }

    /** pct% of b mixed into a. */
    static int mix(int a, int b, int pct) {
        int r = (((a >> 16) & 255) * (100 - pct) + ((b >> 16) & 255) * pct) / 100;
        int g = (((a >> 8) & 255) * (100 - pct) + ((b >> 8) & 255) * pct) / 100;
        int bl = ((a & 255) * (100 - pct) + (b & 255) * pct) / 100;
        return (r << 16) | (g << 8) | bl;
    }

    // ---------------------------------------------------------------- background image

    /** Biggest picture we'll decode: phones decode the whole thing at ~4 bytes per pixel. */
    static final int MAX_PIXELS = 2000000;

    /**
     * Width and height from the file header (PNG, GIF, JPEG, BMP) without decoding it,
     * or null if it can't tell. Reads only the header bytes.
     */
    static int[] imageSize(InputStream in) throws java.io.IOException {
        int b0 = in.read(), b1 = in.read();
        if (b0 < 0 || b1 < 0) return null;
        if (b0 == 0x89 && b1 == 'P') { // PNG: IHDR width/height at bytes 16..23, big-endian
            skip(in, 14);
            return new int[] { be32(in), be32(in) };
        }
        if (b0 == 'G' && b1 == 'I') { // GIF: logical screen size at bytes 6..9, little-endian
            skip(in, 4);
            int w = in.read() | in.read() << 8, h = in.read() | in.read() << 8;
            return new int[] { w, h };
        }
        if (b0 == 'B' && b1 == 'M') { // BMP: width/height at 18..25, little-endian (height may be negative)
            skip(in, 16);
            int w = le32(in), h = le32(in);
            return new int[] { Math.abs(w), Math.abs(h) };
        }
        if (b0 == 0xff && b1 == 0xd8) { // JPEG: walk the segments to the frame header (SOFn)
            while (true) {
                int m = in.read();
                while (m == 0xff) m = in.read();
                if (m < 0) return null;
                if (m == 0xd8 || m == 0x01 || (m >= 0xd0 && m <= 0xd7)) continue; // no length
                int len = in.read() << 8 | in.read();
                if (len < 2) return null;
                boolean sof = m >= 0xc0 && m <= 0xcf && m != 0xc4 && m != 0xc8 && m != 0xcc;
                if (sof) {
                    in.read(); // precision
                    int h = in.read() << 8 | in.read();
                    int w = in.read() << 8 | in.read();
                    return new int[] { w, h };
                }
                if (m == 0xd9 || m == 0xda) return null; // end / scan data before any frame header
                skip(in, len - 2);
            }
        }
        return null;
    }

    private static void skip(InputStream in, long n) throws java.io.IOException {
        while (n > 0) {
            long k = in.skip(n);
            if (k <= 0) { if (in.read() < 0) throw new java.io.IOException("short file"); k = 1; }
            n -= k;
        }
    }

    private static int be32(InputStream in) throws java.io.IOException {
        return in.read() << 24 | in.read() << 16 | in.read() << 8 | in.read();
    }

    private static int le32(InputStream in) throws java.io.IOException {
        return in.read() | in.read() << 8 | in.read() << 16 | in.read() << 24;
    }

    /** null if w x h is fine to use, else the message to show. */
    static String tooBig(int w, int h) {
        if ((long) w * h <= MAX_PIXELS) return null;
        return "That picture is " + w + "x" + h + ". Pick one under about 2 megapixels (for example 1600x1200), "
            + "ideally your screen size, so the phone doesn't run out of memory.";
    }

    /** Decode the picked image. Call off the UI thread. Returns null if ok, else why not. */
    static String loadImage() {
        if (bgImage == null || bgImage.length() == 0) return "No image picked";
        if (source != null && bgImage.equals(sourceFor)) return null;
        source = null; scaled = null; imageTone = -1; imageError = null;
        FileConnection fc = null;
        InputStream in = null;
        try {
            fc = (FileConnection) Connector.open(bgImage, Connector.READ);
            // check the size from the header first: decoding a big photo can take the whole heap
            in = fc.openInputStream();
            int[] wh = null;
            try { wh = imageSize(in); } catch (Throwable e) {}
            in.close();
            in = null;
            if (wh != null && tooBig(wh[0], wh[1]) != null) {
                imageError = tooBig(wh[0], wh[1]);
                fc.close();
                return imageError;
            }
            in = fc.openInputStream();
            source = Image.createImage(in);
            if (tooBig(source.getWidth(), source.getHeight()) != null) { // header said nothing; check now
                imageError = tooBig(source.getWidth(), source.getHeight());
                source = null;
                in.close();
                fc.close();
                return imageError;
            }
            sourceFor = bgImage;
        } catch (OutOfMemoryError e) {
            imageError = "Image too big for the phone's memory. Try a smaller one (around the screen size).";
        } catch (SecurityException e) {
            imageError = "The phone won't let the app read that image";
        } catch (Throwable e) {
            imageError = "Can't open that image: " + e.getMessage();
        }
        try { if (in != null) in.close(); } catch (Throwable e) {}
        try { if (fc != null) fc.close(); } catch (Throwable e) {}
        return imageError;
    }

    static boolean imageReady() { return source != null && bgImage.equals(sourceFor); }

    /**
     * The background for a w x h view, dimmed toward black or white so text stays readable.
     * Built one source row at a time, so a big photo never needs its full pixel array in memory.
     */
    static Image backgroundImage(int w, int h) {
        if (!imageReady() || w <= 0 || h <= 0) return null;
        if (scaled != null && scaledW == w && scaledH == h && scaledFit == fit && scaledDim == dim && scaledDir == dimDir) return scaled;
        scaled = null;
        try {
            int sw = source.getWidth(), sh = source.getHeight();
            int[] out = new int[w * h];
            int[] row = new int[sw];
            // source area to map onto the view
            int cx = 0, cy = 0, cw = sw, ch = sh;
            if (fit == FIT_FILL) {
                // crop to the view's aspect ratio, centred
                if ((long) sw * h > (long) sh * w) { cw = (int) ((long) sh * w / h); cx = (sw - cw) / 2; }
                else { ch = (int) ((long) sw * h / w); cy = (sh - ch) / 2; }
            }
            long tr = 0, tg = 0, tb = 0;
            int lastSy = -1;
            for (int y = 0; y < h; y++) {
                int sy = fit == FIT_TILE ? y % sh : cy + (int) ((long) y * ch / h);
                if (sy != lastSy) { source.getRGB(row, 0, sw, 0, sy, sw, 1); lastSy = sy; }
                for (int x = 0; x < w; x++) {
                    int sx = fit == FIT_TILE ? x % sw : cx + (int) ((long) x * cw / w);
                    out[y * w + x] = row[sx] & 0xffffff;
                }
            }
            // dim: pull everything toward black or white (auto: whichever the picture is closer to)
            boolean lighter;
            if (dimDir == 1) lighter = false;
            else if (dimDir == 2) lighter = true;
            else {
                long sum = 0;
                for (int i = 0; i < out.length; i += 7) sum += lum(out[i]);
                lighter = sum / ((out.length + 6) / 7) >= 128;
            }
            int target = lighter ? 0xffffff : 0x000000;
            for (int i = 0; i < out.length; i++) {
                int c = dim > 0 ? mix(out[i], target, dim) : out[i];
                out[i] = 0xff000000 | c;
                if ((i & 15) == 0) { tr += (c >> 16) & 255; tg += (c >> 8) & 255; tb += c & 255; }
            }
            long n = (out.length + 15) / 16;
            imageTone = (int) ((tr / n) << 16 | (tg / n) << 8 | (tb / n));
            scaled = Image.createRGBImage(out, w, h, false);
            scaledW = w; scaledH = h; scaledFit = fit; scaledDim = dim; scaledDir = dimDir;
        } catch (OutOfMemoryError e) {
            scaled = null;
            imageError = "Not enough memory to draw the background image";
        } catch (Throwable e) {
            scaled = null;
            imageError = "Can't draw the background image: " + e;
        }
        return scaled;
    }

    /** Drop the cached copies (after picking another image or changing the fit). */
    static void forget() { scaled = null; scaledFit = -1; if (!bgImage.equals(sourceFor)) { source = null; imageTone = -1; } }
}
