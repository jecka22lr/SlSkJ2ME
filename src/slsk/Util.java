package slsk;

import java.io.IOException;
import java.io.InputStream;

final class Util {
    private Util() {}

    static byte[] utf8(String s) {
        try { return s.getBytes("UTF-8"); }
        catch (Exception e) { return s.getBytes(); }
    }

    static String decode(byte[] b, int off, int len) {
        try {
            return new String(b, off, len, "UTF-8");
        } catch (Exception e) {
            try { return new String(b, off, len, "ISO8859_1"); }
            catch (Exception e2) { return new String(b, off, len); }
        }
    }

    static void readFully(InputStream in, byte[] b, int off, int len) throws IOException {
        while (len > 0) {
            int r = in.read(b, off, len);
            if (r < 0) throw new IOException("connection closed");
            off += r; len -= r;
        }
    }

    static int readU32(InputStream in) throws IOException {
        byte[] b = new byte[4];
        readFully(in, b, 0, 4);
        return (b[0] & 0xff) | ((b[1] & 0xff) << 8) | ((b[2] & 0xff) << 16) | ((b[3] & 0xff) << 24);
    }

    static void skip(InputStream in, int n) throws IOException {
        byte[] t = new byte[Math.min(n, 4096)];
        while (n > 0) {
            int k = Math.min(n, t.length);
            readFully(in, t, 0, k);
            n -= k;
        }
    }

    /** Soulseek sends IPv4 as a LE uint32 whose high byte is the first octet. */
    static String ip(int v) {
        return ((v >>> 24) & 255) + "." + ((v >>> 16) & 255) + "." + ((v >>> 8) & 255) + "." + (v & 255);
    }

    static String baseName(String path) {
        int i = Math.max(path.lastIndexOf('\\'), path.lastIndexOf('/'));
        return i >= 0 ? path.substring(i + 1) : path;
    }

    static String parentDir(String path) {
        int i = path.lastIndexOf('\\');
        if (i <= 0) return "";
        String d = path.substring(0, i);
        int j = d.lastIndexOf('\\');
        return j >= 0 ? d.substring(j + 1) : d;
    }

    static String safeName(String s) {
        StringBuffer sb = new StringBuffer(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 32 || "\\/:*?\"<>|%#".indexOf(c) >= 0) c = '_';
            sb.append(c);
        }
        String r = sb.toString().trim();
        return r.length() == 0 ? "file" : r;
    }

    /** Percent-encode a file name for a file:// URL (JSR-75 wants escaped URLs). */
    static String urlEscape(String s) {
        byte[] b = utf8(s);
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < b.length; i++) {
            int c = b[i] & 0xff;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || "-_.!~'()".indexOf(c) >= 0) {
                sb.append((char) c);
            } else {
                sb.append('%');
                sb.append("0123456789ABCDEF".charAt(c >> 4));
                sb.append("0123456789ABCDEF".charAt(c & 15));
            }
        }
        return sb.toString();
    }

    /** zlib stream using stored (uncompressed) blocks: valid for any inflater, no deflate code needed. */
    static byte[] zlibStore(byte[] d, int len) {
        int blocks = Math.max(1, (len + 65534) / 65535);
        byte[] out = new byte[2 + len + blocks * 5 + 4];
        int o = 0;
        out[o++] = 0x78; out[o++] = 0x01;
        int p = 0;
        for (int b = 0; b < blocks; b++) {
            int n = Math.min(65535, len - p);
            out[o++] = (byte) (b == blocks - 1 ? 1 : 0);
            out[o++] = (byte) n; out[o++] = (byte) (n >> 8);
            out[o++] = (byte) ~n; out[o++] = (byte) (~n >> 8);
            System.arraycopy(d, p, out, o, n);
            o += n; p += n;
        }
        long a = 1, bb = 0;
        p = 0;
        while (p < len) {
            int end = Math.min(len, p + 5552);
            for (; p < end; p++) { a += d[p] & 0xff; bb += a; }
            a %= 65521; bb %= 65521;
        }
        out[o++] = (byte) (bb >> 8); out[o++] = (byte) bb; out[o++] = (byte) (a >> 8); out[o++] = (byte) a;
        return out;
    }

    static String decodeUrl(String s) {
        if (s.indexOf('%') < 0) return s;
        byte[] b = new byte[s.length()];
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length()) {
                try { b[n++] = (byte) Integer.parseInt(s.substring(i + 1, i + 3), 16); i += 2; continue; }
                catch (Exception e) {}
            }
            b[n++] = (byte) c;
        }
        return decode(b, 0, n);
    }

    static String size(long n) {
        if (n < 1024) return n + " B";
        if (n < 1024 * 1024) return (n / 1024) + " KB";
        long t = n * 10 / (1024 * 1024);
        if (t < 10240) return (t / 10) + "." + (t % 10) + " MB";
        t = n * 10 / (1024L * 1024 * 1024);
        return (t / 10) + "." + (t % 10) + " GB";
    }

    static String duration(int s) {
        int m = s / 60, r = s % 60;
        return m + ":" + (r < 10 ? "0" : "") + r;
    }
}
