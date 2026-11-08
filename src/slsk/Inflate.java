package slsk;

import java.io.IOException;

/**
 * Minimal zlib/DEFLATE decoder (puff-style). Soulseek compresses search
 * results and share lists with zlib, and CLDC has no java.util.zip.
 */
final class Inflate {
    private static final short[] LBASE = { 3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31,
        35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258 };
    private static final short[] LEXT = { 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2,
        3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0 };
    private static final short[] DBASE = { 1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193,
        257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577 };
    private static final short[] DEXT = { 0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6,
        7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13 };
    private static final byte[] ORDER = { 16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15 };

    private final byte[] in;
    private int ip;
    private final int iend;
    private int bitbuf, bitcnt;
    private byte[] out;
    private int olen;
    private final int maxOut;

    private Inflate(byte[] in, int off, int len, int maxOut) {
        this.in = in; this.ip = off; this.iend = off + len; this.maxOut = maxOut;
        this.out = new byte[Math.min(Math.max(len * 4, 1024), maxOut)];
    }

    /** Decompress a zlib stream. Throws if output would exceed maxOut. */
    static byte[] zlib(byte[] b, int off, int len, int maxOut) throws IOException {
        if (len < 2) throw new IOException("zlib: short");
        int cmf = b[off] & 0xff, flg = b[off + 1] & 0xff;
        if ((cmf & 15) != 8 || ((cmf << 8) | flg) % 31 != 0 || (flg & 0x20) != 0)
            throw new IOException("zlib: bad header");
        Inflate z = new Inflate(b, off + 2, len - 2, maxOut);
        z.run();
        byte[] r = new byte[z.olen];
        System.arraycopy(z.out, 0, r, 0, z.olen);
        return r;
    }

    private int bits(int need) throws IOException {
        int val = bitbuf;
        while (bitcnt < need) {
            if (ip >= iend) throw new IOException("zlib: eof");
            val |= (in[ip++] & 0xff) << bitcnt;
            bitcnt += 8;
        }
        bitbuf = val >>> need;
        bitcnt -= need;
        return val & ((1 << need) - 1);
    }

    private void put(int v) throws IOException {
        if (olen == out.length) grow();
        out[olen++] = (byte) v;
    }

    private void grow() throws IOException {
        if (out.length >= maxOut) throw new IOException("zlib: too big");
        int n = Math.min(out.length * 2, maxOut);
        byte[] o = new byte[n];
        System.arraycopy(out, 0, o, 0, olen);
        out = o;
    }

    private void run() throws IOException {
        int last;
        do {
            last = bits(1);
            int type = bits(2);
            if (type == 0) stored();
            else if (type == 1) fixed();
            else if (type == 2) dynamic();
            else throw new IOException("zlib: bad block");
        } while (last == 0);
    }

    private void stored() throws IOException {
        bitbuf = 0; bitcnt = 0;
        if (ip + 4 > iend) throw new IOException("zlib: eof");
        int len = (in[ip] & 0xff) | ((in[ip + 1] & 0xff) << 8);
        int nlen = (in[ip + 2] & 0xff) | ((in[ip + 3] & 0xff) << 8);
        ip += 4;
        if (len != (~nlen & 0xffff)) throw new IOException("zlib: stored len");
        if (ip + len > iend) throw new IOException("zlib: eof");
        while (olen + len > out.length) grow();
        System.arraycopy(in, ip, out, olen, len);
        ip += len; olen += len;
    }

    // Huffman table: count[0..15], symbol[]
    private static int construct(short[] count, short[] symbol, int[] length, int off, int n) {
        for (int i = 0; i < 16; i++) count[i] = 0;
        for (int s = 0; s < n; s++) count[length[off + s]]++;
        if (count[0] == n) return 0;
        int left = 1;
        for (int len = 1; len < 16; len++) {
            left <<= 1;
            left -= count[len];
            if (left < 0) return left;
        }
        short[] offs = new short[16];
        for (int len = 1; len < 15; len++) offs[len + 1] = (short) (offs[len] + count[len]);
        for (int s = 0; s < n; s++)
            if (length[off + s] != 0) symbol[offs[length[off + s]]++] = (short) s;
        return left;
    }

    private int decode(short[] count, short[] symbol) throws IOException {
        int code = 0, first = 0, index = 0;
        for (int len = 1; len < 16; len++) {
            code |= bits(1);
            int c = count[len];
            if (code - c < first) return symbol[index + (code - first)];
            index += c;
            first += c;
            first <<= 1;
            code <<= 1;
        }
        throw new IOException("zlib: bad code");
    }

    private void codes(short[] lc, short[] ls, short[] dc, short[] ds) throws IOException {
        int sym;
        do {
            sym = decode(lc, ls);
            if (sym < 256) {
                put(sym);
            } else if (sym > 256) {
                sym -= 257;
                if (sym >= 29) throw new IOException("zlib: bad len");
                int len = LBASE[sym] + bits(LEXT[sym]);
                int ds2 = decode(dc, ds);
                if (ds2 >= 30) throw new IOException("zlib: bad dist");
                int dist = DBASE[ds2] + bits(DEXT[ds2]);
                if (dist > olen) throw new IOException("zlib: dist too far");
                while (olen + len > out.length) grow();
                int from = olen - dist;
                for (int i = 0; i < len; i++) out[olen++] = out[from + i];
            }
        } while (sym != 256);
    }

    private static short[] fLc, fLs, fDc, fDs;

    private void fixed() throws IOException {
        if (fLc == null) {
            int[] l = new int[288];
            int s = 0;
            for (; s < 144; s++) l[s] = 8;
            for (; s < 256; s++) l[s] = 9;
            for (; s < 280; s++) l[s] = 7;
            for (; s < 288; s++) l[s] = 8;
            short[] lc = new short[16], ls = new short[288];
            construct(lc, ls, l, 0, 288);
            int[] d = new int[30];
            for (s = 0; s < 30; s++) d[s] = 5;
            short[] dc = new short[16], ds = new short[30];
            construct(dc, ds, d, 0, 30);
            fLs = ls; fDc = dc; fDs = ds; fLc = lc;
        }
        codes(fLc, fLs, fDc, fDs);
    }

    private void dynamic() throws IOException {
        int nlen = bits(5) + 257, ndist = bits(5) + 1, ncode = bits(4) + 4;
        if (nlen > 286 || ndist > 30) throw new IOException("zlib: bad counts");
        int[] lengths = new int[320];
        for (int i = 0; i < ncode; i++) lengths[ORDER[i]] = bits(3);
        short[] lc = new short[16], ls = new short[288];
        if (construct(lc, ls, lengths, 0, 19) != 0) throw new IOException("zlib: bad codelen");
        int idx = 0;
        while (idx < nlen + ndist) {
            int sym = decode(lc, ls);
            if (sym < 16) {
                lengths[idx++] = sym;
            } else {
                int len = 0;
                if (sym == 16) {
                    if (idx == 0) throw new IOException("zlib: repeat");
                    len = lengths[idx - 1];
                    sym = 3 + bits(2);
                } else if (sym == 17) sym = 3 + bits(3);
                else sym = 11 + bits(7);
                if (idx + sym > nlen + ndist) throw new IOException("zlib: too many");
                while (sym-- > 0) lengths[idx++] = len;
            }
        }
        if (lengths[256] == 0) throw new IOException("zlib: no eob");
        int err = construct(lc, ls, lengths, 0, nlen);
        if (err < 0 || (err > 0 && nlen - lc[0] != 1)) throw new IOException("zlib: bad lit");
        short[] dc = new short[16], ds = new short[30];
        err = construct(dc, ds, lengths, nlen, ndist);
        if (err < 0 || (err > 0 && ndist - dc[0] != 1)) throw new IOException("zlib: bad dist tbl");
        codes(lc, ls, dc, ds);
    }
}
