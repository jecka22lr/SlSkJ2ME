package slsk;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.ChoiceGroup;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;
import javax.microedition.lcdui.Ticker;
import javax.microedition.midlet.MIDlet;
import javax.microedition.rms.RecordStore;

public final class SlskMIDlet extends MIDlet implements CommandListener, Client.Listener {
    private Display display;
    private Client client;
    private boolean started;

    private Form loginF;
    private TextField fUser, fPass, fHost, fPort, fListen, fDir;
    private ChoiceGroup fRemember;
    private List mainL, resL, dlL;
    private TextBox searchTB;
    private Form logF, infoF;
    private Displayable infoBack;
    private final Vector shown = new Vector();   // Results in resL order
    private final Vector shownDl = new Vector(); // Transfers in dlL order
    private Timer timer;
    private String lastQuery = "";
    private Displayable dlBack;

    private final Command cExit = new Command("Exit", Command.EXIT, 9);
    private final Command cLogin = new Command("Log in", Command.OK, 1);
    private final Command cBack = new Command("Back", Command.BACK, 2);
    private final Command cGo = new Command("Search", Command.OK, 1);
    private final Command cGet = new Command("Download", Command.ITEM, 1);
    private final Command cInfo = new Command("Details", Command.ITEM, 2);
    private final Command cNew = new Command("New search", Command.SCREEN, 3);
    private final Command cRetry = new Command("Retry", Command.ITEM, 2);
    private final Command cPlace = new Command("Queue place", Command.ITEM, 3);
    private final Command cRemove = new Command("Remove", Command.ITEM, 4);
    private final Command cClear = new Command("Clear finished", Command.SCREEN, 5);
    private final Command cCancel = new Command("Cancel", Command.CANCEL, 1);
    private final Command cDl = new Command("Downloads", Command.SCREEN, 4);
    private final Command cAbout = new Command("About", Command.HELP, 8);
    private Form aboutF;
    private Displayable aboutBack;
    private Form connF;

    private static final String[] MENU = { "Search", "Results", "Downloads", "Uploads", "Chat rooms", "Messages",
        "Find user", "Wishlist", "Profile & sharing", "Log", "Download folder", "About", "Log out" };
    private SocialUI social;
    private String[] settings;
    private final Command cUser = new Command("User", Command.ITEM, 3);
    private final Command cFilter = new Command("Filter", Command.SCREEN, 2);
    private static final String[] FILTERS = { "All files", "MP3 only", "FLAC only", "Lossy (mp3/ogg/m4a/opus...)",
        "Lossless (flac/wav/ape/alac)", "Free slots only" };
    private int filter;
    private List filterL;
    private List folderL;
    private final Vector folderDirs = new Vector();

    protected void startApp() {
        if (started) return;
        started = true;
        display = Display.getDisplay(this);
        social = new SocialUI(this, display);
        buildLogin();
        logF = new Form("Log");
        logF.addCommand(cBack);
        logF.setCommandListener(this);
        display.setCurrent(loginF);
    }

    protected void pauseApp() {}

    protected void destroyApp(boolean u) {
        if (client != null) client.logout();
        if (timer != null) timer.cancel();
    }

    // ------------------------------------------------------------ screens

    private void buildLogin() {
        String[] s = loadSettings();
        settings = s;
        social.description = s[6];
        social.shareDir = s[7];
        social.sharing = "1".equals(s[8]);
        social.slots = s[9];
        loginF = new Form("SlskME");
        fUser = new TextField("Username", s[0], 30, TextField.ANY | TextField.NON_PREDICTIVE);
        fPass = new TextField("Password", s[1], 30, TextField.PASSWORD);
        fHost = new TextField("Server", s[2], 64, TextField.URL);
        fPort = new TextField("Port", s[3], 5, TextField.NUMERIC);
        fListen = new TextField("Listen port (0 = off)", s[4], 5, TextField.NUMERIC);
        fDir = new TextField("Download folder", s[5], 128, TextField.URL);
        fRemember = new ChoiceGroup(null, ChoiceGroup.MULTIPLE, new String[] { "Remember password" }, null);
        fRemember.setSelectedIndex(0, s[1].length() > 0);
        loginF.append(fUser);
        loginF.append(fPass);
        loginF.append(fRemember);
        loginF.append(fHost);
        loginF.append(fPort);
        loginF.append(fListen);
        loginF.append(fDir);
        loginF.addCommand(cLogin);
        loginF.addCommand(cAbout);
        loginF.addCommand(cExit);
        loginF.setCommandListener(this);
    }

    private void buildMain() {
        mainL = new List("Soulseek", List.IMPLICIT);
        for (int i = 0; i < MENU.length; i++) mainL.append(MENU[i], null);
        mainL.addCommand(cExit);
        mainL.setCommandListener(this);

        searchTB = new TextBox("Search", lastQuery, 100, TextField.ANY);
        searchTB.addCommand(cGo);
        searchTB.addCommand(cBack);
        searchTB.addCommand(cDl);
        searchTB.setCommandListener(this);

        resL = new List("Results", List.IMPLICIT);
        resL.setSelectCommand(cGet);
        resL.addCommand(cInfo);
        resL.addCommand(cNew);
        resL.addCommand(cDl);
        resL.addCommand(cUser);
        resL.addCommand(cFilter);
        resL.addCommand(cBack);
        resL.setCommandListener(this);

        dlL = new List("Downloads", List.IMPLICIT);
        dlL.setSelectCommand(cInfo);
        dlL.addCommand(cRetry);
        dlL.addCommand(cPlace);
        dlL.addCommand(cRemove);
        dlL.addCommand(cClear);
        dlL.addCommand(cBack);
        dlL.setCommandListener(this);

        infoF = new Form("Details");
        infoF.addCommand(cBack);
        infoF.addCommand(cDl);
        infoF.setCommandListener(this);
        mainL.addCommand(cDl);
        logF.addCommand(cDl);
    }

    private void refreshMain() {
        mainL.set(1, "Results (" + client.results.size() + ")", null);
        int active = 0;
        for (int i = 0; i < client.transfers.size(); i++)
            if (!((Transfer) client.transfers.elementAt(i)).finished()) active++;
        mainL.set(2, "Downloads (" + active + "/" + client.transfers.size() + ")", null);
        mainL.set(3, "Uploads (" + social.activeUploads() + ")", null);
        int pm = social.unreadPMs();
        mainL.set(5, pm > 0 ? "Messages (" + pm + " new)" : "Messages", null);
        mainL.set(7, "Wishlist (" + client.wishResults.size() + " found)", null);
    }

    void showMain() {
        refreshMain();
        display.setCurrent(mainL);
    }

    void showInfo(String title, String text, Displayable back) {
        infoF.deleteAll();
        infoF.setTitle(title);
        infoF.append(new StringItem(null, text));
        infoBack = back;
        display.setCurrent(infoF);
    }

    void alert(String title, String msg, Displayable next) {
        Alert a = new Alert(title, msg, null, AlertType.INFO);
        a.setTimeout(Alert.FOREVER);
        display.setCurrent(a, next);
    }

    private void refreshDownloads() {
        Vector v = client.transfers;
        int n = v.size();
        if (n != shownDl.size()) {
            int sel = dlL.getSelectedIndex();
            dlL.deleteAll();
            shownDl.removeAllElements();
            for (int i = 0; i < n; i++) {
                Transfer t = (Transfer) v.elementAt(i);
                shownDl.addElement(t);
                dlL.append(t.label(), null);
            }
            if (sel >= 0 && sel < n) dlL.setSelectedIndex(sel, true);
        } else {
            for (int i = 0; i < n; i++) {
                String l = ((Transfer) shownDl.elementAt(i)).label();
                if (!l.equals(dlL.getString(i))) dlL.set(i, l, null);
            }
        }
    }

    String resultLabel(Result r) { return resLabel(r); }

    private String resLabel(Result r) {
        int st = client == null ? -1 : client.stateOf(r.user, r.file);
        String mark = st < 0 ? "" : st == Transfer.DONE ? "[OK] " : st >= Transfer.FAILED ? "[X] " : "[DL] ";
        return mark + r.label();
    }

    private void refreshResultMarks() {
        for (int i = 0; i < shown.size(); i++) {
            String l = resLabel((Result) shown.elementAt(i));
            if (!l.equals(resL.getString(i))) resL.set(i, l, null);
        }
    }

    private void showDownloads() {
        refreshDownloads();
        display.setCurrent(dlL);
    }

    private void showAbout(Displayable back) {
        aboutBack = back;
        String ver = getAppProperty("MIDlet-Version");
        Runtime rt = Runtime.getRuntime();
        String platform = System.getProperty("microedition.platform");
        aboutF = new Form("About");
        aboutF.append(new StringItem("SlskME", "Alpha " + (ver == null ? "?" : ver) + "\n"));
        aboutF.append(new StringItem(null, "A Soulseek client for J2ME phones. Talks to the Soulseek network directly, no proxy.\n"));
        aboutF.append(new StringItem("Author", "jecka22lr\n"));
        aboutF.append(new StringItem("Source", "github.com/jecka22lr/SlSkJ2ME\n"));
        aboutF.append(new StringItem("License", "MIT\n"));
        aboutF.append(new StringItem("Protocol", "Based on the Nicotine+ project's Soulseek protocol docs. Not affiliated with Soulseek.\n"));
        aboutF.append(new StringItem("Phone", (platform == null ? "unknown" : platform) + "\n"));
        aboutF.append(new StringItem("Memory", Util.size(rt.freeMemory()) + " free of " + Util.size(rt.totalMemory()) + "\n"));
        String dir = fDir != null ? fDir.getString() : "";
        if (dir.length() > 0) aboutF.append(new StringItem("Downloads", dir + "\n"));
        aboutF.addCommand(cBack);
        aboutF.setCommandListener(this);
        display.setCurrent(aboutF);
    }

    Client client() { return client; }

    Displayable main() { return mainL; }

    void toLogin() { display.setCurrent(loginF); }

    void showResults(String title) {
        resL.deleteAll();
        shown.removeAllElements();
        lastQuery = title;
        resL.setTitle(title);
        display.setCurrent(resL);
    }

    void notifyUser(String s) {
        try { display.vibrate(300); } catch (Throwable e) {}
        Ticker t = SocialUI.ticker(s);
        if (mainL != null) mainL.setTicker(t);
        Displayable cur = display.getCurrent();
        if (cur != null && !(cur instanceof Alert)) cur.setTicker(t);
        log(s);
    }

    void clearTicker() {
        if (mainL != null) mainL.setTicker(null);
        Displayable cur = display.getCurrent();
        if (cur != null) cur.setTicker(null);
    }

    void saveAll() {
        boolean[] rem = new boolean[1];
        fRemember.getSelectedFlags(rem);
        saveSettings(new String[] { fUser.getString().trim(), rem[0] ? fPass.getString() : "",
            fHost.getString().trim(), fPort.getString().trim(), fListen.getString().trim(), fDir.getString().trim(),
            social.description, social.shareDir, social.sharing ? "1" : "0", social.slots });
    }

    private void useFolder(String d) {
        fDir.setString(d);
        if (client != null) client.setDownloadDir(d);
        saveAll();
        log("Download folder set: " + d);
    }

    /** Test every candidate folder and let the user pick a writable one. */
    private void showFolders() {
        folderL = new List("Testing folders...", List.IMPLICIT);
        folderL.addCommand(cBack);
        folderL.setCommandListener(this);
        folderDirs.removeAllElements();
        display.setCurrent(folderL);
        final List l = folderL;
        new Thread(new Runnable() {
            public void run() {
                final Vector v = Store.candidates(fDir.getString().trim());
                for (int i = 0; i < v.size(); i++) {
                    final String d = (String) v.elementAt(i);
                    final boolean ok = Store.probe(d) == null;
                    display.callSerially(new Runnable() {
                        public void run() {
                            if (ok) { folderDirs.insertElementAt(d, 0); l.insert(0, "OK  " + d, null); }
                            else { folderDirs.addElement(null); l.append("no  " + d, null); }
                        }
                    });
                }
                display.callSerially(new Runnable() {
                    public void run() { l.setTitle("Pick a folder (OK = writable)"); }
                });
            }
        }).start();
    }

    private Transfer selectedTransfer() {
        int i = dlL.getSelectedIndex();
        return i >= 0 && i < shownDl.size() ? (Transfer) shownDl.elementAt(i) : null;
    }

    // ------------------------------------------------------------ commands

    public void commandAction(Command c, Displayable d) {
        if (c == cExit) {
            destroyApp(true);
            notifyDestroyed();
        } else if (c == cCancel && d == connF) {
            if (client != null) client.logout();
            client = null;
            display.setCurrent(loginF);
        } else if (c == cDl && client != null) {
            if (d == infoF || d == resL || d == searchTB || d == logF || d == mainL) dlBack = d;
            showDownloads();
        } else if (c == cAbout && d == loginF) {
            showAbout(loginF);
        } else if (d == aboutF) {
            display.setCurrent(aboutBack != null ? aboutBack : loginF);
        } else if (d == loginF && c == cLogin) {
            doLogin();
        } else if (d == mainL && c == List.SELECT_COMMAND) {
            switch (mainL.getSelectedIndex()) {
            case 0: display.setCurrent(searchTB); break;
            case 1: refreshResultMarks(); display.setCurrent(resL); break;
            case 2: dlBack = mainL; showDownloads(); break;
            case 3: social.showUploads(); break;
            case 4: social.showRooms(); break;
            case 5: social.showConvos(); break;
            case 6: social.findUser(); break;
            case 7: social.showWishlist(); break;
            case 8: social.showMyProfile(); break;
            case 9: display.setCurrent(logF); break;
            case 10: showFolders(); break;
            case 11: showAbout(mainL); break;
            case 12:
                client.logout();
                client = null;
                social.reset();
                display.setCurrent(loginF);
                break;
            }
        } else if (d == filterL) {
            if (c == List.SELECT_COMMAND) { filter = filterL.getSelectedIndex(); refilter(); }
            display.setCurrent(resL);
        } else if (d == folderL) {
            if (c == cBack) { showMain(); return; }
            int i = folderL.getSelectedIndex();
            String dir = i >= 0 && i < folderDirs.size() ? (String) folderDirs.elementAt(i) : null;
            if (dir == null) { alert("Folder", "The phone won't let the app write there. Pick one marked OK.", folderL); return; }
            useFolder(dir);
            alert("Folder", "Downloads will be saved to " + dir, mainL);
        } else if (d == searchTB && c == cGo) {
            String q = searchTB.getString().trim();
            if (q.length() == 0) return;
            lastQuery = q;
            resL.deleteAll();
            shown.removeAllElements();
            resL.setTitle("Results: " + q);
            client.search(q);
            display.setCurrent(resL);
        } else if (d == resL) {
            int i = resL.getSelectedIndex();
            Result r = i >= 0 && i < shown.size() ? (Result) shown.elementAt(i) : null;
            if (c == cFilter) {
                filterL = new List("Show", List.IMPLICIT, FILTERS, null);
                filterL.setSelectedIndex(filter, true);
                filterL.addCommand(cBack);
                filterL.setCommandListener(this);
                display.setCurrent(filterL);
            } else if (c == cUser && r != null) {
                social.showUser(r.user, resL);
            } else if (c == cGet && r != null) {
                client.download(r);
                resL.set(i, resLabel(r), null);
                Alert a = new Alert("Queued", Util.baseName(r.file), null, AlertType.CONFIRMATION);
                a.setTimeout(1200);
                display.setCurrent(a, resL);
            } else if (c == cInfo && r != null) {
                showInfo("File", r.details(), resL);
            } else if (c == cNew) {
                display.setCurrent(searchTB);
            } else if (c == cBack) {
                showMain();
            }
        } else if (d == dlL) {
            Transfer t = selectedTransfer();
            if (c == cInfo && t != null) showInfo("Download", t.details(), dlL);
            else if (c == cRetry && t != null) { client.queue(t); refreshDownloads(); }
            else if (c == cPlace && t != null) client.askPlace(t);
            else if (c == cRemove && t != null) { client.remove(t); refreshDownloads(); }
            else if (c == cClear) {
                for (int k = client.transfers.size() - 1; k >= 0; k--) {
                    Transfer x = (Transfer) client.transfers.elementAt(k);
                    if (x.state == Transfer.DONE) client.transfers.removeElementAt(k);
                }
                refreshDownloads();
            } else if (c == cBack) {
                Displayable b = dlBack;
                dlBack = null;
                if (b == resL) refreshResultMarks();
                if (b != null && b != mainL) display.setCurrent(b); else showMain();
            }
        } else if (c == cBack) {
            if (d == infoF && infoBack != null) display.setCurrent(infoBack);
            else showMain();
        }
    }

    private void doLogin() {
        String u = fUser.getString().trim(), p = fPass.getString();
        if (u.length() == 0 || p.length() == 0) {
            alert("Login", "Enter a username and password. New names are registered automatically.", loginF);
            return;
        }
        int port = parse(fPort.getString(), 2242), lp = parse(fListen.getString(), 0);
        String dir = fDir.getString().trim();
        if (!dir.endsWith("/")) dir += "/";
        fDir.setString(dir);
        saveAll();
        buildMain();
        social.reset();
        client = new Client(this, u, p, fHost.getString().trim(), port, lp, dir);
        client.description = social.description;
        try { client.uploadSlots = Math.max(1, Math.min(4, Integer.parseInt(social.slots))); } catch (Exception e) {}
        social.loadWishlist(client.wishlist);
        connF = new Form("Connecting");
        connF.append("Logging in to " + fHost.getString() + "...");
        connF.addCommand(cCancel);
        connF.setCommandListener(this);
        display.setCurrent(connF);
        client.connect();
        if (timer == null) {
            timer = new Timer();
            timer.schedule(new TimerTask() {
                public void run() {
                    display.callSerially(new Runnable() {
                        public void run() {
                            if (client != null && display.getCurrent() == dlL) refreshDownloads();
                            if (client != null && social.showingUploads()) social.refreshUploads();
                        }
                    });
                }
            }, 1000, 1000);
        }
    }

    private static int parse(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    // ------------------------------------------------------------ Client.Listener (network threads)

    public void loggedIn(final boolean ok, final String msg) {
        display.callSerially(new Runnable() {
            public void run() {
                if (ok) {
                    log("Logged in. " + msg);
                    showMain();
                    social.startScan();
                    final String dir = fDir.getString().trim();
                    new Thread(new Runnable() {
                        public void run() {
                            String err = Store.probe(dir);
                            if (err == null) { log("Download folder ok: " + dir); return; }
                            log(err);
                            final String alt = Store.findWritable(dir);
                            final String msg = alt != null
                                ? "Not allowed to write to " + dir + ". Saving downloads to " + alt + " instead."
                                : err + " No writable folder found; see Download folder in the menu.";
                            display.callSerially(new Runnable() {
                                public void run() {
                                    if (alt != null) useFolder(alt);
                                    alert("Download folder", msg, display.getCurrent());
                                }
                            });
                        }
                    }).start();
                } else {
                    client = null;
                    alert("Login failed", msg, loginF);
                }
            }
        });
    }

    public void disconnected(final String why) {
        display.callSerially(new Runnable() {
            public void run() {
                client = null;
                alert("Disconnected", why, loginF);
            }
        });
    }

    private static boolean ext(String f, String list) {
        int dot = f.lastIndexOf('.');
        if (dot < 0) return false;
        return list.indexOf("," + f.substring(dot + 1).toLowerCase() + ",") >= 0;
    }

    private boolean passes(Result r) {
        switch (filter) {
        case 1: return ext(r.file, ",mp3,");
        case 2: return ext(r.file, ",flac,");
        case 3: return ext(r.file, ",mp3,ogg,m4a,aac,opus,wma,mp2,");
        case 4: return ext(r.file, ",flac,wav,ape,alac,aiff,aif,wv,");
        case 5: return r.slot;
        default: return true;
        }
    }

    private void titleResults() {
        int total = client == null ? shown.size() : client.results.size();
        resL.setTitle((filter == 0 ? "" + total : shown.size() + "/" + total) + ": " + lastQuery);
    }

    private void addResult(Result r) {
        if (!passes(r)) return;
        // free-slot results go to the top, queued ones to the bottom
        if (r.slot) {
            int i = 0;
            while (i < shown.size() && ((Result) shown.elementAt(i)).slot) i++;
            shown.insertElementAt(r, i);
            resL.insert(i, resLabel(r), null);
        } else {
            shown.addElement(r);
            resL.append(resLabel(r), null);
        }
    }

    private void refilter() {
        resL.deleteAll();
        shown.removeAllElements();
        Vector v = client.results;
        for (int i = 0; i < v.size(); i++) addResult((Result) v.elementAt(i));
        titleResults();
    }

    public void result(final Result r) {
        display.callSerially(new Runnable() {
            public void run() {
                addResult(r);
                titleResults();
            }
        });
    }

    public void event(final int type, final Object a, final Object b) {
        display.callSerially(new Runnable() {
            public void run() { if (client != null) social.onEvent(type, a, b); }
        });
    }

    public void transferChanged(Transfer t) {
        // the 1 s timer repaints the downloads list; nothing to do here
    }

    public void log(final String s) {
        display.callSerially(new Runnable() {
            public void run() {
                logF.insert(0, new StringItem(null, s + "\n"));
                while (logF.size() > 40) logF.delete(logF.size() - 1);
            }
        });
    }

    // ------------------------------------------------------------ settings (RMS)

    private String[] loadSettings() {
        String[] s = { "", "", "server.slsknet.org", "2242", "0", null, "", "", "0", "1" };
        try {
            RecordStore rs = RecordStore.openRecordStore("slskme", true);
            if (rs.getNumRecords() > 0) {
                DataInputStream in = new DataInputStream(new ByteArrayInputStream(rs.getRecord(1)));
                for (int i = 0; i < s.length; i++) s[i] = in.readUTF();
            }
            rs.closeRecordStore();
        } catch (Exception e) {}
        if (s[5] == null || s[5].length() == 0) s[5] = Store.defaultDir();
        return s;
    }

    private void saveSettings(String[] s) {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bo);
            for (int i = 0; i < s.length; i++) out.writeUTF(s[i]);
            byte[] b = bo.toByteArray();
            RecordStore rs = RecordStore.openRecordStore("slskme", true);
            if (rs.getNumRecords() > 0) rs.setRecord(1, b, 0, b.length);
            else rs.addRecord(b, 0, b.length);
            rs.closeRecordStore();
        } catch (Exception e) {}
    }
}
