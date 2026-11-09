package slsk;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;

/** Our shared files: scanned once from a folder on the phone, then searched and listed from memory. */
final class Shares {
    static final class Entry {
        String vpath;  // what peers see: Root\Sub\file.mp3
        String url;    // file:/// URL on the phone
        String lower;  // vpath lower-cased, for searching
        long size;
    }

    volatile boolean enabled;
    int maxFiles = 1500;
    private Vector entries = new Vector();
    private Hashtable byPath = new Hashtable();
    private int dirs;
    private byte[] cachedList;

    synchronized int files() { return entries.size(); }

    synchronized int dirs() { return dirs; }

    synchronized Entry find(String vpath) { return (Entry) byPath.get(vpath); }

    /** Scan a folder. Runs on a worker thread; can take a while on big folders. */
    int scan(String rootUrl) throws IOException {
        if (!rootUrl.endsWith("/")) rootUrl += "/";
        String root = rootUrl.substring(0, rootUrl.length() - 1);
        String rootName = Util.decodeUrl(root.substring(root.lastIndexOf('/') + 1));
        if (rootName.length() == 0 || rootName.endsWith(":")) rootName = "Shared";
        Vector out = new Vector();
        int[] nd = new int[1];
        scanDir(rootUrl, rootName, out, nd, 0);
        Hashtable h = new Hashtable();
        for (int i = 0; i < out.size(); i++) {
            Entry e = (Entry) out.elementAt(i);
            h.put(e.vpath, e);
        }
        synchronized (this) {
            entries = out;
            byPath = h;
            dirs = nd[0];
            cachedList = null;
        }
        return out.size();
    }

    private void scanDir(String url, String vdir, Vector out, int[] nd, int depth) throws IOException {
        if (depth > 8 || out.size() >= maxFiles) return;
        FileConnection fc = (FileConnection) Connector.open(url, Connector.READ);
        Vector subdirs = new Vector();
        boolean any = false;
        try {
            if (!fc.exists() || !fc.isDirectory()) throw new IOException("not a folder: " + url);
            Enumeration en = fc.list();
            while (en.hasMoreElements() && out.size() < maxFiles) {
                String name = (String) en.nextElement();
                if (name.endsWith("/")) {
                    subdirs.addElement(name.substring(0, name.length() - 1));
                    continue;
                }
                long size = -1;
                String furl = url + Util.urlEscape(name);
                try {
                    FileConnection f = (FileConnection) Connector.open(furl, Connector.READ);
                    try { size = f.fileSize(); } finally { f.close(); }
                } catch (Exception e) {}
                if (size < 0) continue;
                Entry e = new Entry();
                e.vpath = vdir + "\\" + name;
                e.url = furl;
                e.lower = e.vpath.toLowerCase();
                e.size = size;
                out.addElement(e);
                any = true;
            }
        } finally {
            fc.close();
        }
        if (any) nd[0]++;
        for (int i = 0; i < subdirs.size(); i++) {
            String s = (String) subdirs.elementAt(i);
            try {
                scanDir(url + Util.urlEscape(s) + "/", vdir + "\\" + s, out, nd, depth + 1);
            } catch (Exception e) {}
        }
    }

    /** Soulseek-style match: every word must appear, "-word" excludes. */
    Vector match(String query, int max) {
        Vector inc = new Vector(), exc = new Vector();
        String q = query.toLowerCase();
        int i = 0;
        while (i < q.length()) {
            while (i < q.length() && q.charAt(i) == ' ') i++;
            int j = i;
            while (j < q.length() && q.charAt(j) != ' ') j++;
            if (j > i) {
                String w = q.substring(i, j);
                boolean neg = w.charAt(0) == '-';
                if (neg) w = w.substring(1);
                while (w.startsWith("*")) w = w.substring(1);
                if (w.length() > 0) (neg ? exc : inc).addElement(w);
            }
            i = j;
        }
        Vector hits = new Vector();
        if (inc.size() == 0) return hits;
        Vector es;
        synchronized (this) { es = entries; }
        for (int k = 0; k < es.size() && hits.size() < max; k++) {
            Entry e = (Entry) es.elementAt(k);
            boolean ok = true;
            for (int a = 0; ok && a < inc.size(); a++) ok = e.lower.indexOf((String) inc.elementAt(a)) >= 0;
            for (int a = 0; ok && a < exc.size(); a++) ok = e.lower.indexOf((String) exc.elementAt(a)) < 0;
            if (ok) hits.addElement(e);
        }
        return hits;
    }

    private static String dirOf(String vpath) {
        int i = vpath.lastIndexOf('\\');
        return i < 0 ? "" : vpath.substring(0, i);
    }

    private static void putFile(Msg m, String name, long size) {
        int dot = name.lastIndexOf('.');
        m.u8(1).str(name).u64(size).str(dot >= 0 ? name.substring(dot + 1) : "").u32(0);
    }

    /** SharedFileListResponse payload (already zlib-wrapped). */
    synchronized byte[] listResponse() {
        if (cachedList != null) return cachedList;
        Msg body = new Msg();
        Msg m = new Msg();
        int nd = 0;
        int i = 0;
        while (i < entries.size()) {
            String d = dirOf(((Entry) entries.elementAt(i)).vpath);
            int j = i;
            while (j < entries.size() && dirOf(((Entry) entries.elementAt(j)).vpath).equals(d)) j++;
            body.str(d).u32(j - i);
            for (int k = i; k < j; k++) {
                Entry e = (Entry) entries.elementAt(k);
                putFile(body, Util.baseName(e.vpath), e.size);
            }
            nd++;
            i = j;
        }
        m.u32(nd).raw(body.toRaw()).u32(0).u32(0);
        cachedList = m.zlib().toRaw();
        return cachedList;
    }

    /** FolderContentsResponse payload (zlib-wrapped). */
    synchronized byte[] folderResponse(int token, String folder) {
        Msg files = new Msg();
        int n = 0;
        for (int k = 0; k < entries.size(); k++) {
            Entry e = (Entry) entries.elementAt(k);
            if (dirOf(e.vpath).equals(folder)) { putFile(files, Util.baseName(e.vpath), e.size); n++; }
        }
        Msg m = new Msg().u32(token).str(folder);
        if (n == 0) m.u32(0);
        else m.u32(1).str(folder).u32(n).raw(files.toRaw());
        return m.zlib().toRaw();
    }

    /** FileSearchResponse payload (zlib-wrapped). */
    static byte[] searchResponse(String me, int token, Vector hits, boolean slot, int speed, int queue) {
        Msg m = new Msg().str(me).u32(token).u32(hits.size());
        for (int i = 0; i < hits.size(); i++) {
            Entry e = (Entry) hits.elementAt(i);
            int dot = e.vpath.lastIndexOf('.');
            m.u8(1).str(e.vpath).u64(e.size).str(dot >= 0 ? e.vpath.substring(dot + 1) : "").u32(0);
        }
        m.bool(slot).u32(speed).u32(queue).u32(0).u32(0);
        return m.zlib().toRaw();
    }
}
