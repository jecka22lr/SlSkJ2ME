package slsk;

/** Plain MD5 for CLDC (no java.security). Used for the login hash. */
final class MD5 {
    private static final int[] S = {
        7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
        5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
        4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
        6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21 };
    private static final int[] K = new int[64];
    static {
        // K[i] = floor(abs(sin(i+1)) * 2^32); Math.sin exists in CLDC 1.1
        for (int i = 0; i < 64; i++) {
            K[i] = (int) (long) (Math.abs(Math.sin(i + 1)) * 4294967296.0);
        }
    }

    static byte[] digest(byte[] msg) {
        int n = msg.length;
        int padLen = ((n + 8) / 64 + 1) * 64;
        byte[] m = new byte[padLen];
        System.arraycopy(msg, 0, m, 0, n);
        m[n] = (byte) 0x80;
        long bits = (long) n * 8;
        for (int i = 0; i < 8; i++) m[padLen - 8 + i] = (byte) (bits >>> (8 * i));

        int a0 = 0x67452301, b0 = 0xefcdab89, c0 = 0x98badcfe, d0 = 0x10325476;
        int[] w = new int[16];
        for (int off = 0; off < padLen; off += 64) {
            for (int i = 0; i < 16; i++) {
                int j = off + i * 4;
                w[i] = (m[j] & 0xff) | ((m[j + 1] & 0xff) << 8)
                     | ((m[j + 2] & 0xff) << 16) | ((m[j + 3] & 0xff) << 24);
            }
            int a = a0, b = b0, c = c0, d = d0;
            for (int i = 0; i < 64; i++) {
                int f, g;
                if (i < 16) { f = (b & c) | (~b & d); g = i; }
                else if (i < 32) { f = (d & b) | (~d & c); g = (5 * i + 1) & 15; }
                else if (i < 48) { f = b ^ c ^ d; g = (3 * i + 5) & 15; }
                else { f = c ^ (b | ~d); g = (7 * i) & 15; }
                int t = d;
                d = c;
                c = b;
                int x = a + f + K[i] + w[g];
                b = b + ((x << S[i]) | (x >>> (32 - S[i])));
                a = t;
            }
            a0 += a; b0 += b; c0 += c; d0 += d;
        }
        byte[] out = new byte[16];
        int[] r = { a0, b0, c0, d0 };
        for (int i = 0; i < 4; i++)
            for (int j = 0; j < 4; j++) out[i * 4 + j] = (byte) (r[i] >>> (8 * j));
        return out;
    }

    static String hex(String s) {
        byte[] d = digest(Util.utf8(s));
        StringBuffer sb = new StringBuffer(32);
        for (int i = 0; i < d.length; i++) {
            int v = d[i] & 0xff;
            sb.append("0123456789abcdef".charAt(v >> 4));
            sb.append("0123456789abcdef".charAt(v & 15));
        }
        return sb.toString();
    }
}
