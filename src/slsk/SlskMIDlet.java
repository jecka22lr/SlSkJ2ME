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
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
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
    private final Vector shownDl = new Vector(); // Transfers in dlL order
    private Timer timer;
    private String lastQuery = "";
    private Displayable dlBack;

    private final Command cExit = new Command("Exit", Command.EXIT, 9);
    private final Command cLogin = new Command("Log in", Command.OK, 1);
    private final Command cBack = new Command("Back", Command.BACK, 2);
    private final Command cGo = new Command("Search", Command.OK, 1);
    private final Command cGet = new Command("Open", "Download / open", Command.ITEM, 1);
    private final Command cInfo = new Command("Details", Command.ITEM, 2);
    private final Command cNew = new Command("New search", Command.SCREEN, 3);
    private final Command cRetry = new Command("Retry", Command.ITEM, 2);
    private final Command cPlace = new Command("Place", "Queue place", Command.ITEM, 3);
    private final Command cRemove = new Command("Remove", Command.ITEM, 4);
    private final Command cClear = new Command("Clear", "Clear finished", Command.SCREEN, 5);
    private final Command cCancel = new Command("Cancel", Command.CANCEL, 1);
    private final Command cDl = new Command("Downloads", "My downloads", Command.SCREEN, 4);
    private final Command cAbout = new Command("About", Command.HELP, 8);
    private Form aboutF;
    private Displayable aboutBack;
    private Form connF;

    private static final String[] MENU = { "Search", "Results", "My downloads", "Uploads", "Chat rooms", "Messages",
        "Find user", "Buddies", "Wishlist", "Profile & sharing", "Log", "Save location", "About", "Log out" };
    private final Command cPlay = new Command("Play", "Play / open file", Command.ITEM, 1);
    private final Command cHistory = new Command("History", "Recent searches", Command.SCREEN, 2);
    private final Command cMore = new Command("More", "More filters", Command.SCREEN, 3);
    private List historyL;
    private final Vector history = new Vector();
    private Form moreF;
    private TextField fMinBr, fMinMb, fExclude;
    private int minBitrate;
    private long minSize;
    private String[] excludeWords = new String[0];
    private SocialUI social;
    private String[] settings;
    private final Command cUser = new Command("User", Command.ITEM, 3);
    private final Command cExitNow = new Command("Exit", "Exit anyway", Command.OK, 1);
    private final Command cStay = new Command("Stay", Command.CANCEL, 2);
    private final Command cBrowse = new Command("Browse", "Browse folders", Command.SCREEN, 2);
    private final Command cDirYes = new Command("Get all", "Download all", Command.OK, 1);
    private final Command cDirNo = new Command("Cancel", Command.CANCEL, 2);
    private final Command cFilter = new Command("Filter", Command.SCREEN, 2);
    private static final String[] FILTERS = { "All files", "MP3 only", "FLAC only", "Lossy (mp3/ogg/m4a/opus...)",
        "Lossless (flac/wav/ape/alac)", "Free slots only" };
    private int filter;
    private List filterL;
    private static final int PAGE = 80;
    private int page;
    private volatile boolean resDirty;
    private final Command cNext = new Command("Next", "Next page", Command.SCREEN, 1);
    private final Command cPrev = new Command("Prev", "Previous page", Command.SCREEN, 2);
    private List folderL;
    private final Vector folderDirs = new Vector();

    protected void startApp() {
        if (started) return;
        started = true;
        display = Display.getDisplay(this);
        social = new SocialUI(this, display);
        loadStrings("slskme-hist", history);
        loadFilters();
        buildLogin();
        logF = new Form("Log");
        logF.addCommand(cBack);
        logF.setCommandListener(this);
        display.setCurrent(loginF);
    }

    protected void pauseApp() {}

    /** null if nothing is transferring, else a short description. */
    private String busyText() {
        if (client == null) return null;
        int d = 0, u = 0;
        for (int i = 0; i < client.transfers.size(); i++) if (((Transfer) client.transfers.elementAt(i)).active()) d++;
        for (int i = 0; i < client.uploads.size(); i++) if (((Upload) client.uploads.elementAt(i)).active()) u++;
        if (d == 0 && u == 0) return null;
        return (d > 0 ? d + " download(s)" : "") + (d > 0 && u > 0 ? " and " : "") + (u > 0 ? u + " upload(s)" : "")
            + " in progress.";
    }

    protected void destroyApp(boolean u) {
        if (client != null) saveQueue();
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
        social.upLimit = s[10];
        social.downLimit = s[11];
        social.perUser = s[12];
        loginF = new Form("SlskME");
        fUser = new TextField("Username", s[0], 30, TextField.ANY | TextField.NON_PREDICTIVE);
        fPass = new TextField("Password", s[1], 30, TextField.PASSWORD);
        fHost = new TextField("Server", s[2], 64, TextField.URL);
        fPort = new TextField("Port", s[3], 5, TextField.NUMERIC);
        fListen = new TextField("Listen port (0 = off)", s[4], 5, TextField.NUMERIC);
        fDir = new TextField("Save location", s[5], 128, TextField.URL);
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
        searchTB.addCommand(cHistory);
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

        dlL = new List("My downloads", List.IMPLICIT);
        dlL.setSelectCommand(cInfo);
        dlL.addCommand(cPlay);
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
        mainL.set(2, "My downloads (" + active + "/" + client.transfers.size() + ")", null);
        mainL.set(3, "Uploads (" + social.activeUploads() + ")", null);
        int pm = social.unreadPMs();
        mainL.set(5, pm > 0 ? "Messages (" + pm + " new)" : "Messages", null);
        int on = social.buddiesOnline();
        mainL.set(7, client.buddies.size() == 0 ? "Buddies" : "Buddies (" + on + "/" + client.buddies.size() + " online)", null);
        mainL.set(8, "Wishlist (" + client.wishResults.size() + " found)", null);
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

    private void refreshResultMarks() { resDirty = true; renderPage(); }

    private final ResultGroups groups = new ResultGroups();
    private Vector rows = new Vector();      // all rows (User, Dir, Result), filtered
    private final Vector pageRows = new Vector(); // what resL shows right now
    private Image icUser, icDir, icFile;
    private ResultGroups.Dir pendingDir;
    private final ResultGroups.Filter rowFilter = new ResultGroups.Filter() {
        public boolean passes(Result r) { return SlskMIDlet.this.passes(r); }
    };

    private int pages() { return Math.max(1, (rows.size() + PAGE - 1) / PAGE); }

    private long searchStarted;

    /** The phone shows "No results" for an empty list; say what's actually going on instead. */
    private String placeholder() {
        int total = groups.total();
        if (total > 0) return "(" + total + " results, none match the filter)";
        long secs = (System.currentTimeMillis() - searchStarted) / 1000;
        if (searchStarted == 0) return "(no search yet)";
        if (secs < 30) return "Searching... " + secs + " s";
        return "No results yet (still listening)";
    }

    /** Small drawn icons, so user / folder / file rows are easy to tell apart. */
    private void makeIcons() {
        if (icUser != null) return;
        try {
            int s = 12;
            Image m = Image.createImage(s, s);
            Graphics g = m.getGraphics();
            g.setColor(0xffffff); g.fillRect(0, 0, s, s);
            g.setColor(0x2a9d8f); g.fillArc(3, 0, 6, 6, 0, 360); g.fillArc(1, 6, 10, 10, 0, 180);
            icUser = Image.createImage(m);
            m = Image.createImage(s, s); g = m.getGraphics();
            g.setColor(0xffffff); g.fillRect(0, 0, s, s);
            g.setColor(0xe9a23b); g.fillRect(0, 2, 5, 2); g.fillRect(0, 3, 12, 8);
            icDir = Image.createImage(m);
            m = Image.createImage(s, s); g = m.getGraphics();
            g.setColor(0xffffff); g.fillRect(0, 0, s, s);
            g.setColor(0x6c757d); g.fillArc(1, 7, 5, 4, 0, 360); g.fillRect(5, 1, 2, 8); g.fillRect(5, 1, 5, 2);
            icFile = Image.createImage(m);
        } catch (Throwable e) {}
    }

    private String rowLabel(Object o) {
        if (o instanceof ResultGroups.User) return ResultGroups.userLabel((ResultGroups.User) o);
        if (o instanceof ResultGroups.Dir) {
            ResultGroups.Dir d = (ResultGroups.Dir) o;
            return ResultGroups.dirLabel(d, ResultGroups.files(d, rowFilter).size());
        }
        Result r = (Result) o;
        int st = client == null ? -1 : client.stateOf(r.user, r.file);
        String mark = st < 0 ? "  " : st == Transfer.DONE ? "  [OK] " : st >= Transfer.FAILED ? "  [X] " : "  [DL] ";
        return mark + ResultGroups.fileLabel(r);
    }

    private Image rowIcon(Object o) {
        return o instanceof ResultGroups.User ? icUser : o instanceof ResultGroups.Dir ? icDir : icFile;
    }

    /** Show the current page; repeats the user/folder headers if a page starts mid-group. */
    private void renderPage() {
        if (resDirty) { rows = groups.rows(rowFilter); resDirty = false; }
        if (rows.size() == 0) {
            pageRows.removeAllElements();
            String p = placeholder();
            if (resL.size() == 1 && p.equals(resL.getString(0))) return;
            resL.deleteAll();
            resL.append(p, null);
            titleResults();
            return;
        }
        makeIcons();
        if (page >= pages()) page = pages() - 1;
        int start = page * PAGE;
        int end = Math.min(rows.size(), start + PAGE);
        pageRows.removeAllElements();
        Object first = rows.elementAt(start);
        if (first instanceof Result) {
            Result r = (Result) first;
            for (int k = start - 1; k >= 0; k--) {
                Object o = rows.elementAt(k);
                if (o instanceof ResultGroups.Dir && pageRows.size() == 0) pageRows.addElement(o);
                if (o instanceof ResultGroups.User && ((ResultGroups.User) o).name.equals(r.user)) {
                    pageRows.insertElementAt(o, 0);
                    break;
                }
            }
        } else if (first instanceof ResultGroups.Dir) {
            pageRows.addElement(((ResultGroups.Dir) first).user);
        }
        for (int k = start; k < end; k++) pageRows.addElement(rows.elementAt(k));
        for (int k = 0; k < pageRows.size(); k++) {
            Object o = pageRows.elementAt(k);
            String l = rowLabel(o);
            if (k < resL.size()) { if (!l.equals(resL.getString(k))) resL.set(k, l, rowIcon(o)); }
            else resL.append(l, rowIcon(o));
        }
        while (resL.size() > pageRows.size()) resL.delete(resL.size() - 1);
        titleResults();
    }

    private void clearResults(String title) {
        resL.deleteAll();
        groups.clear();
        rows = new Vector();
        pageRows.removeAllElements();
        page = 0;
        resDirty = false;
        lastQuery = title;
        searchStarted = System.currentTimeMillis();
        resL.setTitle(title);
        renderPage();
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
        aboutF.append(new StringItem("SlskME", "Beta " + (ver == null ? "?" : ver) + "\n"));
        aboutF.append(new StringItem(null, "A Soulseek client for J2ME phones. Talks to the Soulseek network directly, no proxy.\n"));
        aboutF.append(new StringItem("Author", "jecka22lr\n"));
        aboutF.append(new StringItem("Source", "github.com/jecka22lr/SlSkJ2ME\n"));
        aboutF.append(new StringItem("License", "MIT\n"));
        aboutF.append(new StringItem("Protocol", "Based on the Nicotine+ project's Soulseek protocol docs. Not affiliated with Soulseek.\n"));
        aboutF.append(new StringItem("Phone", (platform == null ? "unknown" : platform) + "\n"));
        aboutF.append(new StringItem("Memory", Util.size(rt.freeMemory()) + " free of " + Util.size(rt.totalMemory()) + "\n"));
        String dir = fDir != null ? fDir.getString() : "";
        if (dir.length() > 0) aboutF.append(new StringItem("Saving to", Store.friendly(dir) + "\n"));
        aboutF.addCommand(cBack);
        aboutF.setCommandListener(this);
        display.setCurrent(aboutF);
    }

    Client client() { return client; }

    Displayable main() { return mainL; }

    void toLogin() { display.setCurrent(loginF); }

    void showResults(String title) {
        clearResults(title);
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
            social.description, social.shareDir, social.sharing ? "1" : "0", social.slots,
            social.upLimit, social.downLimit, social.perUser });
    }

    private void useFolder(String d) {
        fDir.setString(d);
        if (client != null) client.setDownloadDir(d);
        saveAll();
        log("Save location: " + Store.friendly(d));
    }

    /** Test every candidate folder and let the user pick a writable one. */
    private void showFolders() {
        folderL = new List("Testing folders...", List.IMPLICIT);
        folderL.addCommand(cBrowse);
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
                            if (ok) { folderDirs.insertElementAt(d, 0); l.insert(0, "OK  " + Store.friendly(d), null); }
                            else { folderDirs.addElement(null); l.append("no  " + Store.friendly(d), null); }
                        }
                    });
                }
                display.callSerially(new Runnable() {
                    public void run() { l.setTitle("Pick a folder (OK = writable)"); }
                });
            }
        }).start();
    }

    private final Command cOkMore = new Command("OK", Command.OK, 1);

    private void runSearch(String q) {
        history.removeElement(q);
        history.insertElementAt(q, 0);
        while (history.size() > 15) history.removeElementAt(history.size() - 1);
        saveHistory();
        clearResults("Results: " + q);
        lastQuery = q;
        client.search(q);
        display.setCurrent(resL);
    }

    private void showMoreFilters() {
        moreF = new Form("More filters");
        fMinBr = new TextField("Min bitrate (kbps, 0 = any)", minBitrate > 0 ? "" + minBitrate : "", 4, TextField.NUMERIC);
        fMinMb = new TextField("Min size (MB, 0 = any)", minSize > 0 ? "" + (minSize / 1048576L) : "", 5, TextField.NUMERIC);
        StringBuffer ex = new StringBuffer();
        for (int k = 0; k < excludeWords.length; k++) ex.append(k > 0 ? " " : "").append(excludeWords[k]);
        fExclude = new TextField("Hide files with these words", ex.toString(), 100, TextField.ANY);
        moreF.append(fMinBr);
        moreF.append(fMinMb);
        moreF.append(fExclude);
        moreF.append(new StringItem(null, "Files with unknown bitrate still show. Kept until you change them."));
        moreF.addCommand(cOkMore);
        moreF.addCommand(cBack);
        moreF.setCommandListener(this);
        display.setCurrent(moreF);
    }

    static String[] words(String s) {
        Vector v = new Vector();
        int i = 0;
        while (i < s.length()) {
            while (i < s.length() && s.charAt(i) == ' ') i++;
            int j = i;
            while (j < s.length() && s.charAt(j) != ' ') j++;
            if (j > i) v.addElement(s.substring(i, j));
            i = j;
        }
        String[] a = new String[v.size()];
        v.copyInto(a);
        return a;
    }

    /** Hand a finished download to the phone's own player (or whatever app opens that file type). */
    private void play(Transfer t) {
        if (t.state != Transfer.DONE) { alert("Play", "Not downloaded yet.", dlL); return; }
        String url = t.path != null ? t.path : Store.guessPath(fDir.getString().trim(), t);
        try {
            if (platformRequest(url)) alert("Play", "Close SlskME to open the file. Downloads stop while it's closed.", dlL);
        } catch (Throwable e) {
            alert("Play", "The phone couldn't open " + Store.friendly(url) + ": " + e.getMessage()
                + ". Open it from the phone's Music player or file manager.", dlL);
        }
    }

    private void saveHistory() { saveStrings("slskme-hist", history); }

    private void saveFilters() {
        Vector v = new Vector();
        v.addElement("" + minBitrate);
        v.addElement("" + minSize);
        StringBuffer ex = new StringBuffer();
        for (int k = 0; k < excludeWords.length; k++) ex.append(k > 0 ? " " : "").append(excludeWords[k]);
        v.addElement(ex.toString());
        saveStrings("slskme-flt", v);
    }

    private void loadFilters() {
        Vector v = new Vector();
        loadStrings("slskme-flt", v);
        if (v.size() < 3) return;
        minBitrate = parse((String) v.elementAt(0), 0);
        try { minSize = Long.parseLong((String) v.elementAt(1)); } catch (Exception e) {}
        excludeWords = words((String) v.elementAt(2));
    }

    static void saveStrings(String store, Vector v) {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bo);
            out.writeInt(v.size());
            for (int i = 0; i < v.size(); i++) out.writeUTF((String) v.elementAt(i));
            byte[] b = bo.toByteArray();
            RecordStore rs = RecordStore.openRecordStore(store, true);
            if (rs.getNumRecords() > 0) rs.setRecord(1, b, 0, b.length);
            else rs.addRecord(b, 0, b.length);
            rs.closeRecordStore();
        } catch (Exception e) {}
    }

    static void loadStrings(String store, Vector into) {
        try {
            RecordStore rs = RecordStore.openRecordStore(store, true);
            if (rs.getNumRecords() > 0) {
                DataInputStream in = new DataInputStream(new ByteArrayInputStream(rs.getRecord(1)));
                int n = in.readInt();
                for (int i = 0; i < n; i++) into.addElement(in.readUTF());
            }
            rs.closeRecordStore();
        } catch (Exception e) {}
    }

    private Transfer selectedTransfer() {
        int i = dlL.getSelectedIndex();
        return i >= 0 && i < shownDl.size() ? (Transfer) shownDl.elementAt(i) : null;
    }

    // ------------------------------------------------------------ commands

    public void commandAction(Command c, Displayable d) {
        if (c == cExit || c == cExitNow) {
            String busy = c == cExit ? busyText() : null;
            if (busy != null) {
                Alert a = new Alert("Exit?", busy + " Exiting stops them (downloads resume next time).", null, AlertType.WARNING);
                a.setTimeout(Alert.FOREVER);
                a.addCommand(cExitNow);
                a.addCommand(cStay);
                a.setCommandListener(this);
                display.setCurrent(a);
                return;
            }
            destroyApp(true);
            notifyDestroyed();
        } else if (c == cStay) {
            showMain();
        } else if (c == cDirYes || c == cDirNo) {
            if (c == cDirYes && pendingDir != null && client != null) {
                Vector v = ResultGroups.files(pendingDir, rowFilter);
                String sub = Util.baseName(pendingDir.path);
                for (int k = 0; k < v.size(); k++) client.downloadInto((Result) v.elementAt(k), sub);
                log("Queued " + v.size() + " files from " + ResultGroups.shortDir(pendingDir.path));
                resDirty = true;
                renderPage();
            }
            pendingDir = null;
            display.setCurrent(resL);
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
            case 7: social.showBuddies(); break;
            case 8: social.showWishlist(); break;
            case 9: social.showMyProfile(); break;
            case 10: display.setCurrent(logF); break;
            case 11: showFolders(); break;
            case 12: showAbout(mainL); break;
            case 13:
                saveQueue();
                client.logout();
                client = null;
                social.reset();
                display.setCurrent(loginF);
                break;
            }
        } else if (d == filterL) {
            if (c == cMore) { showMoreFilters(); return; }
            if (c == List.SELECT_COMMAND) { filter = filterL.getSelectedIndex(); refilter(); }
            display.setCurrent(resL);
        } else if (d == folderL) {
            if (c == cBack) { showMain(); return; }
            if (c == cBrowse) {
                new FolderPicker(display, "Pick save location", new FolderPicker.Done() {
                    public void picked(final String url) {
                        if (url == null) { display.setCurrent(folderL); return; }
                        new Thread(new Runnable() {
                            public void run() {
                                final String err = Store.probe(url);
                                display.callSerially(new Runnable() {
                                    public void run() {
                                        if (err != null) { alert("Save location", err + " Pick another folder.", folderL); return; }
                                        useFolder(url);
                                        alert("Save location", "Downloads will be saved to " + Store.friendly(url), mainL);
                                    }
                                });
                            }
                        }).start();
                    }
                }).show(fDir.getString().trim());
                return;
            }
            int i = folderL.getSelectedIndex();
            String dir = i >= 0 && i < folderDirs.size() ? (String) folderDirs.elementAt(i) : null;
            if (dir == null) { alert("Folder", "The phone won't let the app write there. Pick one marked OK.", folderL); return; }
            useFolder(dir);
            alert("Save location", "Downloads will be saved to " + Store.friendly(dir), mainL);
        } else if (d == searchTB && c == cGo) {
            String q = searchTB.getString().trim();
            if (q.length() == 0) return;
            runSearch(q);
        } else if (d == searchTB && c == cHistory) {
            historyL = new List("Recent searches", List.IMPLICIT);
            for (int k = 0; k < history.size(); k++) historyL.append((String) history.elementAt(k), null);
            if (history.size() == 0) historyL.append("(none yet)", null);
            historyL.addCommand(cRemove);
            historyL.addCommand(cBack);
            historyL.setCommandListener(this);
            display.setCurrent(historyL);
        } else if (d == historyL) {
            int k = historyL.getSelectedIndex();
            String q = k >= 0 && k < history.size() ? (String) history.elementAt(k) : null;
            if (c == cBack) display.setCurrent(searchTB);
            else if (c == cRemove && q != null) { history.removeElementAt(k); saveHistory(); historyL.delete(k); }
            else if (c == List.SELECT_COMMAND && q != null) { searchTB.setString(q); runSearch(q); }
        } else if (d == moreF) {
            if (c == cOkMore) {
                minBitrate = parse(fMinBr.getString(), 0);
                minSize = parse(fMinMb.getString(), 0) * 1024L * 1024L;
                excludeWords = words(fExclude.getString().toLowerCase());
                saveFilters();
                refilter();
            }
            display.setCurrent(resL);
        } else if (d == resL) {
            int i = resL.getSelectedIndex();
            Object row = i >= 0 && i < pageRows.size() ? pageRows.elementAt(i) : null;
            Result r = row instanceof Result ? (Result) row : null;
            String rowUser = row instanceof Result ? ((Result) row).user
                    : row instanceof ResultGroups.User ? ((ResultGroups.User) row).name
                    : row instanceof ResultGroups.Dir ? ((ResultGroups.Dir) row).user.name : null;
            if (c == cNext || c == cPrev) {
                int np = page + (c == cNext ? 1 : -1);
                if (np >= 0 && np < pages()) {
                    page = np;
                    renderPage();
                    if (resL.size() > 0) resL.setSelectedIndex(0, true);
                }
            } else if (c == cGet && row instanceof ResultGroups.User) {
                social.showUser(rowUser, resL);
            } else if (c == cGet && row instanceof ResultGroups.Dir) {
                pendingDir = (ResultGroups.Dir) row;
                int n = ResultGroups.files(pendingDir, rowFilter).size();
                Alert al = new Alert("Download folder?", "Download all " + n + " files in "
                        + ResultGroups.shortDir(pendingDir.path) + " from " + rowUser + "?", null, AlertType.CONFIRMATION);
                al.setTimeout(Alert.FOREVER);
                al.addCommand(cDirYes);
                al.addCommand(cDirNo);
                al.setCommandListener(this);
                display.setCurrent(al);
            } else if (c == cFilter) {
                filterL = new List("Show", List.IMPLICIT, FILTERS, null);
                filterL.setSelectedIndex(filter, true);
                filterL.addCommand(cMore);
                filterL.addCommand(cBack);
                filterL.setCommandListener(this);
                display.setCurrent(filterL);
            } else if (c == cUser && rowUser != null) {
                social.showUser(rowUser, resL);
            } else if (c == cGet && r != null) {
                client.download(r);
                resL.set(i, rowLabel(r), icFile);
                Alert a = new Alert("Queued", Util.baseName(r.file), null, AlertType.CONFIRMATION);
                a.setTimeout(1200);
                display.setCurrent(a, resL);
            } else if (c == cInfo && r != null) {
                showInfo("File", r.details(), resL);
            } else if (c == cInfo && row instanceof ResultGroups.Dir) {
                ResultGroups.Dir dd = (ResultGroups.Dir) row;
                showInfo("Folder", dd.path.replace('\\', '/') + "\n\nUser: " + dd.user.name + "\nFiles: " + dd.files.size(), resL);
            } else if (c == cNew) {
                display.setCurrent(searchTB);
            } else if (c == cBack) {
                showMain();
            }
        } else if (d == dlL) {
            Transfer t = selectedTransfer();
            if (c == cInfo && t != null) showInfo("Download", t.details(), dlL);
            else if (c == cPlay && t != null) play(t);
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
        social.applyLimits(client);
        social.loadWishlist(client.wishlist);
        social.loadPeople(client);
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
                            if (client != null && display.getCurrent() == resL && (resDirty || rows.size() == 0)) renderPage();
                            if (client != null) saveQueueIfChanged();
                            if (client != null && social.showingUploads()) social.refreshUploads();
                        }
                    });
                }
            }, 1000, 1000);
        }
    }

    private String queueSig = "";

    /** Saves the download list when its contents or states change (checked once a second). */
    private void saveQueueIfChanged() {
        StringBuffer sb = new StringBuffer();
        Vector v = client.transfers;
        for (int i = 0; i < v.size(); i++) sb.append(((Transfer) v.elementAt(i)).state);
        String sig = v.size() + ":" + sb.toString();
        if (sig.equals(queueSig)) return;
        queueSig = sig;
        saveQueue();
    }

    private void saveQueue() {
        try {
            Vector v = client.transfers;
            int start = Math.max(0, v.size() - 200);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bo);
            out.writeUTF(client.username);
            out.writeInt(v.size() - start);
            for (int i = start; i < v.size(); i++) {
                Transfer t = (Transfer) v.elementAt(i);
                out.writeUTF(t.user);
                out.writeUTF(t.file);
                byte[] raw = Util.rawBytes(t.file); // non-UTF-8 name: keep the exact bytes the peer knows
                out.writeInt(raw == null ? 0 : raw.length);
                if (raw != null) out.write(raw);
                out.writeLong(t.size);
                out.writeBoolean(t.state == Transfer.DONE);
                out.writeUTF(t.path == null ? "" : t.path);
                out.writeUTF(t.subdir == null ? "" : t.subdir);
            }
            byte[] b = bo.toByteArray();
            RecordStore rs = RecordStore.openRecordStore("slskme-dl2", true);
            if (rs.getNumRecords() > 0) rs.setRecord(1, b, 0, b.length);
            else rs.addRecord(b, 0, b.length);
            rs.closeRecordStore();
        } catch (Exception e) {}
    }

    private static byte[] record(String store) {
        try {
            RecordStore rs = RecordStore.openRecordStore(store, true);
            byte[] b = rs.getNumRecords() > 0 ? rs.getRecord(1) : null;
            rs.closeRecordStore();
            return b;
        } catch (Exception e) { return null; }
    }

    /** Puts back last session's downloads and re-queues the unfinished ones. */
    private void restoreQueue() {
        try {
            byte[] b = record("slskme-dl2");
            boolean v2 = b != null;
            if (!v2) b = record("slskme-dl"); // saved by 0.4.x
            if (b == null) return;
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(b));
            if (!in.readUTF().equals(client.username)) return;
            int n = in.readInt(), again = 0;
            for (int i = 0; i < n; i++) {
                String user = in.readUTF(), file = in.readUTF();
                if (v2) {
                    int rl = in.readInt();
                    if (rl > 0) {
                        byte[] raw = new byte[rl];
                        in.readFully(raw);
                        Util.rememberRaw(file, raw);
                    }
                }
                long size = in.readLong();
                boolean done = in.readBoolean();
                String path = v2 ? in.readUTF() : "", sub = v2 ? in.readUTF() : "";
                Transfer t = client.restore(user, file, size, done);
                if (path.length() > 0) t.path = path;
                if (sub.length() > 0) t.subdir = sub;
                if (!done) { client.queue(t); again++; }
            }
            if (again > 0) log("Resuming " + again + " download(s) from last time");
        } catch (Exception e) {}
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
                    restoreQueue();
                    showMain();
                    social.startScan();
                    final String dir = fDir.getString().trim();
                    new Thread(new Runnable() {
                        public void run() {
                            String err = Store.probe(dir);
                            if (err == null) { log("Saving to " + Store.friendly(dir)); return; }
                            log(err);
                            final String alt = Store.findWritable(dir);
                            final String msg = alt != null
                                ? "Not allowed to write to " + Store.friendly(dir) + ". Saving downloads to " + Store.friendly(alt) + " instead."
                                : err + " No writable folder found; see Save location in the menu.";
                            display.callSerially(new Runnable() {
                                public void run() {
                                    if (alt != null) useFolder(alt);
                                    alert("Save location", msg, display.getCurrent());
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
        if (minBitrate > 0 && r.bitrate > 0 && r.bitrate < minBitrate) return false;
        if (minSize > 0 && r.size < minSize) return false;
        if (excludeWords.length > 0) {
            String f = r.file.toLowerCase();
            for (int k = 0; k < excludeWords.length; k++) if (f.indexOf(excludeWords[k]) >= 0) return false;
        }
        switch (filter) {
        case 1: return ext(r.file, ",mp3,");
        case 2: return ext(r.file, ",flac,");
        case 3: return ext(r.file, ",mp3,ogg,m4a,aac,opus,wma,mp2,");
        case 4: return ext(r.file, ",flac,wav,ape,alac,aiff,aif,wv,");
        case 5: return r.slot;
        default: return true;
        }
    }

    private boolean pageCmds;

    private void updatePageCommands() {
        boolean want = pages() > 1;
        if (want == pageCmds) return;
        pageCmds = want;
        if (want) { resL.addCommand(cNext); resL.addCommand(cPrev); }
        else { resL.removeCommand(cNext); resL.removeCommand(cPrev); }
    }

    private void titleResults() {
        updatePageCommands();
        int total = groups.total();
        int shownFiles = 0;
        if (filter != 0) for (int k = 0; k < rows.size(); k++) if (rows.elementAt(k) instanceof Result) shownFiles++;
        boolean any = filter != 0 || minBitrate > 0 || minSize > 0 || excludeWords.length > 0;
        if (any && filter == 0) for (int k = 0; k < rows.size(); k++) if (rows.elementAt(k) instanceof Result) shownFiles++;
        String count = !any ? "" + total : shownFiles + "/" + total;
        String pg = pages() > 1 ? "p" + (page + 1) + "/" + pages() + " " : "";
        resL.setTitle(pg + count + ": " + lastQuery);
    }

    private void refilter() {
        page = 0;
        resDirty = true;
        renderPage();
    }

    public void result(final Result r) {
        display.callSerially(new Runnable() {
            public void run() {
                groups.add(r);
                resDirty = true;
                // fill the first screen live; after that the 1 s timer repaints in batches
                if (page == 0 && groups.total() <= 30) renderPage();
            }
        });
    }

    public void event(final int type, final Object a, final Object b) {
        display.callSerially(new Runnable() {
            public void run() {
                if (client == null) return;
                if (type == Client.EV_CONN) {
                    log((String) a);
                    Ticker t = "Reconnected".equals(a) ? null : new Ticker((String) a);
                    if (mainL != null) mainL.setTicker(t);
                    Displayable cur = display.getCurrent();
                    if (cur != null && !(cur instanceof Alert)) cur.setTicker(t);
                    if (t == null) refreshMain();
                    return;
                }
                social.onEvent(type, a, b);
            }
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
        String[] s = { "", "", "server.slsknet.org", "2242", "0", null, "", "", "0", "1", "0", "0", "50" };
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
