package slsk;
public class E2E implements Client.Listener {
  volatile boolean in; volatile int res;
  public void loggedIn(boolean ok, String m) { System.out.println("UI loggedIn " + ok + " " + m); in = ok; }
  public void disconnected(String w) { System.out.println("UI disconnected " + w); }
  public void result(Result r) { res++; System.out.println("UI result " + r.label()); }
  public void transferChanged(Transfer t) { }
  public void log(String s) { System.out.println("UI log " + s); }
  public void event(int t, Object a, Object b) {}
  public static void main(String[] a) throws Exception {
    try { run(); } catch (Throwable t) { t.printStackTrace(); System.out.println("TEST FAILED: " + t); System.exit(1); }
    System.exit(0);
  }
  static void run() throws Exception {
    E2E ui = new E2E();
    Client c = new Client(ui, "jecka", "hunter2", "127.0.0.1", 22420, 0, Store.defaultDir());
    System.out.println("dir " + c.downloadDir);
    c.connect();
    while (!ui.in) Thread.sleep(50);
    c.search("bjork hyper ballad");
    while (ui.res < 2) Thread.sleep(50);
    Thread.sleep(500);
    System.out.println("results kept: " + c.results.size());
    Transfer t = c.download((Result) c.results.elementAt(0));
    long until = System.currentTimeMillis() + 30000;
    while (!t.finished() && System.currentTimeMillis() < until) Thread.sleep(100);
    System.out.println("TRANSFER " + t.label() + " | " + t.details().replace('\n', ' '));
    c.logout();
    System.exit(0);
  }
}
