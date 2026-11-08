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
