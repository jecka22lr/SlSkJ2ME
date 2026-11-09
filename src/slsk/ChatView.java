package slsk;

import java.util.Vector;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

/**
 * Chat / private message view. Draws its own wrapped text so it can always open at the newest
 * line (LCDUI Forms open at the top, and phones differ on whether they let you scroll them).
 * Up/Down or dragging scrolls; new lines keep it pinned to the bottom unless you've scrolled up.
 */
final class ChatView extends Canvas {
    private final String me;
    private final Vector lines = new Vector();
    private final Vector wrapped = new Vector();   // String
    private final Vector mine = new Vector();      // Boolean per wrapped line
    private int wrapW = -1;
    private int top;
    private boolean stick = true;
    private int dragY = -1;
    private final Font font = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);

    ChatView(String title, String me) {
        setTitle(title);
        this.me = me + ": ";
    }

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
        mine.removeAllElements();
        for (int i = 0; i < lines.size(); i++) {
            String l = (String) lines.elementAt(i);
            Boolean m = l.startsWith(me) ? Boolean.TRUE : Boolean.FALSE;
            wrapLine(l, w, m);
        }
        wrapW = w;
    }

    private void wrapLine(String s, int w, Boolean m) {
        int start = 0;
        while (start < s.length()) {
            int end = start, lastSpace = -1;
            while (end < s.length() && font.substringWidth(s, start, end - start + 1) <= w) {
                if (s.charAt(end) == ' ') lastSpace = end;
                end++;
            }
            if (end < s.length() && lastSpace > start) end = lastSpace + 1;
            if (end == start) end = start + 1; // one char wider than the screen: show it anyway
            wrapped.addElement(s.substring(start, end).trim());
            mine.addElement(m);
            start = end;
        }
        if (s.length() == 0) { wrapped.addElement(""); mine.addElement(m); }
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
            g.setColor(((Boolean) mine.elementAt(i)).booleanValue() ? 0x1d6fa5 : 0x000000);
            g.drawString((String) wrapped.elementAt(i), 4, y, Graphics.TOP | Graphics.LEFT);
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
