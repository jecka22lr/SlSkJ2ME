package slsk;

import java.util.Vector;

/**
 * Search results grouped like desktop clients show them:
 *   user (slot / speed / queue)
 *     folder
 *       files
 * Users with a free slot come first, then by upload speed. Flattened into rows for a List.
 */
final class ResultGroups {
    static final class User {
        final String name;
        boolean slot;
        int speed, queue;
        final Vector dirs = new Vector();
        User(String n) { name = n; }
    }

    static final class Dir {
        final User user;
        final String path; // full remote folder path
        final Vector files = new Vector();
        Dir(User u, String p) { user = u; path = p; }
    }

    private final Vector users = new Vector();
    private int total;

    void clear() { users.removeAllElements(); total = 0; }

    int total() { return total; }

    static String dirOf(String file) {
        int i = file.lastIndexOf('\\');
        return i < 0 ? "" : file.substring(0, i);
    }

    /** Last two folder names, e.g. "Artist/Album". */
    static String shortDir(String path) {
        if (path.length() == 0) return "(top folder)";
        int a = path.lastIndexOf('\\');
        if (a < 0) return path;
        int b = path.lastIndexOf('\\', a - 1);
        return path.substring(b + 1).replace('\\', '/');
    }

    void add(Result r) {
        total++;
        User u = null;
        for (int i = 0; i < users.size(); i++) {
            User x = (User) users.elementAt(i);
            if (x.name.equals(r.user)) { u = x; break; }
        }
        if (u == null) {
            u = new User(r.user);
            u.slot = r.slot; u.speed = r.speed; u.queue = r.queue;
            // keep order: free slot first, then faster first
            int i = 0;
            while (i < users.size() && before((User) users.elementAt(i), u)) i++;
            users.insertElementAt(u, i);
        }
        String dp = dirOf(r.file);
        Dir d = null;
        for (int i = 0; i < u.dirs.size(); i++) {
            Dir x = (Dir) u.dirs.elementAt(i);
            if (x.path.equals(dp)) { d = x; break; }
        }
        if (d == null) { d = new Dir(u, dp); u.dirs.addElement(d); }
        d.files.addElement(r);
    }

    private static boolean before(User a, User b) {
        if (a.slot != b.slot) return a.slot;
        return a.speed >= b.speed;
    }

    interface Filter { boolean passes(Result r); }

    /** Flatten to rows (User, Dir, Result objects). Groups with no passing files are left out. */
    Vector rows(Filter f) {
        Vector out = new Vector();
        for (int i = 0; i < users.size(); i++) {
            User u = (User) users.elementAt(i);
            boolean userShown = false;
            for (int j = 0; j < u.dirs.size(); j++) {
                Dir d = (Dir) u.dirs.elementAt(j);
                boolean dirShown = false;
                for (int k = 0; k < d.files.size(); k++) {
                    Result r = (Result) d.files.elementAt(k);
                    if (f != null && !f.passes(r)) continue;
                    if (!userShown) { out.addElement(u); userShown = true; }
                    if (!dirShown) { out.addElement(d); dirShown = true; }
                    out.addElement(r);
                }
            }
        }
        return out;
    }

    /** Files of a folder that pass the filter. */
    static Vector files(Dir d, Filter f) {
        Vector v = new Vector();
        for (int k = 0; k < d.files.size(); k++) {
            Result r = (Result) d.files.elementAt(k);
            if (f == null || f.passes(r)) v.addElement(r);
        }
        return v;
    }

    static String userLabel(User u) {
        StringBuffer sb = new StringBuffer(u.name);
        sb.append(u.slot ? "  free slot" : "  queue " + u.queue);
        if (u.speed > 0) sb.append("  ").append(Util.size(u.speed)).append("/s");
        return sb.toString();
    }

    static String dirLabel(Dir d, int shownFiles) {
        return shortDir(d.path) + "  (" + shownFiles + ")";
    }

    static String fileLabel(Result r) {
        StringBuffer sb = new StringBuffer(Util.baseName(r.file));
        sb.append("  ").append(Util.size(r.size));
        if (r.bitrate > 0) sb.append("  ").append(r.bitrate).append("k");
        if (r.duration > 0) sb.append("  ").append(Util.duration(r.duration));
        return sb.toString();
    }
}
