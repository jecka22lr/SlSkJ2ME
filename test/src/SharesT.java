package slsk;

/** Share scanning: skipped-file reporting, the per-file fallback, and adding finished downloads. */
public class SharesT {
  static void check(boolean ok, String what) { if (!ok) throw new Error("FAIL " + what); System.out.println("SharesT ok " + what); }
  public static void main(String[] a) {
    try {
      Shares s = new Shares();
      int n = s.scan("file:///E:/ShareT/");
      check(n == 3, "scan finds 3 files incl. one only readable via the folder connection (" + n + ")");
      check(s.skipped == 0, "nothing skipped");
      check(s.find("ShareT\\Album\\NOOPEN 05 Track.mp3") != null && s.find("ShareT\\Album\\NOOPEN 05 Track.mp3").size == 5, "fallback size");
      check(s.dirs() == 2, "2 folders");
      check(s.add("file:///E:/ShareT/Album/06%20New.mp3", 1234), "download inside share added");
      check(s.files() == 4 && s.find("ShareT\\Album\\06 New.mp3").size == 1234, "added entry");
      check(s.add("file:///E:/ShareT/Fresh/x.mp3", 9) && s.dirs() == 3, "new folder counted");
      check(!s.add("file:///E:/Elsewhere/y.mp3", 9), "download outside share ignored");
      check(s.listResponse() != null, "list rebuilds");
      System.exit(0);
    } catch (Throwable e) { e.printStackTrace(); System.exit(1); }
  }
}
