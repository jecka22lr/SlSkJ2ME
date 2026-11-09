package slsk;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.microedition.io.Connector;
import javax.microedition.io.SocketConnection;
import javax.microedition.io.StreamConnection;

/**
 * A peer connection. Three ways in:
 *  PIERCE  - server told us (ConnectToPeer) to connect to someone and send PierceFirewall
 *  DIRECT  - we connect to someone and send PeerInit (to queue a download)
 *  IN      - someone connected to our listening port
 * After the init message it is either a "P" (messages) or "F" (file data) connection.
 */
final class PeerConn implements Runnable {
    static final int PIERCE = 0, DIRECT = 1, IN = 2, DISTRIB = 3, UPLOAD = 4;

    private final Client c;
    private final int mode;
    String user, type;
    private String host;
    private int port, token;
    private Upload upload;
    private StreamConnection sc;
    private DataInputStream in;
    private OutputStream out;
    volatile boolean closed;
    volatile long lastActive = System.currentTimeMillis();
    private final Object lock = new Object();
    private int ready; // 0 waiting, 1 ok, -1 failed

    private PeerConn(Client c, int mode) { this.c = c; this.mode = mode; }

    static PeerConn pierce(Client c, String user, String type, String host, int port, int token) {
        PeerConn p = new PeerConn(c, PIERCE);
        p.user = user; p.type = type; p.host = host; p.port = port; p.token = token;
        return p;
    }

    static PeerConn direct(Client c, String user, String host, int port) {
        PeerConn p = new PeerConn(c, DIRECT);
        p.user = user; p.type = "P"; p.host = host; p.port = port;
        return p;
    }

    /** We connect to a distributed-network parent to receive other people's searches. */
    static PeerConn distrib(Client c, String user, String host, int port) {
        PeerConn p = new PeerConn(c, DISTRIB);
        p.user = user; p.type = "D"; p.host = host; p.port = port;
        return p;
    }

    /** We connect to a downloader to send them a file. */
    static PeerConn uploadTo(Client c, Upload u, String host, int port) {
        PeerConn p = new PeerConn(c, UPLOAD);
        p.user = u.user; p.type = "F"; p.host = host; p.port = port; p.upload = u;
        return p;
    }

    static PeerConn incoming(Client c, StreamConnection sc) {
        PeerConn p = new PeerConn(c, IN);
        p.sc = sc;
        return p;
    }

    void start() { new Thread(this).start(); }

    /** Wait until the connection is usable for messages. */
    boolean waitReady(long ms) {
        long until = System.currentTimeMillis() + ms;
        synchronized (lock) {
            while (ready == 0) {
                long left = until - System.currentTimeMillis();
                if (left <= 0) break;
                try { lock.wait(left); } catch (InterruptedException e) { break; }
            }
            return ready == 1;
        }
    }

    private void setReady(int v) {
        synchronized (lock) { if (ready == 0) ready = v; lock.notifyAll(); }
    }

    synchronized void send(byte[] frame) throws IOException {
        if (closed || out == null) throw new IOException("peer closed");
        out.write(frame);
        out.flush();
        lastActive = System.currentTimeMillis();
    }

    void close() {
        if (closed) return;
        closed = true;
        setReady(-1);
        try { if (in != null) in.close(); } catch (Throwable e) {}
        try { if (out != null) out.close(); } catch (Throwable e) {}
        try { if (sc != null) sc.close(); } catch (Throwable e) {}
    }

    public void run() {
        boolean counted = c.peerOpened();
        try {
            if ("F".equals(type) && upload == null) c.fileConnStep(user, "Data connection to " + host + ":" + port);
            if (mode != IN) {
                try {
                    sc = (StreamConnection) Connector.open("socket://" + host + ":" + port, Connector.READ_WRITE, true);
                } catch (SecurityException e) {
                    throw new IOException("phone denied network permission");
                }
                try { ((SocketConnection) sc).setSocketOption(SocketConnection.DELAY, 0); } catch (Throwable e) {}
            }
            in = sc.openDataInputStream();
            out = sc.openOutputStream();
            if (mode == PIERCE) {
                send(new Msg().u32(token).initFrame(0));
            } else if (mode == DIRECT || mode == DISTRIB || mode == UPLOAD) {
                send(new Msg().str(c.username).str(type).u32(0).initFrame(1));
            } else {
                readInit();
            }
            if ("P".equals(type)) {
                c.registerPeer(this);
                setReady(1);
                messageLoop();
            } else if ("F".equals(type)) {
                setReady(1);
                if (upload != null) sendFile();
                else fileTransfer();
            } else if ("D".equals(type) && mode == DISTRIB) {
                setReady(1);
                distribLoop();
            }
            // incoming "D" (someone wanting us as their parent): we accept no children
        } catch (Throwable e) {
            String m = e.getMessage() == null ? e.toString() : e.getMessage();
            if (mode == DIRECT || "F".equals(type)) c.log("Peer " + user + " (" + type + "): " + m);
            if ("F".equals(type) && upload == null) c.fileConnFailed(user, m);
            if (upload != null && ready == 1) c.uploadFailed(upload, m, true);
        } finally {
            close();
            if (counted) c.peerClosed(this);
        }
    }

    private void readInit() throws IOException {
        int len = Util.readU32(in);
        if (len < 1 || len > 4096) throw new IOException("bad init");
        byte[] b = new byte[len];
        Util.readFully(in, b, 0, len);
        Rd r = new Rd(b, 0, len);
        int code = r.u8();
        if (code == 0) { // PierceFirewall answering our ConnectToPeer
            int tok = r.u32();
            Client.Pending pd = c.takePending(tok, this);
            if (pd == null) throw new IOException("unknown pierce token");
            user = pd.user; type = pd.type; upload = pd.upload;
        } else if (code == 1) { // PeerInit
            user = r.str();
            type = r.str();
        } else throw new IOException("bad init code");
    }

    private void messageLoop() throws IOException {
        while (!closed) {
            int len = Util.readU32(in);
            if (len < 4) throw new IOException("bad frame");
            int code = Util.readU32(in);
            int plen = len - 4;
            int max = code == 9 ? c.maxSearchBytes : 256 * 1024;
            if (plen > max) {
                Util.skip(in, plen);
                continue;
            }
            byte[] b = new byte[plen];
            Util.readFully(in, b, 0, plen);
            lastActive = System.currentTimeMillis();
            try {
                c.onPeer(this, code, new Rd(b, 0, plen));
            } catch (IOException e) {
                c.log("Bad msg " + code + " from " + user);
            } catch (OutOfMemoryError e) {
                c.log("Out of memory reading results");
            }
        }
    }

    private void distribLoop() throws IOException {
        while (!closed) {
            int len = Util.readU32(in);
            if (len < 1) throw new IOException("bad distrib frame");
            if (len > 64 * 1024) { Util.skip(in, len); continue; }
            byte[] b = new byte[len];
            Util.readFully(in, b, 0, len);
            lastActive = System.currentTimeMillis();
            try {
                c.onDistrib(b[0] & 0xff, new Rd(b, 1, len - 1));
            } catch (IOException e) {}
        }
    }

    /** We are the uploader: FileTransferInit(token), read the offset, stream the file. */
    private void sendFile() throws IOException {
        Upload u = upload;
        send(new Msg().u32(u.token).toRaw());
        byte[] ob = new byte[8];
        Util.readFully(in, ob, 0, 8);
        long offset = new Rd(ob, 0, 8).u64();
        if (offset < 0 || offset > u.entry.size) offset = 0;
        javax.microedition.io.file.FileConnection fc = null;
        InputStream fin = null;
        try {
            try {
                fc = (javax.microedition.io.file.FileConnection) Connector.open(u.entry.url, Connector.READ);
                fin = fc.openInputStream();
            } catch (SecurityException e) {
                throw new IOException("phone denied reading the file");
            }
            long skipped = 0;
            while (skipped < offset) {
                long k = fin.skip(offset - skipped);
                if (k <= 0) throw new IOException("can't seek in file");
                skipped += k;
            }
            u.done = offset;
            u.state = Upload.RUNNING;
            u.info = "";
            c.uploadChanged(u);
            byte[] buf = new byte[8192];
            long lastUi = 0;
            while (u.done < u.entry.size) {
                int r = fin.read(buf, 0, (int) Math.min(buf.length, u.entry.size - u.done));
                if (r < 0) throw new IOException("file shorter than shared size");
                out.write(buf, 0, r);
                u.done += r;
                long now = System.currentTimeMillis();
                if (now - lastUi > 700) { lastUi = now; c.uploadChanged(u); }
            }
            out.flush();
            c.uploadDone(u);
            upload = null; // so the close below doesn't count as a failure
            // let the downloader close first so the tail isn't cut off; force it after 8 s
            new Thread(new Runnable() {
                public void run() {
                    try { Thread.sleep(8000); } catch (InterruptedException e) {}
                    close();
                }
            }).start();
            try { while (in.read() >= 0) {} } catch (IOException e) {}
        } finally {
            try { if (fin != null) fin.close(); } catch (Throwable e) {}
            try { if (fc != null) fc.close(); } catch (Throwable e) {}
        }
    }

    private void fileTransfer() throws IOException {
        int tok = Util.readU32(in); // FileTransferInit from uploader
        Transfer t = c.transferByToken(user, tok);
        if (t == null) t = c.transferByToken(null, tok);
        if (t == null) throw new IOException("no transfer for token " + tok);
        t.step("Data connection open, token ok");
        Store.Sink sink = null;
        try {
            sink = Store.open(c.downloadDir, t);
            t.step("File open: " + sink.where);
            long offset = sink.offset;
            if (offset > t.size) { offset = 0; sink.truncate(); }
            t.done = offset;
            t.startBytes = offset;
            t.startTime = System.currentTimeMillis();
            t.state = Transfer.RUNNING;
            t.info = "Saving to " + sink.name;
            c.changed(t);
            send(new Msg().u64(offset).toRaw());
            byte[] buf = new byte[8192];
            InputStream is = in;
            long lastUi = 0;
            while (t.done < t.size) {
                if (t.cancelled) throw new IOException("cancelled");
                int want = (int) Math.min(buf.length, t.size - t.done);
                int r = is.read(buf, 0, want);
                if (r < 0) throw new IOException("connection lost");
                sink.write(buf, 0, r);
                t.done += r;
                long now = System.currentTimeMillis();
                if (now - lastUi > 700) { lastUi = now; c.changed(t); }
            }
            String savedUrl = sink.url;
            sink.close();
            sink = null;
            t.state = Transfer.DONE;
            c.downloadSaved(savedUrl, t.size);
            t.info = "Saved";
            t.step("Done");
            c.log("Finished " + Util.baseName(t.file));
        } catch (IOException e) {
            if (t.state != Transfer.DONE) {
                t.state = Transfer.FAILED;
                t.info = e.getMessage() + " (Retry resumes)";
                t.step("Data failed: " + e.getMessage());
            }
            throw e;
        } finally {
            if (sink != null) sink.close();
            c.changed(t);
        }
    }
}
