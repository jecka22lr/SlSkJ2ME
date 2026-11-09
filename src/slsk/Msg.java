package slsk;

/** Little-endian message builder for Soulseek frames. */
final class Msg {
    private byte[] b = new byte[64];
    private int n;

    private void ensure(int k) {
        if (n + k > b.length) {
            byte[] nb = new byte[Math.max(b.length * 2, n + k)];
            System.arraycopy(b, 0, nb, 0, n);
            b = nb;
        }
    }

    Msg u8(int v) { ensure(1); b[n++] = (byte) v; return this; }

    Msg u32(int v) {
        ensure(4);
        b[n++] = (byte) v; b[n++] = (byte) (v >>> 8);
        b[n++] = (byte) (v >>> 16); b[n++] = (byte) (v >>> 24);
        return this;
    }

    Msg u64(long v) { u32((int) v); return u32((int) (v >>> 32)); }

    Msg bool(boolean v) { return u8(v ? 1 : 0); }

    int length() { return n; }

    /** zlib-wrap the payload (for share lists and search results). */
    Msg zlib() {
        byte[] z = Util.zlibStore(b, n);
        Msg m = new Msg();
        return m.raw(z);
    }

    Msg raw(byte[] d) { ensure(d.length); System.arraycopy(d, 0, b, n, d.length); n += d.length; return this; }

    Msg str(String s) { byte[] d = Util.utf8(s); u32(d.length); return raw(d); }

    /** Payload bytes with no framing (used for the raw file offset). */
    byte[] toRaw() {
        byte[] f = new byte[n];
        System.arraycopy(b, 0, f, 0, n);
        return f;
    }

    /** Server / peer message: [u32 len][u32 code][payload]. */
    byte[] frame(int code) {
        byte[] f = new byte[8 + n];
        put32(f, 0, 4 + n);
        put32(f, 4, code);
        System.arraycopy(b, 0, f, 8, n);
        return f;
    }

    /** Peer-init message: [u32 len][u8 code][payload]. */
    byte[] initFrame(int code) {
        byte[] f = new byte[5 + n];
        put32(f, 0, 1 + n);
        f[4] = (byte) code;
        System.arraycopy(b, 0, f, 5, n);
        return f;
    }

    static void put32(byte[] f, int o, int v) {
        f[o] = (byte) v; f[o + 1] = (byte) (v >>> 8);
        f[o + 2] = (byte) (v >>> 16); f[o + 3] = (byte) (v >>> 24);
    }
}
