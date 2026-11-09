package slsk;

import java.util.Vector;

/** Private message conversation with one user. */
public final class Convo {
    public final String user;
    public final Vector lines = new Vector();
    public int unread;

    Convo(String user) { this.user = user; }

    synchronized void add(String line, boolean incoming) {
        lines.addElement(line);
        while (lines.size() > 60) lines.removeElementAt(0);
        if (incoming) unread++;
    }
}
