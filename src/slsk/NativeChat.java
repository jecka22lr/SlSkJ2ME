package slsk;

import java.util.Vector;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.StringItem;

/**
 * Chat in the phone's own Form, so it follows the theme exactly. The nickname is the item label
 * (S60 draws labels bold in the theme's label colour); colours can't be set per word here.
 * Jumps to the newest line with Display.setCurrentItem, which S60 honours.
 */
final class NativeChat extends Form implements ChatScreen {
    private final Display d;

    NativeChat(String title, Display d) {
        super(title);
        this.d = d;
    }

    private Item item(String line) {
        String n = ChatView.nameOf(line);
        StringItem it = n.length() > 0
            ? new StringItem(n + ":", line.substring(n.length() + 2))
            : new StringItem(null, line);
        it.setLayout(Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_LEFT);
        return it;
    }

    public void setLines(Vector lines) {
        deleteAll();
        for (int i = Math.max(0, lines.size() - 40); i < lines.size(); i++) append(item(String.valueOf(lines.elementAt(i))));
    }

    public void add(String line) {
        append(item(line));
        while (size() > 40) delete(0);
        if (d.getCurrent() == this) toBottom();
    }

    public void toBottom() {
        if (size() == 0) return;
        final Item last = get(size() - 1);
        d.callSerially(new Runnable() {
            public void run() {
                try { if (d.getCurrent() == NativeChat.this) d.setCurrentItem(last); } catch (Throwable e) {}
            }
        });
    }

    public void refresh() {}

    public void show(Display d) {
        d.setCurrent(this);
        toBottom();
    }
}
