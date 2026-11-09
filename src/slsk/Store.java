package slsk;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.Vector;
import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import javax.microedition.io.file.FileSystemRegistry;

/** Writes downloads with JSR-75. Resumes from whatever is already on disk. */
final class Store {
    private Store() {}

    static final class Sink {
        final FileConnection fc;
        OutputStream os;
        final long offset;
        final String name, where, dir;
        /** Where the data is going right now (the .part file while unfinished). */
        String url;
        final boolean part;

        Sink(FileConnection fc, long offset, String dir, String name, String url, boolean part) throws IOException {
            this.fc = fc; this.offset = offset; this.dir = dir; this.name = name; this.url = url; this.part = part;
            this.where = dir + name + (part ? PART : "");
            os = fc.openOutputStream(offset);
        }

        /** Download complete: drop the .part suffix (picks "name (2).ext" if the name is taken). Returns the final URL. */
        String finish() throws IOException {
            try { os.flush(); } catch (Throwable e) {}
            try { os.close(); } catch (Throwable e) {}
            if (!part) { fc.close(); return url; }
            String target = name;
            for (int n = 2; n < 100 && exists(dir + Util.urlEscape(target)); n++) target = numbered(name, n);
            fc.rename(target);
            fc.close();
            url = dir + Util.urlEscape(target);
            return url;
        }

        void write(byte[] b, int off, int len) throws IOException { os.write(b, off, len); }

        void truncate() throws IOException {
            os.close();
            fc.truncate(0);
            os = fc.openOutputStream(0);
        }

        void close() {
            try { os.flush(); } catch (Throwable e) {}
            try { os.close(); } catch (Throwable e) {}
            try { fc.close(); } catch (Throwable e) {}
        }
    }

    /** Open (or resume) the download file. Falls back to the phone's music folder if denied. */
    static final String PART = ".part";

    static String numbered(String name, int n) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) + " (" + n + ")" + name.substring(dot) : name + " (" + n + ")";
    }

    private static boolean exists(String url) { return sizeOf(url) >= 0; }

    /** Where a finished download most likely is, for downloads saved before we recorded paths. */
    static String guessPath(String dir, Transfer t) {
        if (!dir.endsWith("/")) dir += "/";
        if (t.subdir != null && t.subdir.length() > 0) dir += Util.urlEscape(Util.safeName(t.subdir)) + "/";
        return dir + Util.urlEscape(Util.safeName(Util.baseName(t.file)));
    }

    static Sink open(String dir, Transfer t) throws IOException {
        if (!dir.endsWith("/")) dir += "/";
        if (t.subdir != null && t.subdir.length() > 0) dir += Util.urlEscape(Util.safeName(t.subdir)) + "/";
        String name = Util.safeName(Util.baseName(t.file));
        try {
            return openIn(dir, name, t.size);
        } catch (SecurityException e) {
            String alt = fallbackDir();
            if (alt != null && !alt.equals(dir)) {
                try { return openIn(alt, name, t.size); } catch (SecurityException e2) {}
            }
            throw new IOException("phone denied file permission for " + dir);
        }
    }

    private static long sizeOf(String url) {
        long n = -1;
        FileConnection f = null;
        try {
            f = (FileConnection) Connector.open(url, Connector.READ);
            if (f.exists()) n = f.fileSize();
        } catch (Throwable e) {
            n = -1;
        }
        if (f != null) { try { f.close(); } catch (Throwable e) {} }
        return n;
    }

    private static Sink openIn(String dir, String name, long size) throws IOException {
        if (!dir.endsWith("/")) dir += "/";
        ensureDir(dir);
        // unfinished data lives in "name.part"; older versions wrote straight to "name", so resume that if it's there
        String full = dir + Util.urlEscape(name);
        long have = sizeOf(full);
        // a smaller file is an old-style partial download; same size means it's already done;
        // bigger is some other file with that name, so download next to it
        boolean part = have < 0 || have > size;
        String url = part ? full + Util.urlEscape(PART) : full;
        FileConnection fc = (FileConnection) Connector.open(url, Connector.READ_WRITE);
        try {
            if (!fc.exists()) fc.create();
            return new Sink(fc, fc.fileSize(), dir, name, url, part);
        } catch (IOException e) {
            fc.close();
            throw e;
        } catch (SecurityException e) {
            fc.close();
            throw e;
        }
    }

    /** Creates dir if missing. Returns true if it had to create it. */
    private static boolean ensureDir(String dir) throws IOException {
        FileConnection d = (FileConnection) Connector.open(dir, Connector.READ_WRITE);
        try {
            if (d.exists()) return false;
            d.mkdir();
            return true;
        } finally {
            d.close();
        }
    }

    private static final String[] PROPS = { "fileconn.dir.memorycard", "fileconn.dir.music",
        "fileconn.dir.recordings", "fileconn.dir.tones", "fileconn.dir.videos", "fileconn.dir.photos",
        "fileconn.dir.graphics" };

    /** Folders worth trying, most useful first. Unsigned apps often may only write in some. */
    static Vector candidates(String configured) {
        Vector v = new Vector();
        add(v, configured);
        for (int i = 0; i < PROPS.length; i++) {
            String p = null;
            try { p = System.getProperty(PROPS[i]); } catch (Throwable e) {}
            if (p == null || p.length() == 0) continue;
            if (!p.endsWith("/")) p += "/";
            add(v, p + "Soulseek/");
            add(v, p);
        }
        try {
            Enumeration e = FileSystemRegistry.listRoots();
            while (e.hasMoreElements()) {
                String r = "file:///" + e.nextElement();
                if (!r.endsWith("/")) r += "/";
                add(v, r + "Soulseek/");
                add(v, r + "Data/Sounds/Soulseek/");
                add(v, r + "Sounds/Soulseek/");
            }
        } catch (Throwable e) {}
        return v;
    }

    private static void add(Vector v, String s) {
        if (s != null && s.length() > 0 && !v.contains(s)) v.addElement(s);
    }

    /** First candidate we can actually write to, or null. */
    static String findWritable(String configured) {
        Vector v = candidates(configured);
        for (int i = 0; i < v.size(); i++) {
            String d = (String) v.elementAt(i);
            if (probe(d) == null) return d;
        }
        return null;
    }

    private static String prop(String k) {
        try {
            String p = System.getProperty(k);
            if (p == null || p.length() == 0) return null;
            return p.endsWith("/") ? p : p + "/";
        } catch (Throwable e) { return null; }
    }

    private static final String[][] NAMES = {
        { "fileconn.dir.memorycard", "Memory card" }, { "fileconn.dir.music", "Music" },
        { "fileconn.dir.recordings", "Recordings" }, { "fileconn.dir.tones", "Tones" },
        { "fileconn.dir.videos", "Videos" }, { "fileconn.dir.photos", "Images" },
        { "fileconn.dir.graphics", "Graphics" }, { "fileconn.dir.private", "App private folder" } };

    /** "Memory card/Soulseek/" instead of "file:///E:/Soulseek/" where we can tell. */
    static String friendly(String url) {
        String best = null, rest = null;
        int bestLen = 0;
        for (int i = 0; i < NAMES.length; i++) {
            String p = prop(NAMES[i][0]);
            if (p != null && url.startsWith(p) && p.length() > bestLen) {
                best = NAMES[i][1]; rest = url.substring(p.length()); bestLen = p.length();
            }
        }
        if (best == null) {
            String u = url.toUpperCase();
            if (u.startsWith("FILE:///C:/")) { best = "Phone memory"; rest = url.substring(11); }
            else return Util.decodeUrl(url.startsWith("file:///") ? url.substring(8) : url);
        }
        return best + (rest.length() > 0 ? "/" + Util.decodeUrl(rest) : "");
    }

    static String fallbackDir() {
        try {
            String m = System.getProperty("fileconn.dir.music");
            if (m != null && m.length() > 0) return (m.endsWith("/") ? m : m + "/") + "Soulseek/";
        } catch (Throwable e) {}
        return null;
    }

    /**
     * Touch the download folder right after login, so the phone asks for file
     * permission while you're looking, instead of mid-download. Returns null if ok.
     */
    static String probe(String dir) {
        try {
            if (!dir.endsWith("/")) dir += "/";
            boolean made = ensureDir(dir);
            FileConnection f = (FileConnection) Connector.open(dir + "slskme.tmp", Connector.READ_WRITE);
            try {
                if (!f.exists()) f.create();
                OutputStream o = f.openOutputStream();
                o.write(1);
                o.close();
                f.delete();
            } finally {
                f.close();
            }
            // a test must not leave empty folders lying around; downloads recreate it when needed
            if (made) {
                try {
                    FileConnection d = (FileConnection) Connector.open(dir, Connector.READ_WRITE);
                    try { d.delete(); } finally { d.close(); }
                } catch (Throwable e) {}
            }
            return null;
        } catch (SecurityException e) {
            return "The phone denied writing to " + dir + ".";
        } catch (Throwable e) {
            return "Can't use download folder " + dir + ": " + e.getMessage();
        }
    }

    /** Best guess for a download folder: memory card first. */
    static String defaultDir() {
        // Nokia (S60, S40) and most JSR-75 phones say where the memory card is
        String card = prop("fileconn.dir.memorycard");
        if (card != null) return card + "Soulseek/";
        try {
            String best = null;
            Enumeration e = FileSystemRegistry.listRoots();
            while (e.hasMoreElements()) {
                String r = (String) e.nextElement();
                String u = r.toUpperCase();
                if (best == null) best = r;
                if (u.startsWith("E:") || u.indexOf("MEMORY") >= 0 || u.indexOf("CARD") >= 0
                        || u.startsWith("SD")) { best = r; break; }
            }
            if (best != null) return "file:///" + best + "Soulseek/";
        } catch (Throwable e) {}
        return "file:///E:/Soulseek/";
    }
}
