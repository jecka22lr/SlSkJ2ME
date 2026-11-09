package slsk;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Random;
import java.util.Vector;
import javax.microedition.io.Connector;
import javax.microedition.io.ServerSocketConnection;
import javax.microedition.io.SocketConnection;
import javax.microedition.io.StreamConnection;

/** Soulseek protocol core. UI-agnostic: talks to a Listener. */
public final class Client implements Runnable {
    public interface Listener {
        void loggedIn(boolean ok, String msg);
        void disconnected(String why);
        void result(Result r);
        void transferChanged(Transfer t);
        void log(String s);
        /** Everything else (chat, PMs, profiles, browse, wishlist, shares, uploads). */
        void event(int type, Object a, Object b);
    }

    public static final int EV_ROOMLIST = 1, EV_ROOM = 2, EV_ROOMMSG = 3, EV_PM = 4, EV_USERINFO = 5,
        EV_BROWSE = 6, EV_WISH = 7, EV_SHARES = 8, EV_UPLOAD = 9, EV_CONN = 10;

    static final int VERSION = 160, MINOR = 1;

    final String username, password, host;
    final int port, listenPort;
    volatile String downloadDir;
    int maxSearchBytes = 192 * 1024;
    int maxBrowseBytes = 1536 * 1024;
    int maxResults = 2000;
    /** Max results kept per user, so a few big lossless folders can't fill the whole list. */
    int perUserCap = 25;
    private final Hashtable perUser = new Hashtable();
    private volatile boolean capPerUser = true;
    int maxPeers = 10;

    // profile & sharing settings (set by the UI before/after connect)
    public volatile String description = "";
    public volatile boolean away;
    public int uploadSlots = 1;
    final Shares shares = new Shares();
    private int uploadedTotal;

    private final Listener ui;
    private StreamConnection ssc;
    private DataInputStream sin;
    private OutputStream sout;
    private volatile boolean online, stopping;
    private ServerSocketConnection listener;
    private boolean listening;

    private final Random rnd = new Random();
    private volatile int searchToken;
    public final Vector results = new Vector();
    public final Vector transfers = new Vector();
    public final Vector uploads = new Vector();
    private final Hashtable peers = new Hashtable();   // user -> PeerConn ("P")
    private final Hashtable pending = new Hashtable(); // Integer token -> Pending
    private final Hashtable addrs = new Hashtable();   // user -> int[]{ip, port, state}
    private int peerCount;

    // chat
    public final Hashtable rooms = new Hashtable();    // joined: name -> Room
    public String[] roomNames = new String[0];
    public int[] roomCounts = new int[0];
    public final Vector convos = new Vector();
    public final Hashtable users = new Hashtable();    // name -> UserInfo

    // wishlist
    public final Vector wishlist = new Vector();       // String queries
    public final Vector wishResults = new Vector();
    private final Hashtable wishTokens = new Hashtable();
    private volatile int wishInterval;
    private int wishIndex;

    // distributed network (how other people's searches reach us)
    private volatile PeerConn parent;
    private volatile boolean findingParent;
    private final Vector searchJobs = new Vector();
    private boolean searchWorker;

    static final class Pending {
        String user, type;
        Upload upload;
        PeerConn conn;
        boolean failed;
    }

    public Client(Listener ui, String user, String pass, String host, int port, int listenPort, String dir) {
        this.ui = ui; this.username = user; this.password = pass; this.host = host;
        this.port = port; this.listenPort = listenPort; this.downloadDir = dir;
    }

    // ================================================================ server

    public void connect() { new Thread(this).start(); }

    private volatile boolean everOnline;
    private volatile int gen; // bumps on every successful login; old helper threads exit

    /** Connects, and after the first successful login keeps reconnecting until logout. */
    public void run() {
        int delay = 5;
        while (true) {
            String why = session();
            boolean was = online;
            shutdown();
            if (stopping) return;
            if (!everOnline) {
                ui.loggedIn(false, "Can't connect: " + why);
                return;
            }
            if (was) delay = 5;
            ui.event(EV_CONN, "Connection lost (" + why + "), reconnecting in " + delay + " s", null);
            for (int i = 0; i < delay * 10 && !stopping; i++) {
                try { Thread.sleep(100); } catch (InterruptedException e) {}
            }
            if (stopping) return;
            delay = Math.min(delay * 2, 120);
        }
    }

    private String session() {
        try {
            try {
                ssc = (StreamConnection) Connector.open("socket://" + host + ":" + port, Connector.READ_WRITE, true);
            } catch (SecurityException e) {
                throw new IOException("phone denied network permission");
            }
            if (stopping) return "cancelled";
            try { ((SocketConnection) ssc).setSocketOption(SocketConnection.KEEPALIVE, 1); } catch (Throwable e) {}
            sin = ssc.openDataInputStream();
            sout = ssc.openOutputStream();
            sendServer(1, new Msg().str(username).str(password).u32(VERSION)
                    .str(MD5.hex(username + password)).u32(MINOR));
            while (!stopping) {
                int len = Util.readU32(sin);
                if (len < 4) throw new IOException("bad frame");
                int code = Util.readU32(sin);
                int plen = len - 4;
                if (plen > 1024 * 1024) { Util.skip(sin, plen); continue; }
                byte[] b = new byte[plen];
                Util.readFully(sin, b, 0, plen);
                try {
                    onServer(code, new Rd(b, 0, plen));
                } catch (IOException e) {
                    log("Bad server msg " + code);
                } catch (OutOfMemoryError e) {
                    log("Out of memory on server msg " + code);
                }
            }
            return "logged out";
        } catch (Throwable e) {
            return e.getMessage() == null ? e.toString() : e.getMessage();
        }
    }

    private synchronized void sendServer(int code, Msg m) throws IOException {
        if (sout == null) throw new IOException("not connected");
        sout.write(m.frame(code));
        sout.flush();
    }

    private void trySend(int code, Msg m) {
        try { sendServer(code, m); } catch (IOException e) { log("Send failed: " + e.getMessage()); }
    }

    private UserInfo info(String user) {
        UserInfo u = (UserInfo) users.get(user);
        if (u == null) { u = new UserInfo(user); users.put(user, u); }
        return u;
    }

    private void onServer(int code, Rd r) throws IOException {
        switch (code) {
        case 1: { // Login
            boolean ok = r.bool();
            if (!ok) {
                String reason = r.str();
                if (!stopping) ui.loggedIn(false, loginReason(reason));
                stopping = true;
                return;
            }
            String greet = r.str();
            if (stopping) return;
            online = true;
            afterLogin();
            if (!everOnline) {
                everOnline = true;
                ui.loggedIn(true, greet);
            } else {
                afterReconnect();
                ui.event(EV_CONN, "Reconnected", null);
            }
            break;
        }
        case 3: { // GetPeerAddress
            String u = r.str();
            int ip = r.u32(), p = r.u32();
            int[] a = (int[]) addrs.get(u);
            if (a != null) synchronized (a) { a[0] = ip; a[1] = p; a[2] = 1; a.notifyAll(); }
            break;
        }
        case 5: { // WatchUser
            String u = r.str();
            UserInfo i = info(u);
            if (r.bool()) {
                i.status = r.u32(); i.speed = r.u32(); r.u64(); i.files = r.u32(); i.dirs = r.u32();
            } else i.status = 0;
            ui.event(EV_USERINFO, i, null);
            break;
        }
        case 7: { // GetUserStatus
            String u = r.str();
            info(u).status = r.u32();
            ui.event(EV_USERINFO, info(u), null);
            break;
        }
        case 13: { // SayChatroom
            String room = r.str(), u = r.str(), msg = r.str();
            Room rm = (Room) rooms.get(room);
            if (rm != null) {
                String line = u + ": " + msg;
                rm.add(line);
                ui.event(EV_ROOMMSG, rm, line);
            }
            break;
        }
        case 14: { // JoinRoom
            String room = r.str();
            Room rm = (Room) rooms.get(room);
            if (rm == null) rm = new Room(room);
            rm.users.removeAllElements();
            int n = r.u32();
            for (int i = 0; i < n; i++) rm.users.addElement(r.str());
            rooms.put(room, rm);
            ui.event(EV_ROOM, rm, null);
            break;
        }
        case 15: { // LeaveRoom
            Room rm = (Room) rooms.remove(r.str());
            ui.event(EV_ROOM, rm, "left");
            break;
        }
        case 16: { // UserJoinedRoom
            Room rm = (Room) rooms.get(r.str());
            String u = r.str();
            if (rm != null && !rm.users.contains(u)) { rm.users.addElement(u); ui.event(EV_ROOM, rm, null); }
            break;
        }
        case 17: { // UserLeftRoom
            Room rm = (Room) rooms.get(r.str());
            if (rm != null) { rm.users.removeElement(r.str()); ui.event(EV_ROOM, rm, null); }
            break;
        }
        case 18: { // ConnectToPeer: someone wants us to connect to them
            String u = r.str(), type = r.str();
            int ip = r.u32(), p = r.u32(), tok = r.u32();
            if (!"P".equals(type) && !"F".equals(type)) break;
            // searches can fill the peer slots; keep some headroom so browse/profile requests still get through
            if ("P".equals(type) && peerCount >= maxPeers + 4 && !hasPending(u) && !hasUpload(u)) {
                log("Too many connections, ignored " + u + "'s request (they'll see 'cannot connect')");
                trySend(1001, new Msg().u32(tok).str(u));
                break;
            }
            if ("F".equals(type)) fileConnStep(u, "Uploader asked us to connect (F)");
            PeerConn.pierce(this, u, type, Util.ip(ip), p, tok).start();
            break;
        }
        case 22: { // MessageUser
            int id = r.u32();
            r.u32(); // timestamp
            String u = r.str(), msg = r.str();
            trySend(23, new Msg().u32(id)); // MessageAcked
            Convo c = convo(u);
            c.add(u + ": " + msg, true);
            ui.event(EV_PM, c, msg);
            break;
        }
        case 26: { // FileSearch forwarded by the server (user search aimed at us)
            String u = r.str();
            int tok = r.u32();
            searchRequest(u, tok, r.str(), null);
            break;
        }
        case 41: // Relogged
            stopping = true;
            shutdown();
            ui.disconnected("Logged in from another place");
            break;
        case 64: { // RoomList
            int n = r.u32();
            String[] names = new String[n];
            for (int i = 0; i < n; i++) names[i] = r.str();
            int m = r.u32();
            int[] counts = new int[n];
            for (int i = 0; i < m && i < n; i++) counts[i] = r.u32();
            roomNames = names;
            roomCounts = counts;
            ui.event(EV_ROOMLIST, null, null);
            break;
        }
        case 93: { // EmbeddedMessage (distributed message relayed by the server)
            int dc = r.u8();
            onDistrib(dc, r);
            break;
        }
        case 102: { // PossibleParents
            int n = r.u32();
            final Vector cands = new Vector();
            for (int i = 0; i < n; i++) {
                String u = r.str();
                int ip = r.u32(), p = r.u32();
                cands.addElement(new Object[] { u, Util.ip(ip), new Integer(p) });
            }
            findParent(cands);
            break;
        }
        case 104: // WishlistInterval
            startWishlist(r.u32());
            break;
        case 1001: { // CantConnectToPeer
            int tok = r.u32();
            Pending pd = (Pending) pending.get(new Integer(tok));
            if (pd != null) synchronized (pd) { pd.failed = true; pd.notifyAll(); }
            break;
        }
        default:
            break;
        }
    }

    private static String loginReason(String r) {
        if ("INVALIDPASS".equals(r)) return "Wrong password";
        if ("INVALIDUSERNAME".equals(r)) return "Invalid username";
        if ("INVALIDVERSION".equals(r)) return "Server rejected client version";
        if ("SVRFULL".equals(r)) return "Server full";
        return "Login failed: " + r;
    }

    /** After a reconnect: rejoin rooms and re-send queued download requests. */
    private void afterReconnect() {
        Enumeration e = rooms.keys();
        while (e.hasMoreElements()) joinRoom((String) e.nextElement());
        Vector v = new Vector();
        synchronized (transfers) {
            for (int i = 0; i < transfers.size(); i++) v.addElement(transfers.elementAt(i));
        }
        for (int i = 0; i < v.size(); i++) {
            Transfer t = (Transfer) v.elementAt(i);
            if (t.state == Transfer.QUEUED || t.state == Transfer.CONNECTING) {
                t.state = Transfer.FAILED; // let queue() run again
                queue(t);
            }
        }
    }

    private void afterLogin() {
        final int g = ++gen;
        wishInterval = 0;
        if (listenPort > 0) {
            try {
                listener = (ServerSocketConnection) Connector.open("socket://:" + listenPort);
                listening = true;
                new Thread(new Runnable() {
                    public void run() { acceptLoop(); }
                }).start();
                trySend(2, new Msg().u32(listenPort)); // SetWaitPort
                log("Listening on port " + listenPort);
            } catch (Throwable e) {
                log("Can't listen (" + e.getMessage() + "), using firewall piercing only");
            }
        }
        trySend(28, new Msg().u32(away ? 1 : 2));                  // SetStatus
        sendShareCounts();
        trySend(71, new Msg().bool(true));                          // HaveNoParent
        trySend(100, new Msg().bool(false));                        // AcceptChildren: no
        new Thread(new Runnable() {
            public void run() {
                while (online && !stopping && g == gen) {
                    try { Thread.sleep(90000); } catch (InterruptedException e) {}
                    if (online && g == gen) trySend(32, new Msg()); // ServerPing keeps NAT alive
                }
            }
        }).start();
        new Thread(new Runnable() {
            public void run() {
                while (online && !stopping && g == gen) {
                    try { Thread.sleep(10000); } catch (InterruptedException e) {}
                    reapIdlePeers();
                    checkUploads();
                }
            }
        }).start();
    }

    private void acceptLoop() {
        while (online && listener != null) {
            try {
                StreamConnection s = listener.acceptAndOpen();
                PeerConn.incoming(this, s).start();
            } catch (Throwable e) {
                break;
            }
        }
    }

    public void logout() {
        stopping = true;
        shutdown();
    }

    private void shutdown() {
        online = false;
        try { if (listener != null) listener.close(); } catch (Throwable e) {}
        listener = null;
        try { if (sin != null) sin.close(); } catch (Throwable e) {}
        try { if (sout != null) sout.close(); } catch (Throwable e) {}
        try { if (ssc != null) ssc.close(); } catch (Throwable e) {}
        sout = null;
        if (parent != null) parent.close();
        Vector v = new Vector();
        synchronized (peers) {
            Enumeration e = peers.elements();
            while (e.hasMoreElements()) v.addElement(e.nextElement());
        }
        for (int i = 0; i < v.size(); i++) ((PeerConn) v.elementAt(i)).close();
    }

    public boolean isOnline() { return online; }

    public void setDownloadDir(String d) { downloadDir = d; }

    public void setAway(boolean a) {
        away = a;
        if (online) trySend(28, new Msg().u32(a ? 1 : 2));
    }

    // ================================================================ search

    public void search(String query) {
        searchToken = rnd.nextInt() & 0x7fffffff;
        results.removeAllElements();
        perUser.clear();
        capPerUser = true;
        trySend(26, new Msg().u32(searchToken).str(query));
        log("Searching: " + query);
    }

    /** Search only one user's shares. Results land in the normal results list. */
    public void userSearch(String user, String query) {
        searchToken = rnd.nextInt() & 0x7fffffff;
        results.removeAllElements();
        perUser.clear();
        capPerUser = false; // one user's files: no point capping
        trySend(42, new Msg().str(user).u32(searchToken).str(query));
        log("Searching " + user + "'s files: " + query);
    }

    private void onSearchResponse(PeerConn p, Rd r0) throws IOException {
        byte[] raw = Inflate.zlib(r0.b, r0.p, r0.left(), 2 * 1024 * 1024);
        Rd r = new Rd(raw, 0, raw.length);
        String user = r.str();
        int tok = r.u32();
        boolean wish = tok != searchToken;
        if (wish && !wishTokens.containsKey(new Integer(tok))) return;
        Vector dest = wish ? wishResults : results;
        int cap = wish ? 200 : maxResults;
        if (dest.size() >= cap) return;
        // stop collecting before the phone runs out of heap
        if (dest.size() > 150 && Runtime.getRuntime().freeMemory() < 350 * 1024) {
            System.gc();
            if (Runtime.getRuntime().freeMemory() < 350 * 1024) return;
        }
        int n = r.u32();
        Result[] got = new Result[Math.min(n, 2000)];
        int k = 0;
        for (int i = 0; i < n; i++) {
            r.u8();
            String file = r.str();
            long size = r.u64();
            r.skipStr();
            int na = r.u32();
            int br = 0, dur = 0;
            for (int j = 0; j < na; j++) {
                int ac = r.u32(), av = r.u32();
                if (ac == 0) br = av;
                else if (ac == 1) dur = av;
            }
            if (k < got.length) {
                Result x = new Result();
                x.user = user; x.file = file; x.size = size; x.bitrate = br; x.duration = dur;
                got[k++] = x;
            }
        }
        boolean slot = r.left() > 0 && r.bool();
        int speed = r.left() >= 4 ? r.u32() : 0;
        int queue = r.left() >= 4 ? r.u32() : 0;
        int already = 0;
        if (!wish && capPerUser) {
            Integer prev = (Integer) perUser.get(user);
            already = prev == null ? 0 : prev.intValue();
            k = Math.max(0, Math.min(k, perUserCap - already));
            perUser.put(user, new Integer(already + k));
        }
        for (int i = 0; i < k; i++) {
            if (dest.size() >= cap) break;
            got[i].slot = slot; got[i].speed = speed; got[i].queue = queue;
            dest.addElement(got[i]);
            if (wish) ui.event(EV_WISH, got[i], wishTokens.get(new Integer(tok)));
            else ui.result(got[i]);
        }
    }

    // ================================================================ wishlist

    private void startWishlist(int secs) {
        boolean first = wishInterval == 0;
        wishInterval = Math.max(secs, 60);
        if (!first) return;
        final int g = gen;
        new Thread(new Runnable() {
            public void run() {
                while (online && !stopping && g == gen) {
                    wishSearchNext();
                    try { Thread.sleep(wishInterval * 1000L); } catch (InterruptedException e) {}
                }
            }
        }).start();
    }

    private synchronized void wishSearchNext() {
        if (wishlist.size() == 0) return;
        if (wishIndex >= wishlist.size()) wishIndex = 0;
        String q = (String) wishlist.elementAt(wishIndex++);
        wishSearch(q);
    }

    /** Run one wishlist query now. */
    public void wishSearch(String q) {
        int tok = rnd.nextInt() & 0x7fffffff;
        wishTokens.put(new Integer(tok), q);
        trySend(103, new Msg().u32(tok).str(q));
    }

    // ================================================================ chat & users

    public void requestRoomList() { trySend(64, new Msg()); }

    public void joinRoom(String room) { trySend(14, new Msg().str(room).u32(0)); }

    public void leaveRoom(String room) { trySend(15, new Msg().str(room)); }

    public void say(String room, String msg) { trySend(13, new Msg().str(room).str(msg)); }

    public Convo convo(String user) {
        synchronized (convos) {
            for (int i = 0; i < convos.size(); i++) {
                Convo c = (Convo) convos.elementAt(i);
                if (c.user.equals(user)) return c;
            }
            Convo c = new Convo(user);
            convos.addElement(c);
            return c;
        }
    }

    public void sendPM(String user, String msg) {
        trySend(22, new Msg().str(user).str(msg));
        convo(user).add(username + ": " + msg, false);
    }

    /** Server status + the user's own profile from their client. Results arrive as EV_USERINFO. */
    public void requestUserInfo(final String user) {
        trySend(5, new Msg().str(user)); // WatchUser
        new Thread(new Runnable() {
            public void run() {
                try { ensurePeer(user).send(new Msg().frame(15)); }
                catch (IOException e) {
                    UserInfo i = info(user);
                    if (i.description == null) i.description = "(Couldn't reach their client: " + e.getMessage() + ")";
                    ui.event(EV_USERINFO, i, null);
                }
            }
        }).start();
    }

    public void browse(final String user) {
        new Thread(new Runnable() {
            public void run() {
                try { ensurePeer(user).send(new Msg().frame(4)); }
                catch (IOException e) { ui.event(EV_BROWSE, user, "Can't reach " + user + ": " + e.getMessage()); }
            }
        }).start();
    }

    // ================================================================ sharing

    /** Scan the share folder (blocking) and tell the server how much we share. */
    public int scanShares(String dir) throws IOException {
        shares.wanted = true;
        int n = shares.scan(dir);
        shares.enabled = n > 0;
        sendShareCounts();
        ui.event(EV_SHARES, new Integer(n), null);
        return n;
    }

    /** Skipped files and first reason from the last scan, or null if none were skipped. */
    public String scanProblems() {
        return shares.skipped == 0 ? null : shares.skipped + " skipped (" + shares.firstError + ")";
    }

    void downloadSaved(String url, long size) {
        if (shares.wanted && shares.add(url, size)) {
            shares.enabled = true;
            sendShareCounts();
            ui.event(EV_SHARES, new Integer(shares.files()), null);
        }
    }

    public void disableShares() {
        shares.wanted = false;
        shares.enabled = false;
        sendShareCounts();
    }

    public int sharedFiles() { return shares.enabled ? shares.files() : 0; }

    public int sharedDirs() { return shares.enabled ? shares.dirs() : 0; }

    private void sendShareCounts() {
        if (online) trySend(35, new Msg().u32(sharedDirs()).u32(sharedFiles()));
    }

    /** Someone searched (distributed network or user search); answer if we have matches. */
    private void searchRequest(String user, int token, String query, PeerConn direct) {
        if (!shares.enabled || user.equals(username)) return;
        Vector hits = shares.match(query, 50);
        if (hits.size() == 0) return;
        final byte[] payload = Shares.searchResponse(username, token, hits, freeSlot(), 0, queuedUploads());
        if (direct != null) {
            try { direct.send(new Msg().raw(payload).frame(9)); } catch (IOException e) {}
            return;
        }
        synchronized (searchJobs) {
            if (searchJobs.size() >= 6) return; // phone can't keep up; drop
            searchJobs.addElement(new Object[] { user, payload });
            if (searchWorker) return;
            searchWorker = true;
        }
        new Thread(new Runnable() {
            public void run() {
                while (true) {
                    Object[] job;
                    synchronized (searchJobs) {
                        if (searchJobs.size() == 0 || !online) { searchWorker = false; return; }
                        job = (Object[]) searchJobs.elementAt(0);
                        searchJobs.removeElementAt(0);
                    }
                    try {
                        ensurePeer((String) job[0]).send(new Msg().raw((byte[]) job[1]).frame(9));
                    } catch (Throwable e) {}
                }
            }
        }).start();
    }

    private void findParent(final Vector cands) {
        if (!shares.enabled || parent != null || findingParent || cands.size() == 0) return;
        findingParent = true;
        new Thread(new Runnable() {
            public void run() {
                try {
                    for (int i = 0; i < cands.size() && parent == null && online; i++) {
                        Object[] c = (Object[]) cands.elementAt(i);
                        PeerConn p = PeerConn.distrib(Client.this, (String) c[0], (String) c[1], ((Integer) c[2]).intValue());
                        p.start();
                        if (p.waitReady(15000)) {
                            parent = p;
                            trySend(71, new Msg().bool(false)); // HaveNoParent: we have one now
                            log("Joined search network via " + c[0]);
                        } else p.close();
                    }
                } finally {
                    findingParent = false;
                }
            }
        }).start();
    }

    void parentLost(PeerConn p) {
        if (parent != p) return;
        parent = null;
        if (online) trySend(71, new Msg().bool(true));
    }

    void onDistrib(int code, Rd r) throws IOException {
        switch (code) {
        case 3: { // DistribSearch
            r.u32();
            String u = r.str();
            int tok = r.u32();
            searchRequest(u, tok, r.str(), null);
            break;
        }
        case 4: // BranchLevel
            trySend(126, new Msg().u32(r.u32() + 1));
            break;
        case 5: // BranchRoot
            trySend(127, new Msg().str(r.str()));
            break;
        case 93: // embedded
            onDistrib(r.u8(), r);
            break;
        default:
            break;
        }
    }

    // ================================================================ uploads

    private boolean freeSlot() {
        int active = 0;
        synchronized (uploads) {
            for (int i = 0; i < uploads.size(); i++) if (((Upload) uploads.elementAt(i)).active()) active++;
        }
        return active < uploadSlots;
    }

    private int queuedUploads() {
        int q = 0;
        synchronized (uploads) {
            for (int i = 0; i < uploads.size(); i++) if (((Upload) uploads.elementAt(i)).state == Upload.QUEUED) q++;
        }
        return q;
    }

    private boolean hasUpload(String user) {
        synchronized (uploads) {
            for (int i = 0; i < uploads.size(); i++) {
                Upload u = (Upload) uploads.elementAt(i);
                if (u.user.equals(user) && !u.finished()) return true;
            }
        }
        return false;
    }

    private Upload uploadFor(String user, String file) {
        synchronized (uploads) {
            for (int i = uploads.size() - 1; i >= 0; i--) {
                Upload u = (Upload) uploads.elementAt(i);
                if (u.user.equals(user) && u.file.equals(file) && !u.finished()) return u;
            }
        }
        return null;
    }

    private Upload uploadByToken(String user, int tok) {
        synchronized (uploads) {
            for (int i = uploads.size() - 1; i >= 0; i--) {
                Upload u = (Upload) uploads.elementAt(i);
                if (u.token == tok && (user == null || u.user.equals(user))) return u;
            }
        }
        return null;
    }

    private int placeOf(Upload u) {
        int n = 0;
        synchronized (uploads) {
            for (int i = 0; i < uploads.size(); i++) {
                Upload x = (Upload) uploads.elementAt(i);
                if (x.state == Upload.QUEUED) { n++; if (x == u) return n; }
            }
        }
        return 0;
    }

    private void queueUpload(PeerConn p, String file) throws IOException {
        Shares.Entry e = shares.enabled ? shares.find(file) : null;
        if (e == null) {
            p.send(new Msg().str(file).str("File not shared.").frame(50));
            return;
        }
        if (uploadFor(p.user, file) != null) return;
        Upload u = new Upload(p.user, e);
        uploads.addElement(u);
        uploadChanged(u);
        processUploads();
    }

    void uploadChanged(Upload u) { ui.event(EV_UPLOAD, u, null); }

    private void processUploads() {
        Upload next = null;
        synchronized (uploads) {
            int active = 0;
            for (int i = 0; i < uploads.size(); i++) if (((Upload) uploads.elementAt(i)).active()) active++;
            if (active >= uploadSlots) return;
            for (int i = 0; i < uploads.size(); i++) {
                Upload u = (Upload) uploads.elementAt(i);
                if (u.state == Upload.QUEUED) { next = u; break; }
            }
            if (next == null) return;
            next.state = Upload.OFFERED;
            next.since = System.currentTimeMillis();
            next.token = rnd.nextInt() & 0x7fffffff;
        }
        final Upload u = next;
        uploadChanged(u);
        new Thread(new Runnable() {
            public void run() {
                try {
                    ensurePeer(u.user).send(new Msg().u32(1).u32(u.token).str(u.file).u64(u.entry.size).frame(40));
                } catch (IOException e) {
                    uploadFailed(u, "Can't reach " + u.user + ": " + e.getMessage(), false);
                }
            }
        }).start();
    }

    void uploadFailed(Upload u, String why, boolean tellPeer) {
        if (u.finished()) return;
        u.state = Upload.FAILED;
        u.info = why;
        uploadChanged(u);
        if (tellPeer) {
            PeerConn p;
            synchronized (peers) { p = (PeerConn) peers.get(u.user); }
            if (p != null) try { p.send(new Msg().str(u.file).frame(46)); } catch (IOException e) {}
        }
        processUploads();
    }

    void uploadDone(Upload u) {
        u.state = Upload.DONE;
        u.info = "Sent";
        uploadedTotal++;
        uploadChanged(u);
        processUploads();
    }

    private void checkUploads() {
        long now = System.currentTimeMillis();
        Vector stale = new Vector();
        synchronized (uploads) {
            for (int i = 0; i < uploads.size(); i++) {
                Upload u = (Upload) uploads.elementAt(i);
                if ((u.state == Upload.OFFERED || u.state == Upload.CONNECTING) && now - u.since > 90000) stale.addElement(u);
            }
        }
        for (int i = 0; i < stale.size(); i++) uploadFailed((Upload) stale.elementAt(i), "No answer from downloader", true);
    }

    /** They accepted our TransferRequest: open the file connection and send. */
    private void startUploadConn(final Upload u) {
        u.state = Upload.CONNECTING;
        u.since = System.currentTimeMillis();
        uploadChanged(u);
        new Thread(new Runnable() {
            public void run() {
                try {
                    int[] a = getAddress(u.user);
                    if (a != null && a[1] != 0) {
                        PeerConn f = PeerConn.uploadTo(Client.this, u, Util.ip(a[0]), a[1]);
                        f.start();
                        if (f.waitReady(25000)) return; // PeerConn takes it from here
                        f.close();
                    }
                    if (listening) {
                        int tok = rnd.nextInt() & 0x7fffffff;
                        Pending pd = new Pending();
                        pd.user = u.user; pd.type = "F"; pd.upload = u;
                        pending.put(new Integer(tok), pd);
                        sendServer(18, new Msg().u32(tok).str(u.user).str("F"));
                        synchronized (pd) {
                            long until = System.currentTimeMillis() + 30000;
                            while (pd.conn == null && !pd.failed && System.currentTimeMillis() < until) {
                                try { pd.wait(1000); } catch (InterruptedException e) { break; }
                            }
                        }
                        pending.remove(new Integer(tok));
                        if (pd.conn != null) return;
                    }
                    uploadFailed(u, "Can't open a data connection to " + u.user, true);
                } catch (IOException e) {
                    uploadFailed(u, e.getMessage(), true);
                }
            }
        }).start();
    }

    public void removeUpload(Upload u) {
        uploadFailed(u, "Cancelled", true);
        uploads.removeElement(u);
    }

    // ================================================================ peers

    synchronized boolean peerOpened() { peerCount++; return true; }

    void peerClosed(PeerConn p) {
        synchronized (this) { peerCount--; }
        if (p.user != null) synchronized (peers) {
            if (peers.get(p.user) == p) peers.remove(p.user);
        }
        parentLost(p);
    }

    void registerPeer(PeerConn p) {
        if (p.user == null) return;
        synchronized (peers) { peers.put(p.user, p); }
    }

    /** We couldn't reach someone who asked us to connect: tell the server so they stop waiting. */
    void pierceFailed(String user, int token, String why) {
        log("Couldn't connect back to " + user + " (" + why + "). If both of you have closed ports, Soulseek can't link you.");
        trySend(1001, new Msg().u32(token).str(user));
    }

    /** Incoming PierceFirewall: match it to a ConnectToPeer we sent. */
    Pending takePending(int tok, PeerConn conn) {
        Pending pd = (Pending) pending.remove(new Integer(tok));
        if (pd == null) return null;
        synchronized (pd) { pd.conn = conn; pd.notifyAll(); }
        return pd;
    }

    private int[] getAddress(String user) throws IOException {
        int[] a = new int[3];
        addrs.put(user, a);
        try {
            sendServer(3, new Msg().str(user));
            synchronized (a) {
                long until = System.currentTimeMillis() + 20000;
                while (a[2] == 0) {
                    long left = until - System.currentTimeMillis();
                    if (left <= 0) break;
                    try { a.wait(left); } catch (InterruptedException e) { break; }
                }
            }
        } finally {
            if (addrs.get(user) == a) addrs.remove(user);
        }
        return a[2] == 0 ? null : a;
    }

    /** Get a usable "P" connection to user. Blocks; call from a worker thread. */
    PeerConn ensurePeer(String user) throws IOException {
        PeerConn p;
        synchronized (peers) { p = (PeerConn) peers.get(user); }
        if (p != null && !p.closed) return p;
        int[] a = getAddress(user);
        if (a == null) throw new IOException("No address reply");
        if (a[0] == 0 && a[1] == 0) throw new IOException("User is offline");
        if (a[1] != 0) {
            p = PeerConn.direct(this, user, Util.ip(a[0]), a[1]);
            p.start();
            if (p.waitReady(25000)) return p;
            p.close();
        }
        if (listening) {
            int tok = rnd.nextInt() & 0x7fffffff;
            Pending pd = new Pending();
            pd.user = user; pd.type = "P";
            pending.put(new Integer(tok), pd);
            sendServer(18, new Msg().u32(tok).str(user).str("P"));
            synchronized (pd) {
                long until = System.currentTimeMillis() + 30000;
                while (pd.conn == null && !pd.failed) {
                    long left = until - System.currentTimeMillis();
                    if (left <= 0) break;
                    try { pd.wait(left); } catch (InterruptedException e) { break; }
                }
            }
            pending.remove(new Integer(tok));
            if (pd.conn != null && pd.conn.waitReady(10000)) return pd.conn;
        }
        throw new IOException(listening ? "Can't reach peer" : "Can't reach peer (both firewalled?)");
    }

    void onPeer(PeerConn p, int code, Rd r) throws IOException {
        switch (code) {
        case 4: // GetShareFileList
            p.send(new Msg().raw(shares.enabled ? shares.listResponse() : Util.zlibStore(new byte[12], 12)).frame(5));
            break;
        case 5: { // SharedFileListResponse (we asked to browse them)
            try {
                byte[] raw = Inflate.zlib(r.b, r.p, r.left(), maxBrowseBytes);
                ui.event(EV_BROWSE, p.user, Browse.parse(p.user, raw));
            } catch (IOException e) {
                ui.event(EV_BROWSE, p.user, "Share list too big for the phone (" + e.getMessage()
                        + "). Try searching their files instead.");
            } catch (OutOfMemoryError e) {
                ui.event(EV_BROWSE, p.user, "Out of memory: share list too big. Try searching their files instead.");
            }
            break;
        }
        case 8: { // FileSearchRequest (old direct peer search)
            int tok = r.u32();
            searchRequest(p.user, tok, r.str(), p);
            break;
        }
        case 9:
            onSearchResponse(p, r);
            break;
        case 15: // UserInfoRequest
            p.send(new Msg().str(description).bool(false).u32(uploadedTotal).u32(queuedUploads())
                    .bool(freeSlot()).u32(shares.enabled ? 1 : 0).frame(16));
            break;
        case 16: { // UserInfoResponse
            UserInfo i = info(p.user);
            i.description = r.str();
            i.picture = null;
            if (r.bool()) {
                int len = r.u32();
                if (len > 0 && len <= 96 * 1024 && len <= r.left()) {
                    i.picture = new byte[len];
                    System.arraycopy(r.b, r.p, i.picture, 0, len);
                }
                r.skip(Math.min(len, r.left()));
            }
            if (r.left() >= 4) i.uploads = r.u32();
            if (r.left() >= 4) i.queue = r.u32();
            if (r.left() >= 1) i.slots = r.bool();
            i.gotPeer = true;
            ui.event(EV_USERINFO, i, null);
            break;
        }
        case 36: { // FolderContentsRequest
            int tok = r.u32();
            String folder = r.str();
            byte[] z = shares.enabled ? shares.folderResponse(tok, folder) : new Msg().u32(tok).str(folder).u32(0).zlib().toRaw();
            p.send(new Msg().raw(z).frame(37));
            break;
        }
        case 40: { // TransferRequest
            int dir = r.u32(), tok = r.u32();
            String file = r.str();
            if (dir == 1) {
                long size = r.left() >= 8 ? r.u64() : 0;
                Transfer t = transferByFile(p.user, file);
                if (t != null && !t.cancelled && t.state != Transfer.DONE) {
                    t.token = tok;
                    if (size > 0) t.size = size;
                    t.state = Transfer.STARTING;
                    t.info = "Waiting for data connection";
                    t.step("Uploader ready (TransferRequest), accepted");
                    changed(t);
                    p.send(new Msg().u32(tok).bool(true).frame(41));
                } else {
                    p.send(new Msg().u32(tok).bool(false).str("Cancelled").frame(41));
                }
            } else {
                // legacy download request: queue it like QueueUpload
                p.send(new Msg().u32(tok).bool(false).str("Queued").frame(41));
                queueUpload(p, file);
            }
            break;
        }
        case 41: { // TransferResponse to our upload offer
            int tok = r.u32();
            boolean allowed = r.bool();
            Upload u = uploadByToken(p.user, tok);
            if (u == null || u.state != Upload.OFFERED) break;
            if (allowed) startUploadConn(u);
            else uploadFailed(u, "Refused: " + (r.left() >= 4 ? r.str() : ""), false);
            break;
        }
        case 43: // QueueUpload
            queueUpload(p, r.str());
            break;
        case 44: { // PlaceInQueueResponse
            String file = r.str();
            int place = r.u32();
            Transfer t = transferByFile(p.user, file);
            if (t != null) { t.place = place; changed(t); }
            break;
        }
        case 46: { // UploadFailed
            Transfer t = transferByFile(p.user, r.str());
            if (t != null && !t.finished()) {
                t.step("Uploader sent UploadFailed");
                t.state = Transfer.FAILED;
                t.info = t.info.startsWith("Data connection failed") ? t.info
                        : "Remote upload failed (uploader couldn't open the data connection or read the file)";
                changed(t);
            }
            break;
        }
        case 50: { // UploadDenied
            String file = r.str();
            String why = r.left() >= 4 ? r.str() : "";
            Transfer t = transferByFile(p.user, file);
            if (t != null) { t.step("Denied: " + why); t.state = Transfer.DENIED; t.info = why; changed(t); }
            break;
        }
        case 51: { // PlaceInQueueRequest
            String file = r.str();
            Upload u = uploadFor(p.user, file);
            if (u != null) p.send(new Msg().str(file).u32(placeOf(u)).frame(44));
            break;
        }
        default:
            break;
        }
    }

    /**
     * Peers that sent search results keep their sockets open. Phones run out
     * of sockets fast, so close idle ones; uploaders reconnect when ready.
     */
    private void reapIdlePeers() {
        long now = System.currentTimeMillis();
        Vector v = new Vector();
        synchronized (peers) {
            Enumeration e = peers.elements();
            while (e.hasMoreElements()) v.addElement(e.nextElement());
        }
        for (int i = 0; i < v.size(); i++) {
            PeerConn p = (PeerConn) v.elementAt(i);
            long idle = now - p.lastActive;
            boolean busy = hasPending(p.user) || hasUpload(p.user);
            if (idle > (busy ? 120000 : 20000)) p.close();
        }
    }

    /** True if we have an unfinished download from this user. */
    boolean hasPending(String user) {
        if (user == null) return false;
        synchronized (transfers) {
            for (int i = 0; i < transfers.size(); i++) {
                Transfer t = (Transfer) transfers.elementAt(i);
                if (t.user.equals(user) && !t.finished()) return true;
            }
        }
        return false;
    }

    private Transfer startingFrom(String user) {
        synchronized (transfers) {
            for (int i = 0; i < transfers.size(); i++) {
                Transfer t = (Transfer) transfers.elementAt(i);
                if (t.user.equals(user) && t.state == Transfer.STARTING) return t;
            }
        }
        return null;
    }

    void fileConnStep(String user, String s) {
        Transfer t = startingFrom(user);
        if (t != null) t.step(s);
    }

    void fileConnFailed(String user, String why) {
        Transfer t = startingFrom(user);
        if (t != null) { t.step("Data connection failed: " + why); t.info = "Data connection failed: " + why; changed(t); }
    }

    /** Status of a remote file in our download list, for marking results. */
    public int stateOf(String user, String file) {
        Transfer t = transferByFile(user, file);
        return t == null ? -1 : t.state;
    }

    // ================================================================ downloads

    Transfer transferByFile(String user, String file) {
        synchronized (transfers) {
            for (int i = transfers.size() - 1; i >= 0; i--) {
                Transfer t = (Transfer) transfers.elementAt(i);
                if (t.user.equals(user) && t.file.equals(file)) return t;
            }
        }
        return null;
    }

    Transfer transferByToken(String user, int tok) {
        synchronized (transfers) {
            for (int i = transfers.size() - 1; i >= 0; i--) {
                Transfer t = (Transfer) transfers.elementAt(i);
                if (t.token == tok && (user == null || t.user.equals(user))) return t;
            }
        }
        return null;
    }

    /** Put back a download saved from a previous run. Unfinished ones get re-queued after login. */
    public Transfer restore(String user, String file, long size, boolean done) {
        Transfer t = transferByFile(user, file);
        if (t != null) return t;
        t = new Transfer(user, file, size);
        t.state = done ? Transfer.DONE : Transfer.FAILED;
        t.info = done ? "Saved" : "Restored from last session";
        transfers.addElement(t);
        return t;
    }

    public Transfer download(Result r) {
        Transfer t = transferByFile(r.user, r.file);
        if (t == null) {
            t = new Transfer(r.user, r.file, r.size);
            transfers.addElement(t);
        }
        queue(t);
        return t;
    }

    /** (Re)send QueueUpload. Also used for Retry. */
    public void queue(final Transfer t) {
        if (t.state == Transfer.DONE || t.active()) return;
        t.state = Transfer.CONNECTING;
        t.cancelled = false;
        t.info = "";
        changed(t);
        new Thread(new Runnable() {
            public void run() {
                try {
                    t.step("Connecting to " + t.user);
                    PeerConn p = ensurePeer(t.user);
                    p.send(new Msg().str(t.file).frame(43));
                    t.step("QueueUpload sent");
                    if (t.state == Transfer.CONNECTING) {
                        t.state = Transfer.QUEUED;
                        t.info = "Waiting for " + t.user;
                    }
                    p.send(new Msg().str(t.file).frame(51)); // ask place in queue
                } catch (IOException e) {
                    t.state = Transfer.FAILED;
                    t.info = e.getMessage();
                    t.step("Failed: " + e.getMessage());
                }
                changed(t);
            }
        }).start();
    }

    public void askPlace(final Transfer t) {
        new Thread(new Runnable() {
            public void run() {
                try { ensurePeer(t.user).send(new Msg().str(t.file).frame(51)); }
                catch (IOException e) { log("Queue check failed: " + e.getMessage()); }
            }
        }).start();
    }

    public void remove(Transfer t) {
        t.cancelled = true;
        transfers.removeElement(t);
    }

    void changed(Transfer t) { ui.transferChanged(t); }

    void log(String s) { ui.log(s); }
}
