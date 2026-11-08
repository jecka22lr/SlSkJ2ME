package slsk;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
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
    }

    static final int VERSION = 160, MINOR = 1;
    // zlib(u32 dirs=0, u32 unknown=0, u32 private dirs=0): we share nothing
    private static final byte[] EMPTY_SHARES = { 120, -100, 99, 96, 64, 0, 0, 0, 12, 0, 1 };

    final String username, password, host;
    final int port, listenPort;
    volatile String downloadDir;
    int maxSearchBytes = 192 * 1024;
    int maxResults = 400;
    int maxPeers = 10;

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
    private final Hashtable peers = new Hashtable();   // user -> PeerConn ("P")
    private final Hashtable pending = new Hashtable(); // Integer token -> Pending
    private final Hashtable addrs = new Hashtable();   // user -> int[]{ip, port, state}
    private int peerCount;

    private static final class Pending {
        String user, type;
        PeerConn conn;
        boolean failed;
    }

    public Client(Listener ui, String user, String pass, String host, int port, int listenPort, String dir) {
        this.ui = ui; this.username = user; this.password = pass; this.host = host;
        this.port = port; this.listenPort = listenPort; this.downloadDir = dir;
    }

    // ---------------------------------------------------------------- server

    public void connect() { new Thread(this).start(); }

    public void run() {
        String why = "Disconnected";
        try {
            try {
                ssc = (StreamConnection) Connector.open("socket://" + host + ":" + port, Connector.READ_WRITE, true);
            } catch (SecurityException e) {
                throw new IOException("phone denied network permission");
            }
            if (stopping) { try { ssc.close(); } catch (Throwable e) {} return; }
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
                }
            }
        } catch (Throwable e) {
            why = e.getMessage() == null ? e.toString() : e.getMessage();
        }
        boolean was = online;
        shutdown();
        if (!stopping) {
            if (was) ui.disconnected(why);
            else ui.loggedIn(false, "Can't connect: " + why);
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
            ui.loggedIn(true, greet);
            break;
        }
        case 3: { // GetPeerAddress
            String u = r.str();
            int ip = r.u32(), p = r.u32();
            int[] a = (int[]) addrs.get(u);
            if (a != null) synchronized (a) { a[0] = ip; a[1] = p; a[2] = 1; a.notifyAll(); }
            break;
        }
        case 18: { // ConnectToPeer: someone wants us to connect to them
            String u = r.str(), type = r.str();
            int ip = r.u32(), p = r.u32(), tok = r.u32();
            if (!"P".equals(type) && !"F".equals(type)) break;
            // shed search-result load, but never drop F or anyone we're downloading from
            if ("P".equals(type) && peerCount >= maxPeers && !hasPending(u)) break;
            if ("F".equals(type)) fileConnStep(u, "Uploader asked us to connect (F)");
            PeerConn.pierce(this, u, type, Util.ip(ip), p, tok).start();
            break;
        }
        case 41: // Relogged
            stopping = true;
            shutdown();
            ui.disconnected("Logged in from another place");
            break;
        case 1001: { // CantConnectToPeer
            int tok = r.u32();
            Pending pd = (Pending) pending.get(new Integer(tok));
            if (pd != null) synchronized (pd) { pd.failed = true; pd.notifyAll(); }
            break;
        }
        default:
            // rooms, privileges, distributed network, etc: not handled
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

    private void afterLogin() {
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
        trySend(28, new Msg().u32(2));            // SetStatus online
        trySend(35, new Msg().u32(0).u32(0));     // SharedFoldersFiles
        trySend(71, new Msg().bool(true));        // HaveNoParent
        new Thread(new Runnable() {
            public void run() {
                while (online && !stopping) {
                    try { Thread.sleep(90000); } catch (InterruptedException e) {}
                    if (online) trySend(32, new Msg()); // ServerPing keeps NAT alive
                }
            }
        }).start();
        new Thread(new Runnable() {
            public void run() {
                while (online && !stopping) {
                    try { Thread.sleep(10000); } catch (InterruptedException e) {}
                    reapIdlePeers();
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
        Vector v = new Vector();
        synchronized (peers) {
            java.util.Enumeration e = peers.elements();
            while (e.hasMoreElements()) v.addElement(e.nextElement());
        }
        for (int i = 0; i < v.size(); i++) ((PeerConn) v.elementAt(i)).close();
    }

    public boolean isOnline() { return online; }

    public void setDownloadDir(String d) { downloadDir = d; }

    // ---------------------------------------------------------------- search

    public void search(String query) {
        searchToken = rnd.nextInt() & 0x7fffffff;
        results.removeAllElements();
        trySend(26, new Msg().u32(searchToken).str(query));
        log("Searching: " + query);
    }

    private void onSearchResponse(PeerConn p, Rd r0) throws IOException {
        if (results.size() >= maxResults) return;
        byte[] raw = Inflate.zlib(r0.b, r0.p, r0.left(), 2 * 1024 * 1024);
        Rd r = new Rd(raw, 0, raw.length);
        String user = r.str();
        int tok = r.u32();
        if (tok != searchToken) return;
        int n = r.u32();
        Result[] got = new Result[Math.min(n, 2000)];
        int k = 0;
        for (int i = 0; i < n; i++) {
            r.u8();
            String file = r.str();
            long size = r.u64();
            r.str(); // extension, usually empty
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
        for (int i = 0; i < k; i++) {
            if (results.size() >= maxResults) break;
            got[i].slot = slot; got[i].speed = speed; got[i].queue = queue;
            results.addElement(got[i]);
            ui.result(got[i]);
        }
    }

    // ---------------------------------------------------------------- peers

    synchronized boolean peerOpened() { peerCount++; return true; }

    void peerClosed(PeerConn p) {
        synchronized (this) { peerCount--; }
        if (p.user != null) synchronized (peers) {
            if (peers.get(p.user) == p) peers.remove(p.user);
        }
    }

    void registerPeer(PeerConn p) {
        if (p.user == null) return;
        synchronized (peers) { peers.put(p.user, p); }
    }

    /** Incoming PierceFirewall: match it to a ConnectToPeer we sent. */
    String[] takePending(int tok, PeerConn conn) {
        Pending pd = (Pending) pending.remove(new Integer(tok));
        if (pd == null) return null;
        synchronized (pd) { pd.conn = conn; pd.notifyAll(); }
        return new String[] { pd.user, pd.type };
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
            addrs.remove(user);
        }
        return a[2] == 0 ? null : a;
    }

    /** Get a usable "P" connection to user. Blocks; call from a worker thread. */
    private PeerConn ensurePeer(String user) throws IOException {
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
        case 9:
            onSearchResponse(p, r);
            break;
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
                p.send(new Msg().u32(tok).bool(false).str("File not shared.").frame(41));
            }
            break;
        }
        case 43: { // QueueUpload: they want a file from us
            String file = r.str();
            p.send(new Msg().str(file).str("File not shared.").frame(50));
            break;
        }
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
        case 4: // GetShareFileList
            p.send(new Msg().raw(EMPTY_SHARES).frame(5));
            break;
        case 15: // UserInfoRequest
            p.send(new Msg().str("SlskME, a J2ME Soulseek client").bool(false)
                    .u32(0).u32(0).bool(false).u32(0).frame(16));
            break;
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
            java.util.Enumeration e = peers.elements();
            while (e.hasMoreElements()) v.addElement(e.nextElement());
        }
        for (int i = 0; i < v.size(); i++) {
            PeerConn p = (PeerConn) v.elementAt(i);
            long idle = now - p.lastActive;
            if (idle > (hasPending(p.user) ? 120000 : 20000)) p.close();
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

    // ---------------------------------------------------------------- transfers

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
