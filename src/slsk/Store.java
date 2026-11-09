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
        final String name, where;

        Sink(FileConnection fc, long offset, String name, String where) throws IOException {
            this.fc = fc; this.offset = offset; this.name = name; this.where = where;
            os = fc.openOutputStream(offset);
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
    static Sink open(String dir, Transfer t) throws IOException {
        String name = Util.safeName(Util.baseName(t.file));
        try {
            return openIn(dir, name);
        } catch (SecurityException e) {
            String alt = fallbackDir();
            if (alt != null && !alt.equals(dir)) {
                try { return openIn(alt, name); } catch (SecurityException e2) {}
            }
            throw new IOException("phone denied file permission for " + dir);
        }
    }

    private static Sink openIn(String dir, String name) throws IOException {
        if (!dir.endsWith("/")) dir += "/";
        ensureDir(dir);
        String url = dir + Util.urlEscape(name);
        FileConnection fc = (FileConnection) Connector.open(url, Connector.READ_WRITE);
        try {
            if (!fc.exists()) fc.create();
            return new Sink(fc, fc.fileSize(), name, dir + name);
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
