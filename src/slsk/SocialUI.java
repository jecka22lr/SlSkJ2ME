package slsk;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Enumeration;
import java.util.Vector;
import javax.microedition.lcdui.ChoiceGroup;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.ImageItem;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;
import javax.microedition.lcdui.Ticker;
import javax.microedition.rms.RecordStore;

/** Chat rooms, private messages, user profiles, browsing, wishlist, own profile & sharing, uploads. */
final class SocialUI implements CommandListener {
    private final SlskMIDlet m;
    private final Display d;

    private final Command cBack = new Command("Back", Command.BACK, 2);
    private final Command cOk = new Command("OK", Command.OK, 1);
    private final Command cSay = new Command("Write", Command.SCREEN, 1);
    private final Command cUsers = new Command("Users", Command.SCREEN, 2);
    private final Command cLeave = new Command("Leave room", Command.SCREEN, 3);
    private final Command cJoinName = new Command("Join", "Join by name", Command.SCREEN, 2);
    private final Command cRefresh = new Command("Refresh", Command.SCREEN, 3);
    private final Command cNewPm = new Command("New", "New message", Command.SCREEN, 2);
    private final Command cProfile = new Command("User", Command.SCREEN, 2);
    private final Command cGet = new Command("Get", "Download file", Command.ITEM, 1);
    private final Command cGetAll = new Command("Get all", "Download whole folder", Command.SCREEN, 2);
    private final Command cInfo = new Command("Details", Command.ITEM, 3);
    private final Command cAdd = new Command("Add", Command.SCREEN, 2);
    private final Command cRemove = new Command("Remove", Command.ITEM, 3);
    private final Command cNow = new Command("Search now", Command.ITEM, 4);
    private final Command cSave = new Command("Save", Command.OK, 1);
    private final Command cRescan = new Command("Rescan", "Rescan shares", Command.SCREEN, 2);
    private final Command cClear = new Command("Clear", "Clear finished", Command.SCREEN, 4);
    private final Command cPick = new Command("Pick folder", "Pick shared folder", Command.SCREEN, 2);
    private final Command cSearchRoom = new Command("Search", "Search this room", Command.SCREEN, 4);
    private final Command cSearchBuddies = new Command("Search", "Search buddies' files", Command.SCREEN, 3);
    private final Command cBlocked = new Command("Blocked", "Ignored & banned", Command.SCREEN, 4);

    // rooms
    private List roomsL, usersL;
    private Form roomF;
    private Room curRoom;
    private final Vector roomRows = new Vector(); // String room names in roomsL order
    // messages
    private List convosL;
    private Form convoF;
    private Convo curConvo;
    // text input (shared)
    private TextBox inputTB;
    private int inputMode; // 1 say room, 2 pm, 3 new pm user, 4 find user, 5 user search, 6 join room, 7 wish add
    private Displayable inputBack;
    // users
    private List userL;
    private String curUser;
    private Displayable userBack;
    private Form profileF;
    // browse
    private List browseL, filesL;
    private Browse curBrowse;
    private Result[] curFiles;
    private String browsing;
    // wishlist
    private List wishL, wishResL;
    // own profile
    private Form myF;
    private TextField fDesc, fShare, fSlots;
    private ChoiceGroup fStatus, fSharing;
    private StringItem shareInfo;
    // buddies & blocked
    private List buddiesL, blockedL;
    private final Vector blockedRows = new Vector(); // String[]{"i"|"b", name}
    private TextField fUpLimit, fDownLimit, fPerUser;
    // uploads
    private List upL;
    private final Vector upRows = new Vector();

    String description = "", shareDir = "", slots = "1", upLimit = "0", downLimit = "0", perUser = "50";
    boolean sharing;

    SocialUI(SlskMIDlet m, Display d) { this.m = m; this.d = d; }

    private Client c() { return m.client(); }

    private TextBox input(String title, String text, int mode, Displayable back) {
        inputTB = new TextBox(title, text, 300, TextField.ANY);
        inputTB.addCommand(cOk);
        inputTB.addCommand(cBack);
        inputTB.setCommandListener(this);
        inputMode = mode;
        inputBack = back;
        d.setCurrent(inputTB);
        return inputTB;
    }

    // ================================================================ rooms

    void showRooms() {
        if (roomsL == null) {
            roomsL = new List("Chat rooms", List.IMPLICIT);
            roomsL.addCommand(cJoinName);
            roomsL.addCommand(cRefresh);
            roomsL.addCommand(cBack);
            roomsL.setCommandListener(this);
            c().requestRoomList();
        }
        fillRooms();
        d.setCurrent(roomsL);
    }

    private void fillRooms() {
        Client cl = c();
        roomsL.deleteAll();
        roomRows.removeAllElements();
        Enumeration e = cl.rooms.elements();
        while (e.hasMoreElements()) {
            Room r = (Room) e.nextElement();
            roomsL.append("> " + r.name + " (" + r.users.size() + ")" + (r.unread > 0 ? " *" : ""), null);
            roomRows.addElement(r.name);
        }
        // biggest public rooms first; 150 is plenty for a phone screen
        String[] names = cl.roomNames;
        int[] counts = cl.roomCounts;
        boolean[] used = new boolean[names.length];
        for (int k = 0; k < 150 && k < names.length; k++) {
            int best = -1;
            for (int i = 0; i < names.length; i++)
                if (!used[i] && (best < 0 || counts[i] > counts[best])) best = i;
            if (best < 0) break;
            used[best] = true;
            if (cl.rooms.containsKey(names[best])) continue;
            roomsL.append(names[best] + " (" + counts[best] + ")", null);
            roomRows.addElement(names[best]);
        }
        roomsL.setTitle(names.length == 0 ? "Chat rooms (loading...)" : "Chat rooms");
    }

    private void openRoom(Room r) {
        curRoom = r;
        r.unread = 0;
        roomF = new Form(r.name);
        for (int i = Math.max(0, r.lines.size() - 40); i < r.lines.size(); i++)
            roomF.append(new StringItem(null, r.lines.elementAt(i) + "\n"));
        roomF.addCommand(cSay);
        roomF.addCommand(cUsers);
        roomF.addCommand(cLeave);
        roomF.addCommand(cSearchRoom);
        roomF.addCommand(cBack);
        roomF.setCommandListener(this);
        d.setCurrent(roomF);
    }

    private void showRoomUsers() {
        usersL = new List(curRoom.name + ": " + curRoom.users.size() + " users", List.IMPLICIT);
        for (int i = 0; i < curRoom.users.size(); i++) usersL.append((String) curRoom.users.elementAt(i), null);
        usersL.addCommand(cBack);
        usersL.setCommandListener(this);
        d.setCurrent(usersL);
    }

    // ================================================================ private messages

    void showConvos() {
        convosL = new List("Messages", List.IMPLICIT);
        Vector v = c().convos;
        for (int i = 0; i < v.size(); i++) {
            Convo cv = (Convo) v.elementAt(i);
            convosL.append(cv.user + (cv.unread > 0 ? " (" + cv.unread + " new)" : ""), null);
        }
        convosL.addCommand(cNewPm);
        convosL.addCommand(cBack);
        convosL.setCommandListener(this);
        m.clearTicker();
        d.setCurrent(convosL);
    }

    void openConvo(Convo cv) {
        curConvo = cv;
        cv.unread = 0;
        convoF = new Form(cv.user);
        for (int i = 0; i < cv.lines.size(); i++) convoF.append(new StringItem(null, cv.lines.elementAt(i) + "\n"));
        convoF.addCommand(cSay);
        convoF.addCommand(cProfile);
        convoF.addCommand(cBack);
        convoF.setCommandListener(this);
        d.setCurrent(convoF);
    }

    int unreadPMs() {
        int n = 0;
        Vector v = c().convos;
        for (int i = 0; i < v.size(); i++) n += ((Convo) v.elementAt(i)).unread;
        return n;
    }

    // ================================================================ users

    void findUser() { input("Username", "", 4, m.main()); }

    void showUser(String user, Displayable back) {
        curUser = user;
        userBack = back;
        userL = new List(user, List.IMPLICIT);
        userL.append("View profile", null);
        userL.append("Browse files", null);
        userL.append("Search their files", null);
        userL.append("Send message", null);
        Client cl = c();
        userL.append(cl.isBuddy(user) ? "Remove from buddies" : "Add to buddies", null);
        userL.append(cl.ignored.contains(user) ? "Stop ignoring" : "Ignore (hide chat & messages)", null);
        userL.append(cl.banned.contains(user) ? "Unban" : "Ban (no browsing or downloads)", null);
        userL.addCommand(cBack);
        userL.setCommandListener(this);
        d.setCurrent(userL);
    }

    /** Rebuild the user menu (labels changed) and keep the cursor where it was. */
    private void showUserKeep(int row) {
        Displayable back = userBack;
        showUser(curUser, back);
        if (row < userL.size()) userL.setSelectedIndex(row, true);
    }

    // ================================================================ buddies, ignored, banned

    void showBuddies() {
        Client cl = c();
        buddiesL = new List("Buddies", List.IMPLICIT);
        for (int i = 0; i < cl.buddies.size(); i++) buddiesL.append(buddyLabel((String) cl.buddies.elementAt(i)), null);
        if (cl.buddies.size() == 0) buddiesL.append("(none: Add, or pick Add to buddies on someone)", null);
        buddiesL.addCommand(cAdd);
        buddiesL.addCommand(cRemove);
        buddiesL.addCommand(cSearchBuddies);
        buddiesL.addCommand(cBlocked);
        buddiesL.addCommand(cBack);
        buddiesL.setCommandListener(this);
        d.setCurrent(buddiesL);
    }

    private String buddyLabel(String u) {
        UserInfo i = (UserInfo) c().users.get(u);
        int st = i == null ? -1 : i.status;
        return u + "  " + (st == 2 ? "online" : st == 1 ? "away" : st == 0 ? "offline" : "...");
    }

    int buddiesOnline() {
        Client cl = c();
        int n = 0;
        for (int i = 0; i < cl.buddies.size(); i++) {
            UserInfo u = (UserInfo) cl.users.get(cl.buddies.elementAt(i));
            if (u != null && u.status > 0) n++;
        }
        return n;
    }

    private void showBlocked() {
        Client cl = c();
        blockedL = new List("Ignored & banned", List.IMPLICIT);
        blockedRows.removeAllElements();
        for (int i = 0; i < cl.banned.size(); i++) {
            String u = (String) cl.banned.elementAt(i);
            blockedL.append("[banned] " + u, null);
            blockedRows.addElement(new String[] { "b", u });
        }
        for (int i = 0; i < cl.ignored.size(); i++) {
            String u = (String) cl.ignored.elementAt(i);
            blockedL.append("[ignored] " + u, null);
            blockedRows.addElement(new String[] { "i", u });
        }
        if (blockedRows.size() == 0) blockedL.append("(nobody)", null);
        blockedL.addCommand(cRemove);
        blockedL.addCommand(cBack);
        blockedL.setCommandListener(this);
        d.setCurrent(blockedL);
    }

    void loadPeople(Client cl) {
        Vector v = new Vector();
        SlskMIDlet.loadStrings("slskme-ppl", v);
        for (int i = 0; i < v.size(); i++) {
            String e = (String) v.elementAt(i);
            if (e.length() < 2) continue;
            String u = e.substring(1);
            char k = e.charAt(0);
            if (k == 'f') cl.buddies.addElement(u);
            else if (k == 'i') cl.ignored.addElement(u);
            else if (k == 'b') cl.banned.addElement(u);
        }
    }

    private void savePeople(Client cl) {
        Vector v = new Vector();
        for (int i = 0; i < cl.buddies.size(); i++) v.addElement("f" + cl.buddies.elementAt(i));
        for (int i = 0; i < cl.ignored.size(); i++) v.addElement("i" + cl.ignored.elementAt(i));
        for (int i = 0; i < cl.banned.size(); i++) v.addElement("b" + cl.banned.elementAt(i));
        SlskMIDlet.saveStrings("slskme-ppl", v);
    }

    private static void saveShareCache(Client cl) {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            cl.saveShareCache(new DataOutputStream(bo));
            byte[] b = bo.toByteArray();
            RecordStore rs = RecordStore.openRecordStore("slskme-shares", true);
            if (rs.getNumRecords() > 0) rs.setRecord(1, b, 0, b.length);
            else rs.addRecord(b, 0, b.length);
            rs.closeRecordStore();
        } catch (Throwable e) {}
    }

    void applyLimits(Client cl) {
        cl.upLimit.kbps = parseInt(upLimit, 0);
        cl.downLimit.kbps = parseInt(downLimit, 0);
        cl.maxPerUser = Math.max(1, parseInt(perUser, 50));
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private void showProfile() {
        profileF = new Form(curUser);
        profileF.append("Loading...");
        profileF.addCommand(cBack);
        profileF.setCommandListener(this);
        d.setCurrent(profileF);
        c().requestUserInfo(curUser);
    }

    private void fillProfile(UserInfo i) {
        profileF.deleteAll();
        if (i.picture != null) {
            try {
                Image img = Image.createImage(i.picture, 0, i.picture.length);
                profileF.append(new ImageItem(null, img, ImageItem.LAYOUT_CENTER, "picture"));
            } catch (Throwable e) {}
        }
        profileF.append(new StringItem("Status", i.statusName() + "\n"));
        if (i.files >= 0) profileF.append(new StringItem("Shares", i.files + " files in " + i.dirs + " folders\n"));
        if (i.speed > 0) profileF.append(new StringItem("Speed", Util.size(i.speed) + "/s\n"));
        if (i.gotPeer) {
            profileF.append(new StringItem("Uploads", i.uploads + " done, queue " + i.queue
                    + (i.slots ? ", free slot" : ", no free slot") + "\n"));
        }
        profileF.append(new StringItem("About", (i.description == null ? "(waiting for their client)" : i.description) + "\n"));
    }

    // ================================================================ browse

    private void startBrowse() {
        browsing = curUser;
        curBrowse = null;
        browseL = new List("Browsing " + curUser + "...", List.IMPLICIT);
        browseL.addCommand(cBack);
        browseL.setCommandListener(this);
        d.setCurrent(browseL);
        c().browse(curUser);
    }

    private void fillBrowse(Browse b) {
        curBrowse = b;
        browseL.deleteAll();
        browseL.setTitle(b.user + ": " + b.fileCount + " files");
        for (int i = 0; i < b.dirs.length; i++) {
            String s = b.dirs[i];
            int cut = s.lastIndexOf('\\');
            if (cut > 0) {
                int cut2 = s.lastIndexOf('\\', cut - 1);
                if (cut2 >= 0) s = s.substring(cut2 + 1);
            }
            browseL.append(s.replace('\\', '/') + " (" + b.count(i) + ")", null);
        }
    }

    private void openDir(int i) {
        try {
            curFiles = curBrowse.files(i);
        } catch (Exception e) {
            m.alert("Browse", "Couldn't read that folder", browseL);
            return;
        }
        filesL = new List(Util.baseName(curBrowse.dirs[i]), List.IMPLICIT);
        for (int k = 0; k < curFiles.length; k++) filesL.append(m.resultLabel(curFiles[k]), null);
        filesL.setSelectCommand(cGet);
        filesL.addCommand(cGetAll);
        filesL.addCommand(cInfo);
        filesL.addCommand(cBack);
        filesL.setCommandListener(this);
        d.setCurrent(filesL);
    }

    // ================================================================ wishlist

    void showWishlist() {
        wishL = new List("Wishlist", List.IMPLICIT);
        wishL.append("Results (" + c().wishResults.size() + ")", null);
        Vector w = c().wishlist;
        for (int i = 0; i < w.size(); i++) wishL.append((String) w.elementAt(i), null);
        wishL.addCommand(cAdd);
        wishL.addCommand(cRemove);
        wishL.addCommand(cNow);
        wishL.addCommand(cBack);
        wishL.setCommandListener(this);
        d.setCurrent(wishL);
    }

    private void showWishResults() {
        wishResL = new List("Wishlist results", List.IMPLICIT);
        Vector v = c().wishResults;
        for (int i = 0; i < v.size(); i++) wishResL.append(m.resultLabel((Result) v.elementAt(i)), null);
        wishResL.setSelectCommand(cGet);
        wishResL.addCommand(cInfo);
        wishResL.addCommand(cProfile);
        wishResL.addCommand(cBack);
        wishResL.setCommandListener(this);
        d.setCurrent(wishResL);
    }

    void loadWishlist(Vector into) {
        try {
            RecordStore rs = RecordStore.openRecordStore("slskme-wl", true);
            if (rs.getNumRecords() > 0) {
                DataInputStream in = new DataInputStream(new ByteArrayInputStream(rs.getRecord(1)));
                int n = in.readInt();
                for (int i = 0; i < n; i++) into.addElement(in.readUTF());
            }
            rs.closeRecordStore();
        } catch (Exception e) {}
    }

    private void saveWishlist() {
        try {
            Vector w = c().wishlist;
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bo);
            out.writeInt(w.size());
            for (int i = 0; i < w.size(); i++) out.writeUTF((String) w.elementAt(i));
            byte[] b = bo.toByteArray();
            RecordStore rs = RecordStore.openRecordStore("slskme-wl", true);
            if (rs.getNumRecords() > 0) rs.setRecord(1, b, 0, b.length);
            else rs.addRecord(b, 0, b.length);
            rs.closeRecordStore();
        } catch (Exception e) {}
    }

    // ================================================================ own profile & sharing

    void showMyProfile() {
        myF = new Form("My profile & sharing");
        fDesc = new TextField("Profile text", description, 500, TextField.ANY);
        fStatus = new ChoiceGroup("Status", ChoiceGroup.EXCLUSIVE, new String[] { "Online", "Away" }, null);
        fStatus.setSelectedIndex(c().away ? 1 : 0, true);
        fSharing = new ChoiceGroup("Sharing", ChoiceGroup.EXCLUSIVE, new String[] { "Share files", "Don't share" }, null);
        fSharing.setSelectedIndex(sharing ? 0 : 1, true);
        fShare = new TextField("Shared folder", shareDir, 200, TextField.URL);
        fSlots = new TextField("Upload slots", slots, 1, TextField.NUMERIC);
        fPerUser = new TextField("Max files per person", perUser, 4, TextField.NUMERIC);
        fUpLimit = new TextField("Upload limit KB/s (0 = none)", upLimit, 5, TextField.NUMERIC);
        fDownLimit = new TextField("Download limit KB/s (0 = none)", downLimit, 5, TextField.NUMERIC);
        shareInfo = new StringItem("Now sharing", !sharing ? "not sharing"
            : c().sharedFiles() + " files in " + c().sharedDirs() + " folders");
        myF.append(fDesc);
        myF.append(fStatus);
        myF.append(fSharing);
        myF.append(fShare);
        myF.append(fSlots);
        myF.append(fPerUser);
        myF.append(fUpLimit);
        myF.append(fDownLimit);
        myF.append(shareInfo);
        myF.addCommand(cSave);
        myF.addCommand(cPick);
        myF.addCommand(cRescan);
        myF.addCommand(cBack);
        myF.setCommandListener(this);
        d.setCurrent(myF);
    }

    private void saveMyProfile(boolean rescan) {
        boolean wasSharing = sharing;
        String oldDir = shareDir;
        description = fDesc.getString();
        sharing = fSharing.getSelectedIndex() == 0;
        shareDir = fShare.getString().trim();
        if (shareDir.length() > 0 && !shareDir.endsWith("/")) shareDir += "/";
        slots = fSlots.getString().trim();
        perUser = fPerUser.getString().trim();
        upLimit = fUpLimit.getString().trim();
        downLimit = fDownLimit.getString().trim();
        Client cl = c();
        applyLimits(cl);
        cl.description = description;
        try { cl.uploadSlots = Math.max(1, Math.min(4, Integer.parseInt(slots))); } catch (Exception e) {}
        cl.setAway(fStatus.getSelectedIndex() == 1);
        m.saveAll();
        if (!sharing) {
            if (wasSharing) cl.disableShares();
            shareInfo.setText("not sharing");
        } else if (shareDir.length() == 0) {
            m.alert("Sharing", "Pick a folder to share first", myF);
            return;
        } else if (rescan || !wasSharing || !shareDir.equals(oldDir)) {
            scan(true);
            // stay here so the scan result shows up under "Now sharing"
            m.alert("Sharing", "Saved. Scanning " + Util.decodeUrl(shareDir) + "...", myF);
            return;
        }
        m.alert("Profile", "Saved", m.main());
    }

    /** After login: use the saved scan if it's for this folder, else scan. */
    void startScan() { scan(false); }

    private void scan(final boolean force) {
        final Client cl = c();
        if (!sharing || shareDir.length() == 0 || cl == null) return;
        if (shareInfo != null) shareInfo.setText("scanning...");
        new Thread(new Runnable() {
            public void run() {
                String msg;
                if (!force) {
                    try {
                        byte[] b = null;
                        RecordStore rs = RecordStore.openRecordStore("slskme-shares", true);
                        if (rs.getNumRecords() > 0) b = rs.getRecord(1);
                        rs.closeRecordStore();
                        int n = b == null ? -1 : cl.loadShareCache(new DataInputStream(new ByteArrayInputStream(b)), shareDir);
                        if (n >= 0) {
                            msg = "Sharing " + n + " files (saved scan; Rescan to refresh)";
                            m.log(msg);
                            final String fm = msg;
                            d.callSerially(new Runnable() {
                                public void run() { if (shareInfo != null) shareInfo.setText(fm); }
                            });
                            return;
                        }
                    } catch (Throwable e) {}
                }
                try {
                    int n = cl.scanShares(shareDir);
                    msg = n == 0 ? "Nothing to share: no readable files in " + Util.decodeUrl(shareDir)
                        : "Sharing " + n + (n == 1 ? " file in " : " files in ") + cl.sharedDirs() + (cl.sharedDirs() == 1 ? " folder" : " folders");
                    String p = cl.scanProblems();
                    if (p != null) msg += "\n" + p;
                    saveShareCache(cl);
                } catch (Throwable e) {
                    msg = "Share scan failed: " + e.getMessage();
                }
                m.log(msg);
                final String fm = msg;
                d.callSerially(new Runnable() {
                    public void run() { if (shareInfo != null) shareInfo.setText(fm); }
                });
            }
        }).start();
    }

    // ================================================================ uploads

    void showUploads() {
        if (upL == null) {
            upL = new List("Uploads", List.IMPLICIT);
            upL.setSelectCommand(cInfo);
            upL.addCommand(cRemove);
            upL.addCommand(cClear);
            upL.addCommand(cBack);
            upL.setCommandListener(this);
        }
        refreshUploads();
        d.setCurrent(upL);
    }

    void refreshUploads() {
        if (upL == null) return;
        Vector v = c().uploads;
        int n = v.size();
        if (n != upRows.size()) {
            upL.deleteAll();
            upRows.removeAllElements();
            for (int i = 0; i < n; i++) {
                Upload u = (Upload) v.elementAt(i);
                upRows.addElement(u);
                upL.append(u.label(), null);
            }
        } else {
            for (int i = 0; i < n; i++) {
                String l = ((Upload) upRows.elementAt(i)).label();
                if (!l.equals(upL.getString(i))) upL.set(i, l, null);
            }
        }
        upL.setTitle(c().sharedFiles() == 0 ? "Uploads (not sharing)" : "Uploads");
    }

    boolean showingUploads() { return upL != null && d.getCurrent() == upL; }

    int activeUploads() {
        int n = 0;
        Vector v = c().uploads;
        for (int i = 0; i < v.size(); i++) if (!((Upload) v.elementAt(i)).finished()) n++;
        return n;
    }

    // ================================================================ events from the network

    void onEvent(int type, Object a, Object b) {
        Displayable cur = d.getCurrent();
        switch (type) {
        case Client.EV_ROOMLIST:
            if (roomsL != null && cur == roomsL) fillRooms();
            break;
        case Client.EV_ROOM: {
            Room r = (Room) a;
            if (r != null && b == null && cur == inputTB && inputMode == 6) openRoom(r);
            else if (r != null && b == null && cur == roomsL && roomsL != null) { fillRooms(); openRoom(r); }
            else if (cur == roomsL) fillRooms();
            if (r != null && r == curRoom && "left".equals(b) && cur == roomF) showRooms();
            break;
        }
        case Client.EV_ROOMMSG: {
            Room r = (Room) a;
            if (r == curRoom && cur == roomF) {
                r.unread = 0;
                roomF.append(new StringItem(null, b + "\n"));
                while (roomF.size() > 40) roomF.delete(0);
            }
            break;
        }
        case Client.EV_PM: {
            Convo cv = (Convo) a;
            if (cv == curConvo && cur == convoF) {
                cv.unread = 0;
                convoF.append(new StringItem(null, cv.lines.lastElement() + "\n"));
            } else {
                m.notifyUser("Message from " + cv.user + ": " + b);
                if (cur == convosL) showConvos();
            }
            break;
        }
        case Client.EV_USERINFO: {
            UserInfo i = (UserInfo) a;
            if (profileF != null && cur == profileF && i.user.equals(curUser)) fillProfile(i);
            if (cur == buddiesL && buddiesL != null) {
                int k = c().buddies.indexOf(i.user);
                if (k >= 0 && k < buddiesL.size()) buddiesL.set(k, buddyLabel(i.user), null);
            }
            break;
        }
        case Client.EV_BUDDY: {
            UserInfo i = (UserInfo) a;
            m.notifyUser(i.user + (i.status == 0 ? " went offline" : " is online"));
            break;
        }
        case Client.EV_BROWSE:
            if (browseL == null || !a.equals(browsing)) break;
            if (b instanceof Browse) fillBrowse((Browse) b);
            else if (cur == browseL) m.alert("Browse", (String) b, userL != null ? (Displayable) userL : m.main());
            break;
        case Client.EV_WISH:
            m.notifyUser("Wishlist hit for \"" + b + "\": " + Util.baseName(((Result) a).file));
            break;
        case Client.EV_SHARES:
            // a finished download got added to the shares; keep the saved scan current (off the UI thread)
            if (b == null) {
                final Client cl = c();
                new Thread(new Runnable() { public void run() { saveShareCache(cl); } }).start();
            }
            break;
        case Client.EV_UPLOAD:
            if (showingUploads()) refreshUploads();
            break;
        default:
            break;
        }
    }

    // ================================================================ commands

    public void commandAction(Command c, Displayable s) {
        Client cl = c();
        if (cl == null) { m.toLogin(); return; }

        if (s == inputTB) {
            if (c == cBack) { d.setCurrent(inputBack != null ? inputBack : m.main()); return; }
            String t = inputTB.getString().trim();
            if (t.length() == 0) return;
            switch (inputMode) {
            case 1: cl.say(curRoom.name, t); d.setCurrent(roomF); break;
            case 2:
                cl.sendPM(curConvo.user, t);
                openConvo(curConvo);
                break;
            case 3: openConvo(cl.convo(t)); break;
            case 4: showUser(t, m.main()); break;
            case 5: cl.userSearch(curUser, t); m.showResults("Search " + curUser + ": " + t); break;
            case 6:
                cl.joinRoom(t);
                inputTB.setTitle("Joining " + t + "...");
                break;
            case 8: cl.addBuddy(t); savePeople(cl); showBuddies(); break;
            case 9: cl.roomSearch(curRoom.name, t); m.showResults("Room " + curRoom.name + ": " + t); break;
            case 10: cl.buddySearch(t); m.showResults("Buddies: " + t); break;
            case 7:
                if (!cl.wishlist.contains(t)) cl.wishlist.addElement(t);
                saveWishlist();
                cl.wishSearch(t);
                showWishlist();
                break;
            }
            return;
        }

        if (s == roomsL) {
            if (c == cBack) m.showMain();
            else if (c == cJoinName) input("Join room", "", 6, roomsL);
            else if (c == cRefresh) { cl.requestRoomList(); fillRooms(); }
            else if (c == List.SELECT_COMMAND) {
                int i = roomsL.getSelectedIndex();
                if (i < 0 || i >= roomRows.size()) return;
                String name = (String) roomRows.elementAt(i);
                Room r = (Room) cl.rooms.get(name);
                if (r != null) openRoom(r);
                else { cl.joinRoom(name); roomsL.setTitle("Joining " + name + "..."); }
            }
        } else if (s == roomF) {
            if (c == cBack) showRooms();
            else if (c == cSay) input("Say in " + curRoom.name, "", 1, roomF);
            else if (c == cUsers) showRoomUsers();
            else if (c == cLeave) { cl.leaveRoom(curRoom.name); cl.rooms.remove(curRoom.name); showRooms(); }
            else if (c == cSearchRoom) input("Search " + curRoom.name, "", 9, roomF);
        } else if (s == usersL) {
            if (c == cBack) d.setCurrent(roomF);
            else if (c == List.SELECT_COMMAND && usersL.getSelectedIndex() >= 0)
                showUser(usersL.getString(usersL.getSelectedIndex()), usersL);
        } else if (s == buddiesL) {
            int i = buddiesL.getSelectedIndex();
            String u = i >= 0 && i < cl.buddies.size() ? (String) cl.buddies.elementAt(i) : null;
            if (c == cBack) m.showMain();
            else if (c == cAdd) input("Add buddy (username)", "", 8, buddiesL);
            else if (c == cRemove && u != null) { cl.removeBuddy(u); savePeople(cl); showBuddies(); }
            else if (c == cSearchBuddies) {
                if (cl.buddies.size() == 0) m.alert("Buddies", "Add some buddies first", buddiesL);
                else input("Search buddies' files", "", 10, buddiesL);
            } else if (c == cBlocked) showBlocked();
            else if (c == List.SELECT_COMMAND && u != null) showUser(u, buddiesL);
        } else if (s == blockedL) {
            int i = blockedL.getSelectedIndex();
            String[] row = i >= 0 && i < blockedRows.size() ? (String[]) blockedRows.elementAt(i) : null;
            if (c == cBack) showBuddies();
            else if (row != null && c == cRemove) {
                if (row[0].equals("b")) cl.setBanned(row[1], false); else cl.setIgnored(row[1], false);
                savePeople(cl);
                showBlocked();
            }
        } else if (s == convosL) {
            if (c == cBack) m.showMain();
            else if (c == cNewPm) input("Message to (username)", "", 3, convosL);
            else if (c == List.SELECT_COMMAND) {
                int i = convosL.getSelectedIndex();
                if (i >= 0 && i < cl.convos.size()) openConvo((Convo) cl.convos.elementAt(i));
            }
        } else if (s == convoF) {
            if (c == cBack) showConvos();
            else if (c == cSay) input("To " + curConvo.user, "", 2, convoF);
            else if (c == cProfile) showUser(curConvo.user, convoF);
        } else if (s == userL) {
            if (c == cBack) d.setCurrent(userBack != null ? userBack : m.main());
            else if (c == List.SELECT_COMMAND) {
                switch (userL.getSelectedIndex()) {
                case 0: showProfile(); break;
                case 1: startBrowse(); break;
                case 2: input("Search " + curUser + "'s files", "", 5, userL); break;
                case 3: openConvo(cl.convo(curUser)); break;
                case 4:
                    if (cl.isBuddy(curUser)) cl.removeBuddy(curUser); else cl.addBuddy(curUser);
                    savePeople(cl);
                    m.alert("Buddies", cl.isBuddy(curUser) ? curUser + " added to buddies" : curUser + " removed from buddies", userL);
                    showUserKeep(4);
                    break;
                case 5:
                    cl.setIgnored(curUser, !cl.ignored.contains(curUser));
                    savePeople(cl);
                    showUserKeep(5);
                    break;
                case 6:
                    cl.setBanned(curUser, !cl.banned.contains(curUser));
                    savePeople(cl);
                    showUserKeep(6);
                    break;
                }
            }
        } else if (s == profileF) {
            d.setCurrent(userL);
        } else if (s == browseL) {
            if (c == cBack) d.setCurrent(userL);
            else if (c == List.SELECT_COMMAND && curBrowse != null && browseL.getSelectedIndex() >= 0)
                openDir(browseL.getSelectedIndex());
        } else if (s == filesL || s == wishResL) {
            Result[] arr = s == filesL ? curFiles : null;
            int i = ((List) s).getSelectedIndex();
            Result r = null;
            if (i >= 0) r = arr != null ? (i < arr.length ? arr[i] : null)
                    : (i < cl.wishResults.size() ? (Result) cl.wishResults.elementAt(i) : null);
            if (c == cBack) { if (s == filesL) d.setCurrent(browseL); else showWishlist(); }
            else if (c == cGet && r != null) {
                cl.download(r);
                ((List) s).set(i, m.resultLabel(r), null);
            } else if (c == cGetAll && arr != null) {
                String sub = Util.baseName(filesL.getTitle());
                for (int k = 0; k < arr.length; k++) cl.downloadInto(arr[k], sub);
                for (int k = 0; k < arr.length; k++) filesL.set(k, m.resultLabel(arr[k]), null);
                m.alert("Download whole folder", "Queued " + arr.length + " files", filesL);
            } else if (c == cInfo && r != null) m.showInfo("File", r.details(), s);
            else if (c == cProfile && r != null) showUser(r.user, s);
        } else if (s == wishL) {
            int i = wishL.getSelectedIndex();
            String q = i >= 1 && i - 1 < cl.wishlist.size() ? (String) cl.wishlist.elementAt(i - 1) : null;
            if (c == cBack) m.showMain();
            else if (c == cAdd) input("Add to wishlist", "", 7, wishL);
            else if (c == cRemove && q != null) { cl.wishlist.removeElement(q); saveWishlist(); showWishlist(); }
            else if (c == cNow && q != null) { cl.wishSearch(q); m.alert("Wishlist", "Searching for " + q, wishL); }
            else if (c == List.SELECT_COMMAND && i == 0) showWishResults();
        } else if (s == myF && c == cPick) {
            new FolderPicker(d, "Pick folder to share", new FolderPicker.Done() {
                public void picked(String url) {
                    if (url != null) {
                        fShare.setString(url);
                        fSharing.setSelectedIndex(0, true); // picking a folder to share means sharing it
                        shareInfo.setText("Picked. Save or Rescan to share it.");
                    }
                    d.setCurrent(myF);
                }
            }).show(fShare.getString().trim().length() > 0 ? fShare.getString().trim() : null);
        } else if (s == myF) {
            if (c == cBack) m.showMain();
            else if (c == cSave) saveMyProfile(false);
            else if (c == cRescan) {
                fSharing.setSelectedIndex(0, true); // rescanning only makes sense while sharing
                saveMyProfile(true);
            }
        } else if (s == upL) {
            int i = upL.getSelectedIndex();
            Upload u = i >= 0 && i < upRows.size() ? (Upload) upRows.elementAt(i) : null;
            if (c == cBack) m.showMain();
            else if (c == cInfo && u != null) m.showInfo("Upload", u.details(), upL);
            else if (c == cRemove && u != null) { cl.removeUpload(u); refreshUploads(); }
            else if (c == cClear) {
                for (int k = cl.uploads.size() - 1; k >= 0; k--)
                    if (((Upload) cl.uploads.elementAt(k)).finished()) cl.uploads.removeElementAt(k);
                refreshUploads();
            }
        }
    }

    void reset() {
        roomsL = null; roomF = null; curRoom = null; convosL = null; convoF = null; curConvo = null;
        userL = null; profileF = null; browseL = null; filesL = null; curBrowse = null;
        wishL = null; wishResL = null; myF = null; shareInfo = null; upL = null; upRows.removeAllElements();
    }

    static Ticker ticker(String s) { return new Ticker(s); }
}
