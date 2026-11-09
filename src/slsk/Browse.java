package slsk;

import java.io.IOException;
import java.util.Vector;

/** A user's decompressed share list, indexed by folder without building every file object. */
public final class Browse {
    public final String user;
    public final String[] dirs;
    private final byte[] raw;
    private final int[] offs, counts;
    public final int fileCount;

    private Browse(String user, String[] dirs, byte[] raw, int[] offs, int[] counts, int files) {
        this.user = user; this.dirs = dirs; this.raw = raw; this.offs = offs; this.counts = counts;
        this.fileCount = files;
    }

    static Browse parse(String user, byte[] raw) throws IOException {
        Rd r = new Rd(raw, 0, raw.length);
        int n = r.u32();
        if (n < 0 || n > 200000) throw new IOException("bad dir count");
        Vector names = new Vector(), o = new Vector(), c = new Vector();
        int total = 0;
        for (int i = 0; i < n; i++) {
            String d = r.str();
            int nf = r.u32();
            int at = r.p;
            for (int j = 0; j < nf; j++) {
                r.u8(); r.skipStr(); r.skip(8); r.skipStr();
                int na = r.u32();
                r.skip(na * 8);
            }
            if (nf > 0) {
                names.addElement(d);
                o.addElement(new Integer(at));
                c.addElement(new Integer(nf));
                total += nf;
            }
        }
        String[] ds = new String[names.size()];
        int[] os = new int[ds.length], cs = new int[ds.length];
        for (int i = 0; i < ds.length; i++) {
            ds[i] = (String) names.elementAt(i);
            os[i] = ((Integer) o.elementAt(i)).intValue();
            cs[i] = ((Integer) c.elementAt(i)).intValue();
        }
        return new Browse(user, ds, raw, os, cs, total);
    }

    public int count(int dir) { return counts[dir]; }

    public Result[] files(int dir) throws IOException {
        Rd r = new Rd(raw, offs[dir], raw.length - offs[dir]);
        Result[] out = new Result[counts[dir]];
        for (int j = 0; j < out.length; j++) {
            r.u8();
            String name = r.str();
            long size = r.u64();
            r.skipStr();
            int na = r.u32();
            Result x = new Result();
            for (int k = 0; k < na; k++) {
                int ac = r.u32(), av = r.u32();
                if (ac == 0) x.bitrate = av;
                else if (ac == 1) x.duration = av;
            }
            x.user = user;
            x.file = dirs[dir] + "\\" + name;
            x.size = size;
            x.slot = true;
            out[j] = x;
        }
        return out;
    }
}
