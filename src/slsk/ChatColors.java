package slsk;

import java.util.Vector;

/** Nickname colours in chat: your own name, people online, people offline. Saved in RMS. */
final class ChatColors {
    static final String[] NAMES = { "Blue", "Green", "Red", "Orange", "Purple", "Teal", "Pink", "Brown", "Gray", "Black" };
    static final int[] VALUES = { 0x1d6fa5, 0x2e8b3a, 0xc0392b, 0xd9822b, 0x7d3c98, 0x138d90, 0xc2185b, 0x8d6e63, 0x8a8a8a, 0x000000 };

    /** Palette indexes: own, online, offline. */
    static int own = 0, online = 1, offline = 8;

    static void load() {
        Vector v = new Vector();
        SlskMIDlet.loadStrings("slskme-col", v);
        if (v.size() < 3) return;
        own = idx((String) v.elementAt(0), own);
        online = idx((String) v.elementAt(1), online);
        offline = idx((String) v.elementAt(2), offline);
    }

    static void save() {
        Vector v = new Vector();
        v.addElement("" + own);
        v.addElement("" + online);
        v.addElement("" + offline);
        SlskMIDlet.saveStrings("slskme-col", v);
    }

    private static int idx(String s, int def) {
        try {
            int i = Integer.parseInt(s);
            return i >= 0 && i < VALUES.length ? i : def;
        } catch (Exception e) { return def; }
    }
}
