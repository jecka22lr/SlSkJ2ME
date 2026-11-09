package slsk;

import java.io.IOException;

/** Little-endian reader over a message payload. */
final class Rd {
    final byte[] b;
    int p;
    final int end;

    Rd(byte[] b, int off, int len) { this.b = b; this.p = off; this.end = off + len; }

    int left() { return end - p; }

    private void need(int k) throws IOException {
        if (k < 0 || p + k > end) throw new IOException("short message");
    }

    int u8() throws IOException { need(1); return b[p++] & 0xff; }

    boolean bool() throws IOException { return u8() != 0; }

    int u32() throws IOException {
        need(4);
        int v = (b[p] & 0xff) | ((b[p + 1] & 0xff) << 8) | ((b[p + 2] & 0xff) << 16) | ((b[p + 3] & 0xff) << 24);
        p += 4;
        return v;
    }

    long u64() throws IOException {
        long lo = u32() & 0xffffffffL;
        long hi = u32() & 0xffffffffL;
        return lo | (hi << 32);
    }

    String str() throws IOException {
        int len = u32();
        need(len);
        String s = Util.decode(b, p, len);
        p += len;
        return s;
    }

    void skipStr() throws IOException { int len = u32(); skip(len); }

    void skip(int k) throws IOException { need(k); p += k; }
}
