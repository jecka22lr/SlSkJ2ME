package slsk;

/** Someone downloading a file from us. */
public final class Upload {
    public static final int QUEUED = 0, OFFERED = 1, CONNECTING = 2, RUNNING = 3, DONE = 4, FAILED = 5;
    static final String[] NAMES = { "Queued", "Offered", "Connecting", "Uploading", "Done", "Failed" };

    public final String user, file;
    final Shares.Entry entry;
    volatile int token;
    public volatile int state;
    public volatile long done;
    public volatile String info = "";
    volatile long since = System.currentTimeMillis();
    private final StringBuffer trace = new StringBuffer();

    /** Step-by-step trace shown in Details, same as downloads. */
    synchronized void step(String s) {
        if (trace.length() > 600) trace.delete(0, trace.length() - 400);
        trace.append("\n- ").append(s);
    }

    Upload(String user, Shares.Entry e) { this.user = user; this.file = e.vpath; this.entry = e; }

    public boolean active() { return state == OFFERED || state == CONNECTING || state == RUNNING; }

    public boolean finished() { return state == DONE || state == FAILED; }

    public String label() {
        StringBuffer sb = new StringBuffer();
        if (state == RUNNING && entry.size > 0) sb.append((int) (done * 100 / entry.size)).append("% ");
        else sb.append(NAMES[state]).append(' ');
        sb.append(Util.baseName(file)).append(" > ").append(user);
        return sb.toString();
    }

    public synchronized String details() {
        return file + "\n\nTo: " + user + "\nStatus: " + NAMES[state] + "\nProgress: "
            + Util.size(done) + " / " + Util.size(entry.size) + (info.length() > 0 ? "\n" + info : "")
            + (trace.length() > 0 ? "\n\nSteps:" + trace.toString() : "");
    }
}
