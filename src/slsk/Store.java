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

    private static void ensureDir(String dir) throws IOException {
        FileConnection d = (FileConnection) Connector.open(dir, Connector.READ_WRITE);
        try {
            if (!d.exists()) d.mkdir();
        } finally {
            d.close();
        }
    }

    private static final String[] PROPS = { "fileconn.dir.music", "fileconn.dir.memorycard",
        "fileconn.dir.recordings", "fileconn.dir.tones", "fileconn.dir.videos", "fileconn.dir.photos",
        "fileconn.dir.graphics", "fileconn.dir.private" };

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
            ensureDir(dir);
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
            return null;
        } catch (SecurityException e) {
            return "The phone denied writing to " + dir + ".";
        } catch (Throwable e) {
            return "Can't use download folder " + dir + ": " + e.getMessage();
        }
    }

    /** Best guess for a download folder: memory card first. */
    static String defaultDir() {
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
