package slsk;

import java.util.Vector;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

/**
 * Chat / private message view. Only the nickname is coloured (own / online / offline, see ChatColors). Draws its own wrapped text so it can always open at the newest
 * line (LCDUI Forms open at the top, and phones differ on whether they let you scroll them).
 * Up/Down or dragging scrolls; new lines keep it pinned to the bottom unless you've scrolled up.
 */
final class ChatView extends Canvas {
    /** Colour for a nickname, decided by whoever shows this view. */
    interface Namer { int color(String name); }

    private final Namer namer;
    private final Vector lines = new Vector();
    private final Vector wrapped = new Vector();   // String
    private final Vector names = new Vector();     // String nickname at the start of this wrapped line, or ""
    private final Vector nameLens = new Vector();  // Integer chars of that line that are the nickname (incl. ": ")
    private int wrapW = -1;
    private int top;
    private boolean stick = true;
    private int dragY = -1;
    private final Font font = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);

    ChatView(String title, Namer namer) {
        setTitle(title);
        this.namer = namer;
    }

    /** Statuses changed: redraw the names. */
    void refresh() { repaint(); }

    void setLines(Vector src) {
        lines.removeAllElements();
        for (int i = Math.max(0, src.size() - 60); i < src.size(); i++) lines.addElement(String.valueOf(src.elementAt(i)));
        wrapW = -1;
        stick = true;
        repaint();
    }

    void add(String line) {
        lines.addElement(line);
        while (lines.size() > 60) lines.removeElementAt(0);
        wrapW = -1;
        repaint();
    }

    /** Jump to the newest line (after you send something). */
    void toBottom() { stick = true; repaint(); }

    int lineCount() { return lines.size(); }

    private void rewrap(int w) {
        wrapped.removeAllElements();
        names.removeAllElements();
        nameLens.removeAllElements();
        for (int i = 0; i < lines.size(); i++) wrapLine((String) lines.elementAt(i), w);
        wrapW = w;
    }

    /** "user: text" -> "user", or "" if the line has no nickname. */
    static String nameOf(String l) {
        int c = l.indexOf(": ");
        return c > 0 && c <= 40 ? l.substring(0, c) : "";
    }

    private void wrapLine(String s, int w) {
        String name = nameOf(s);
        int left = name.length() > 0 ? name.length() + 1 : 0; // nickname + ':' still to colour
        int start = 0;
        while (start < s.length()) {
            int end = start, lastSpace = -1;
            while (end < s.length() && font.substringWidth(s, start, end - start + 1) <= w) {
                if (s.charAt(end) == ' ') lastSpace = end;
                end++;
            }
            if (end < s.length() && lastSpace > start) end = lastSpace + 1;
            if (end == start) end = start + 1; // one char wider than the screen: show it anyway
            String seg = s.substring(start, end);
            int lead = 0;
            while (lead < seg.length() && seg.charAt(lead) == ' ' && start > 0) lead++;
            wrapped.addElement(seg.substring(lead));
            int n = Math.max(0, Math.min(left - lead, seg.length() - lead));
            names.addElement(n > 0 ? name : "");
            nameLens.addElement(new Integer(n));
            left = Math.max(0, left - (end - start));
            start = end;
        }
        if (s.length() == 0) { wrapped.addElement(""); names.addElement(""); nameLens.addElement(new Integer(0)); }
    }

    private int lineH() { return font.getHeight() + 2; }

    private int visible() { return Math.max(1, (getHeight() - 4) / lineH()); }

    private int maxTop() { return Math.max(0, wrapped.size() - visible()); }

    protected void paint(Graphics g) {
        int w = getWidth(), h = getHeight();
        if (wrapW != w - 8) rewrap(w - 8);
        if (stick || top > maxTop()) top = maxTop();
        g.setColor(0xffffff);
        g.fillRect(0, 0, w, h);
        g.setFont(font);
        int y = 2, lh = lineH();
        if (wrapped.size() == 0) {
            g.setColor(0x888888);
            g.drawString("No messages yet. Write to start.", 4, y, Graphics.TOP | Graphics.LEFT);
            return;
        }
        for (int i = top; i < wrapped.size() && y < h; i++) {
            String t = (String) wrapped.elementAt(i);
            int n = ((Integer) nameLens.elementAt(i)).intValue();
            int x = 4;
            if (n > 0) {
                String nm = (String) names.elementAt(i);
                g.setColor(namer == null ? 0x000000 : namer.color(nm));
                Font bold = Font.getFont(font.getFace(), Font.STYLE_BOLD, font.getSize());
                g.setFont(bold);
                String head = t.substring(0, n);
                g.drawString(head, x, y, Graphics.TOP | Graphics.LEFT);
                x += bold.stringWidth(head);
                g.setFont(font);
            }
            g.setColor(0x000000);
            g.drawString(t.substring(n), x, y, Graphics.TOP | Graphics.LEFT);
            y += lh;
        }
        if (top > 0) { g.setColor(0x999999); g.fillTriangle(w - 9, 8, w - 3, 8, w - 6, 3); }
        if (top < maxTop()) { g.setColor(0x999999); g.fillTriangle(w - 9, h - 8, w - 3, h - 8, w - 6, h - 3); }
    }

    private void scroll(int by) {
        top = Math.max(0, Math.min(maxTop(), top + by));
        stick = top >= maxTop();
        repaint();
    }

    protected void keyPressed(int k) {
        int a = 0;
        try { a = getGameAction(k); } catch (Throwable e) {}
        if (a == UP || k == KEY_NUM2) scroll(-1);
        else if (a == DOWN || k == KEY_NUM8) scroll(1);
        else if (k == KEY_NUM1) scroll(-visible());
        else if (k == KEY_NUM7) scroll(visible());
    }

    protected void keyRepeated(int k) { keyPressed(k); }

    protected void pointerPressed(int x, int y) { dragY = y; }

    protected void pointerDragged(int x, int y) {
        if (dragY < 0) return;
        int steps = (dragY - y) / lineH();
        if (steps != 0) { scroll(steps); dragY = y; }
    }

    protected void pointerReleased(int x, int y) { dragY = -1; }
}
