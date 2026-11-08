package slsk;

/** One file from a search response. Kept small: phones have little heap. */
public final class Result {
    public String user, file;
    public long size;
    public int bitrate, duration; // 0 if unknown
    public boolean slot;
    public int speed, queue;

    public String label() {
        StringBuffer sb = new StringBuffer();
        sb.append(slot ? "" : "[Q] ");
        sb.append(Util.baseName(file));
        sb.append(" | ").append(Util.size(size));
        if (bitrate > 0) sb.append(" | ").append(bitrate).append("k");
        sb.append(" | ").append(user);
        return sb.toString();
    }

    public String details() {
        StringBuffer sb = new StringBuffer();
        sb.append(file).append("\n\nUser: ").append(user);
        sb.append("\nSize: ").append(Util.size(size));
        if (bitrate > 0) sb.append("\nBitrate: ").append(bitrate).append(" kbps");
        if (duration > 0) sb.append("\nLength: ").append(Util.duration(duration));
        sb.append("\nFree slot: ").append(slot ? "yes" : "no");
        sb.append("\nQueue: ").append(queue);
        sb.append("\nSpeed: ").append(Util.size(speed)).append("/s");
        return sb.toString();
    }
}
