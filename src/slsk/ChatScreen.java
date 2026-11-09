package slsk;

import java.util.Vector;
import javax.microedition.lcdui.Display;

/** A chat room or private message screen: our own drawn view (ChatView) or the phone's form (NativeChat). */
interface ChatScreen {
    void setLines(Vector lines);
    void add(String line);
    void toBottom();
    void refresh();
    /** Make it the current screen, scrolled to the newest line. */
    void show(Display d);
}
