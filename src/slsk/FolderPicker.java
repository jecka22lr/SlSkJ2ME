package slsk;

import java.util.Enumeration;
import java.util.Vector;
import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import javax.microedition.io.file.FileSystemRegistry;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;

/** Browse the phone's drives and folders and pick one. */
final class FolderPicker implements CommandListener {
    interface Done {
        /** url of the chosen folder (ends with "/"), or null if cancelled */
        void picked(String url);
    }

    private final Display d;
    private final Done done;
    private final List l;
    private final Vector urls = new Vector(); // row -> folder url (null for the "up" row)
    private String cur; // null = list of drives
    private TextBox newTB;

    private final Command cUse = new Command("Use this folder", Command.OK, 1);
    private final Command cNew = new Command("New folder", Command.SCREEN, 2);
    private final Command cCancel = new Command("Cancel", Command.CANCEL, 3);
    private final Command cMake = new Command("Create", Command.OK, 1);
    private final Command cBack = new Command("Back", Command.BACK, 2);

    FolderPicker(Display d, String title, Done done) {
        this.d = d;
        this.done = done;
        l = new List(title, List.IMPLICIT);
        l.addCommand(cCancel);
        l.setCommandListener(this);
    }

    /** Opens at start (if it exists), else at the drive list. */
    void show(String start) {
        if (start != null && start.toLowerCase().indexOf("/private/") >= 0) start = null; // don't open inside app storage
        d.setCurrent(l);
        load(start);
    }

    private void load(final String url) {
        l.deleteAll();
        l.append("Loading...", null);
        urls.removeAllElements();
        // file access can trigger a permission prompt; never block the UI thread with it
        new Thread(new Runnable() {
            public void run() {
                final Vector names = new Vector(), us = new Vector();
                String at = null;
                String err = null;
                try {
                    if (url != null) {
                        FileConnection fc = (FileConnection) Connector.open(url, Connector.READ);
                        try {
                            if (fc.exists() && fc.isDirectory()) {
                                at = url;
                                Enumeration e = fc.list();
                                while (e.hasMoreElements()) {
                                    String n = (String) e.nextElement();
                                    if (n.endsWith("/")) insertSorted(names, us, n.substring(0, n.length() - 1),
                                            url + Util.urlEscape(n.substring(0, n.length() - 1)) + "/");
                                }
                            }
                        } finally {
                            fc.close();
                        }
                    }
                    if (at == null) {
                        // real drives (C:/, E:/) first; named roots some phones add are shortcuts
                        Vector aliasN = new Vector(), aliasU = new Vector();
                        Enumeration e = FileSystemRegistry.listRoots();
                        while (e.hasMoreElements()) {
                            String r = (String) e.nextElement();
                            String ru = "file:///" + (r.endsWith("/") ? r : r + "/");
                            if (r.length() >= 2 && r.charAt(1) == ':') {
                                names.addElement(Store.friendly(ru) + "  (" + r + ")");
                                us.addElement(ru);
                            } else {
                                aliasN.addElement(Util.decodeUrl(r) + "  (shortcut)");
                                aliasU.addElement(ru);
                            }
                        }
                        for (int i = 0; i < aliasN.size(); i++) {
                            names.addElement(aliasN.elementAt(i));
                            us.addElement(aliasU.elementAt(i));
                        }
                    }
                } catch (SecurityException e) {
                    err = "The phone won't let the app look in there";
                } catch (Throwable e) {
                    err = "Can't open folder: " + e.getMessage();
                }
                final String fat = at, ferr = err;
                d.callSerially(new Runnable() {
                    public void run() { fill(fat, names, us, ferr); }
                });
            }
        }).start();
    }

    private static void insertSorted(Vector names, Vector us, String n, String u) {
        String k = n.toLowerCase();
        int i = 0;
        while (i < names.size() && ((String) names.elementAt(i)).toLowerCase().compareTo(k) < 0) i++;
        names.insertElementAt(n, i);
        us.insertElementAt(u, i);
    }

    private void fill(String at, Vector names, Vector us, String err) {
        cur = at;
        l.deleteAll();
        urls.removeAllElements();
        l.removeCommand(cUse);
        l.removeCommand(cNew);
        if (at != null) {
            l.append(".. (up)", null);
            urls.addElement(null);
            l.addCommand(cUse);
            l.addCommand(cNew);
            l.setTitle(Store.friendly(at));
        } else {
            l.setTitle("Pick a drive");
        }
        for (int i = 0; i < names.size(); i++) {
            String n = (String) names.elementAt(i);
            if (at != null && n.equalsIgnoreCase("private")) n = "Private (app storage, hidden)";
            l.append((at != null ? "[" + n + "]" : n), null);
            urls.addElement(us.elementAt(i));
        }
        if (err != null) l.append("(" + err + ")", null);
        else if (at != null && names.size() == 0) l.append("(no subfolders)", null);
    }

    private String parentOf(String url) {
        String u = url.substring(0, url.length() - 1);
        int i = u.lastIndexOf('/');
        String p = u.substring(0, i + 1);
        return p.length() <= "file:///".length() ? null : p; // above a drive root: drive list
    }

    public void commandAction(Command c, Displayable s) {
        if (s == newTB) {
            if (c == cMake && cur != null) {
                final String name = Util.safeName(newTB.getString().trim());
                final String u = cur + Util.urlEscape(name) + "/";
                new Thread(new Runnable() {
                    public void run() {
                        try {
                            FileConnection fc = (FileConnection) Connector.open(u, Connector.READ_WRITE);
                            try { if (!fc.exists()) fc.mkdir(); } finally { fc.close(); }
                        } catch (Throwable e) {}
                        d.callSerially(new Runnable() { public void run() { d.setCurrent(l); load(u); } });
                    }
                }).start();
            } else {
                d.setCurrent(l);
            }
            return;
        }
        if (c == cCancel) {
            done.picked(null);
        } else if (c == cUse && cur != null) {
            done.picked(cur);
        } else if (c == cNew && cur != null) {
            newTB = new TextBox("New folder name", "Soulseek", 60, TextField.ANY);
            newTB.addCommand(cMake);
            newTB.addCommand(cBack);
            newTB.setCommandListener(this);
            d.setCurrent(newTB);
        } else if (c == List.SELECT_COMMAND) {
            int i = l.getSelectedIndex();
            if (i < 0 || i >= urls.size()) return;
            String u = (String) urls.elementAt(i);
            if (u == null) load(cur == null ? null : parentOf(cur)); // ".. (up)"
            else load(u);
        }
    }
}
