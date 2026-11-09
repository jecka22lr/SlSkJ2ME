package slsk;

/** What we know about another user (from the server and their client). */
public final class UserInfo {
    public final String user;
    public String description;
    public byte[] picture;
    public int uploads = -1, queue = -1, speed = -1, files = -1, dirs = -1;
    public int status = -1; // 0 offline, 1 away, 2 online
    public boolean slots;
    public boolean gotPeer;

    UserInfo(String user) { this.user = user; }

    public String statusName() {
        return status == 0 ? "Offline" : status == 1 ? "Away" : status == 2 ? "Online" : "?";
    }
}
