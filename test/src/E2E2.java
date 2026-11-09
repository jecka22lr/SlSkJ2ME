package slsk;
import java.util.Vector;
public class E2E2 implements Client.Listener {
  volatile boolean in; volatile int ok;
  volatile Object browse; volatile UserInfo info; volatile int wish, roomMsgs; volatile boolean pm, upDone;
  public void loggedIn(boolean o, String m) { in = o; }
  public void disconnected(String w) { System.out.println("UI disconnected " + w); }
  public void result(Result r) {}
  public void transferChanged(Transfer t) {}
  public void log(String s) { System.out.println("UI log " + s); }
  public void event(int t, Object a, Object b) {
    switch (t) {
      case Client.EV_PM: pm = true; System.out.println("UI pm " + b); break;
      case Client.EV_ROOMMSG: roomMsgs++; System.out.println("UI room " + b); break;
      case Client.EV_USERINFO: if (((UserInfo) a).gotPeer) info = (UserInfo) a; break;
      case Client.EV_BROWSE: browse = b; break;
      case Client.EV_WISH: wish++; System.out.println("UI wish " + ((Result) a).file); break;
      case Client.EV_UPLOAD: Upload u = (Upload) a; System.out.println("UI upload " + u.label() + " " + u.info); if (u.state == Upload.DONE) upDone = true; break;
      case Client.EV_ROOMLIST: System.out.println("UI rooms " + ((Client) null == null ? "" : "")); break;
    }
  }
  static void waitFor(java.util.function.BooleanSupplier f, String what) throws Exception {
    long until = System.currentTimeMillis() + 15000;
    while (!f.getAsBoolean()) { if (System.currentTimeMillis() > until) throw new Error("timeout: " + what); Thread.sleep(50); }
    System.out.println("E2E2 ok " + what);
  }
  public static void main(String[] a) throws Exception {
    E2E2 ui = new E2E2();
    Client c = new Client(ui, "jecka", "pw", "127.0.0.1", 22520, 0, "file:///E:/Soulseek/");
    c.description = "phone gang";
    c.wishlist.addElement("rare album");
    c.connect();
    waitFor(() -> ui.in, "login");
    waitFor(() -> c.roomNames.length == 3, "room list");
    waitFor(() -> ui.pm, "incoming PM");
    c.sendPM("peer1", "yes, j2me stuff");
    c.joinRoom("nokia");
    waitFor(() -> c.rooms.get("nokia") != null && ui.roomMsgs >= 1, "join room + welcome");
    c.say("nokia", "hello room");
    waitFor(() -> ui.roomMsgs >= 2, "room echo");
    waitFor(() -> ui.wish >= 1, "wishlist hit");
    c.requestUserInfo("peer1");
    waitFor(() -> ui.info != null && "I collect old Nokias".equals(ui.info.description) && ui.info.status == 2 && ui.info.files == 1234, "user profile");
    c.browse("peer1");
    waitFor(() -> ui.browse != null, "browse");
    Browse b = (Browse) ui.browse;
    Result[] f = b.files(0);
    if (b.dirs.length != 1 || f.length != 3 || f[2].bitrate != 192 || !f[1].file.equals("Music\\Album\\01 track.mp3")) throw new Error("browse parse " + b.dirs.length + " " + f[1].file);
    System.out.println("E2E2 ok browse parse");
    System.out.println("scanned " + c.scanShares("file:///E:/Share/"));
    waitFor(() -> ui.upDone, "upload to searcher");
    Thread.sleep(500);
    System.exit(0);
  }
}
