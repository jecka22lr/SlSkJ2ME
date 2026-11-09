package slsk;

import java.util.Vector;

/** A joined chat room. */
public final class Room {
    public final String name;
    public final Vector users = new Vector();
    public final Vector lines = new Vector(); // String
    public int unread;

    Room(String name) { this.name = name; }

    synchronized void add(String line) {
        lines.addElement(line);
        while (lines.size() > 60) lines.removeElementAt(0);
        unread++;
    }
}
