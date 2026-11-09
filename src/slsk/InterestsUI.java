package slsk;

import java.util.Vector;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;

/** Likes/dislikes, recommendations, similar users and other people's interests. */
final class InterestsUI implements CommandListener {
    private final SlskMIDlet m;
    private final SocialUI social;
    private final Display d;

    private final Command cBack = new Command("Back", Command.BACK, 2);
    private final Command cAdd = new Command("Add", Command.SCREEN, 2);
    private final Command cRemove = new Command("Remove", Command.ITEM, 3);
    private final Command cOk = new Command("OK", Command.OK, 1);
    private final Command cRefresh = new Command("Refresh", Command.SCREEN, 3);

    private static final String[] MENU = { "My likes", "My dislikes", "Recommended for me", "Popular everywhere", "Similar users" };
    private List menuL, itemsL, recL, itemL, usersL;
    private boolean editingHates;
    private String recKind;            // "rec", "global" or "item:<thing>" shown in recL
    private String usersKind;          // "similar" or "item:<thing>" shown in usersL
    private final Vector recRows = new Vector(), userRows = new Vector();
    private String curItem;
    private Displayable itemBack, usersBack, recBack;
    private TextBox addTB;
    private Form theirF;
    private Displayable theirBack;
    private String theirUser;

    InterestsUI(SlskMIDlet m, SocialUI social, Display d) { this.m = m; this.social = social; this.d = d; }

    private Client c() { return m.client(); }

    void show() {
        Client cl = c();
        menuL = new List("Interests", List.IMPLICIT);
        for (int i = 0; i < MENU.length; i++) menuL.append(MENU[i], null);
        menuL.set(0, "My likes (" + cl.likes.size() + ")", null);
        menuL.set(1, "My dislikes (" + cl.hates.size() + ")", null);
        menuL.addCommand(cBack);
        menuL.setCommandListener(this);
        d.setCurrent(menuL);
    }

    private void showItems(boolean hates) {
        editingHates = hates;
        Vector v = hates ? c().hates : c().likes;
        itemsL = new List(hates ? "My dislikes" : "My likes", List.IMPLICIT);
        for (int i = 0; i < v.size(); i++) itemsL.append((String) v.elementAt(i), null);
        if (v.size() == 0) itemsL.append(hates ? "(none yet: Add)" : "(none yet: Add bands, genres, anything)", null);
        itemsL.addCommand(cAdd);
        itemsL.addCommand(cRemove);
        itemsL.addCommand(cBack);
        itemsL.setCommandListener(this);
        d.setCurrent(itemsL);
    }

    private void showRecs(String kind, Displayable back) {
        recKind = kind;
        recBack = back;
        recRows.removeAllElements();
        recL = new List(kind.equals("rec") ? "Recommended for me" : kind.equals("global") ? "Popular everywhere"
                : "Like " + kind.substring(5), List.IMPLICIT);
        recL.append("Loading...", null);
        recL.addCommand(cRefresh);
        recL.addCommand(cBack);
        recL.setCommandListener(this);
        d.setCurrent(recL);
        requestRecs();
    }

    private void requestRecs() {
        if (recKind.equals("rec")) c().getRecommendations(false);
        else if (recKind.equals("global")) c().getRecommendations(true);
        else c().getItemRecommendations(recKind.substring(5));
    }

    private void showUsers(String kind, Displayable back) {
        usersKind = kind;
        usersBack = back;
        userRows.removeAllElements();
        usersL = new List(kind.equals("similar") ? "Similar users" : "Who likes " + kind.substring(5), List.IMPLICIT);
        usersL.append("Loading...", null);
        usersL.addCommand(cBack);
        usersL.setCommandListener(this);
        d.setCurrent(usersL);
        if (kind.equals("similar")) c().getSimilarUsers();
        else c().getItemSimilarUsers(kind.substring(5));
    }

    private void showItem(String item, Displayable back) {
        curItem = item;
        itemBack = back;
        Client cl = c();
        itemL = new List(item, List.IMPLICIT);
        itemL.append("Users who like this", null);
        itemL.append("Similar things", null);
        itemL.append(cl.likes.contains(item) ? "Remove from my likes" : "Add to my likes", null);
        itemL.append(cl.hates.contains(item) ? "Remove from my dislikes" : "Add to my dislikes", null);
        itemL.append("Search for it", null);
        itemL.addCommand(cBack);
        itemL.setCommandListener(this);
        d.setCurrent(itemL);
    }

    /** From someone's user menu: what they like and dislike. */
    void showTheirs(String user, Displayable back) {
        theirUser = user;
        theirBack = back;
        theirF = new Form(user + "'s interests");
        theirF.append("Loading...");
        theirF.addCommand(cBack);
        theirF.setCommandListener(this);
        d.setCurrent(theirF);
        c().getUserInterests(user);
    }

    void load(Client cl) {
        Vector v = new Vector();
        SlskMIDlet.loadStrings("slskme-int", v);
        for (int i = 0; i < v.size(); i++) {
            String e = (String) v.elementAt(i);
            if (e.length() < 2) continue;
            (e.charAt(0) == 'h' ? cl.hates : cl.likes).addElement(e.substring(1));
        }
    }

    private void save() {
        Client cl = c();
        Vector v = new Vector();
        for (int i = 0; i < cl.likes.size(); i++) v.addElement("l" + cl.likes.elementAt(i));
        for (int i = 0; i < cl.hates.size(); i++) v.addElement("h" + cl.hates.elementAt(i));
        SlskMIDlet.saveStrings("slskme-int", v);
    }

    /** Network events (already on the UI thread). */
    void onEvent(int type, Object a, Object b) {
        Displayable cur = d.getCurrent();
        if (type == Client.EV_RECS && recL != null && cur == recL && a.equals(recKind)) {
            Vector v = (Vector) b;
            recL.deleteAll();
            recRows.removeAllElements();
            for (int i = 0; i < v.size(); i++) {
                Object[] o = (Object[]) v.elementAt(i);
                recRows.addElement(o[0]);
                int sc = ((Integer) o[1]).intValue();
                recL.append(o[0] + (sc != 0 ? "  (" + sc + ")" : ""), null);
            }
            if (v.size() == 0) recL.append(recKind.equals("rec") ? "(nothing yet: add some likes first)" : "(nothing)", null);
        } else if (type == Client.EV_SIMILAR && usersL != null && cur == usersL && a.equals(usersKind)) {
            Vector v = (Vector) b;
            usersL.deleteAll();
            userRows.removeAllElements();
            for (int i = 0; i < v.size(); i++) { userRows.addElement(v.elementAt(i)); usersL.append((String) v.elementAt(i), null); }
            if (v.size() == 0) usersL.append(usersKind.equals("similar") ? "(nobody yet: add some likes first)" : "(nobody)", null);
        } else if (type == Client.EV_INTERESTS && theirF != null && cur == theirF && a.equals(theirUser)) {
            Vector[] lh = (Vector[]) b;
            theirF.deleteAll();
            theirF.append(new StringItem("Likes", lh[0].size() == 0 ? "(none)\n" : join(lh[0]) + "\n"));
            theirF.append(new StringItem("Dislikes", lh[1].size() == 0 ? "(none)\n" : join(lh[1]) + "\n"));
        }
    }

    private static String join(Vector v) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < v.size(); i++) sb.append(i > 0 ? ", " : "").append(v.elementAt(i));
        return sb.toString();
    }

    public void commandAction(Command c, Displayable s) {
        Client cl = c();
        if (cl == null) { m.toLogin(); return; }
        if (s == menuL) {
            if (c == cBack) { m.showMain(); return; }
            switch (menuL.getSelectedIndex()) {
            case 0: showItems(false); break;
            case 1: showItems(true); break;
            case 2: showRecs("rec", menuL); break;
            case 3: showRecs("global", menuL); break;
            case 4: showUsers("similar", menuL); break;
            }
        } else if (s == itemsL) {
            Vector v = editingHates ? cl.hates : cl.likes;
            int i = itemsL.getSelectedIndex();
            String it = i >= 0 && i < v.size() ? (String) v.elementAt(i) : null;
            if (c == cBack) show();
            else if (c == cAdd) {
                addTB = new TextBox(editingHates ? "Add a dislike" : "Add a like (band, genre...)", "", 100, TextField.ANY);
                addTB.addCommand(cOk);
                addTB.addCommand(cBack);
                addTB.setCommandListener(this);
                d.setCurrent(addTB);
            } else if (c == cRemove && it != null) {
                if (editingHates) cl.hate(it, false); else cl.like(it, false);
                save();
                showItems(editingHates);
            } else if (c == List.SELECT_COMMAND && it != null) showItem(it, itemsL);
        } else if (s == addTB) {
            String t = addTB.getString().trim();
            if (c == cOk && t.length() > 0) {
                if (editingHates) cl.hate(t, true); else cl.like(t, true);
                save();
            }
            showItems(editingHates);
        } else if (s == recL) {
            int i = recL.getSelectedIndex();
            if (c == cBack) d.setCurrent(recBack != null ? recBack : menuL);
            else if (c == cRefresh) showRecs(recKind, recBack);
            else if (c == List.SELECT_COMMAND && i >= 0 && i < recRows.size()) showItem((String) recRows.elementAt(i), recL);
        } else if (s == usersL) {
            int i = usersL.getSelectedIndex();
            if (c == cBack) d.setCurrent(usersBack != null ? usersBack : menuL);
            else if (c == List.SELECT_COMMAND && i >= 0 && i < userRows.size()) social.showUser((String) userRows.elementAt(i), usersL);
        } else if (s == itemL) {
            if (c == cBack) { d.setCurrent(itemBack != null ? itemBack : menuL); return; }
            switch (itemL.getSelectedIndex()) {
            case 0: showUsers("item:" + curItem, itemL); break;
            case 1: showRecs("item:" + curItem, itemL); break;
            case 2: cl.like(curItem, !cl.likes.contains(curItem)); save(); showItem(curItem, itemBack); itemL.setSelectedIndex(2, true); break;
            case 3: cl.hate(curItem, !cl.hates.contains(curItem)); save(); showItem(curItem, itemBack); itemL.setSelectedIndex(3, true); break;
            case 4: m.searchFor(curItem); break;
            }
        } else if (s == theirF) {
            d.setCurrent(theirBack != null ? theirBack : m.main());
        }
    }
}
