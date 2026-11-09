package slsk;

/** A download. */
public final class Transfer {
    public static final int CONNECTING = 0, QUEUED = 1, STARTING = 2, RUNNING = 3,
        DONE = 4, FAILED = 5, DENIED = 6;
    static final String[] NAMES = { "Connecting", "Queued", "Starting", "Downloading",
        "Done", "Failed", "Denied" };

    public final String user, file;
    public volatile long size, done;
    public volatile int state;
    public volatile int place = -1;
    public volatile String info = "";
    volatile int token = -1;
    volatile long startTime, startBytes;
    volatile boolean cancelled;
    /** Auto-retry bookkeeping (see Client.retryFailed). */
    volatile long failedAt;
    volatile int retries;
    /** Subfolder of the save location (whole-folder downloads), or null. */
    volatile String subdir;
    /** file:// URL of the saved (or partial) file, once known. */
    volatile String path;
    private final StringBuffer trace = new StringBuffer();

    synchronized void step(String s) {
        if (trace.length() > 600) trace.delete(0, trace.length() - 400);
        trace.append("\n- ").append(s);
    }

    /** Original bytes of a non-UTF-8 file name, or null. */
    final byte[] raw;

    Transfer(String user, String file, long size) {
        this.user = user; this.file = file; this.size = size;
        this.raw = Util.rawBytes(file);
    }

    public boolean active() { return state == RUNNING || state == STARTING; }

    public boolean finished() { return state == DONE || state == FAILED || state == DENIED; }

    public String label() {
        StringBuffer sb = new StringBuffer();
        if (state == RUNNING && size > 0) {
            sb.append((int) (done * 100 / size)).append("% ");
        } else {
            sb.append(NAMES[state]);
            if (state == QUEUED && place >= 0) sb.append(" #").append(place);
            sb.append(' ');
        }
        sb.append(Util.baseName(file));
        if (state == RUNNING) {
            long dt = System.currentTimeMillis() - startTime;
            if (dt > 1000) sb.append(" ").append(Util.size((done - startBytes) * 1000 / dt)).append("/s");
        }
        return sb.toString();
    }

    public String details() {
        StringBuffer sb = new StringBuffer();
        sb.append(file).append("\n\nFrom: ").append(user);
        sb.append("\nStatus: ").append(NAMES[state]);
        if (place >= 0) sb.append(" (place ").append(place).append(")");
        sb.append("\nProgress: ").append(Util.size(done)).append(" / ").append(Util.size(size));
        if (info != null && info.length() > 0) sb.append("\n").append(info);
        if (trace.length() > 0) sb.append("\n\nSteps:").append(trace.toString());
        return sb.toString();
    }
}
