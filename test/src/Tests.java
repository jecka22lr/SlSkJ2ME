package slsk;
import java.security.MessageDigest; import java.util.*; import java.util.zip.Deflater;
public class Tests {
  public static void main(String[] a) throws Exception {
    Random r = new Random(1);
    // MD5
    for (int i = 0; i < 300; i++) {
      byte[] b = new byte[r.nextInt(300)]; r.nextBytes(b);
      if (!Arrays.equals(MD5.digest(b), MessageDigest.getInstance("MD5").digest(b))) throw new Error("md5 " + i);
    }
    System.out.println("md5 ok " + MD5.hex("usernamepassword"));
    // Inflate: random, text-like, repetitive, all levels & strategies
    int n = 0;
    for (int i = 0; i < 400; i++) {
      int len = r.nextInt(i % 7 == 0 ? 300000 : 5000);
      byte[] b = new byte[len];
      int kind = i % 3;
      for (int j = 0; j < len; j++) b[j] = (byte) (kind == 0 ? r.nextInt(256) : kind == 1 ? "abcdefgh \\Music".charAt(r.nextInt(15)) : (j / 37) % 5);
      Deflater d = new Deflater(i % 10); if (i % 4 == 3) d.setStrategy(Deflater.HUFFMAN_ONLY); if (i % 5 == 4) d.setStrategy(Deflater.FILTERED);
      d.setInput(b); d.finish(); byte[] o = new byte[len * 2 + 100]; int ol = 0; while (!d.finished()) ol += d.deflate(o, ol, o.length - ol);
      if (!Arrays.equals(Inflate.zlib(o, 0, ol, 4 << 20), b)) throw new Error("inflate " + i);
      n++;
    }
    System.out.println("inflate ok x" + n);
    // result grouping: free-slot users first, then faster; files grouped by folder; filter drops empty groups
    ResultGroups g = new ResultGroups();
    String[][] rs = { {"slowfree","M\\A\\Alb\\1.flac","1","100"}, {"queued","X\\B\\2.mp3","0","9999"},
                      {"fastfree","Y\\C\\D\\3.mp3","1","500"}, {"slowfree","M\\A\\Alb\\4.mp3","1","100"},
                      {"slowfree","M\\Other\\5.mp3","1","100"} };
    for (int i = 0; i < rs.length; i++) {
      Result x = new Result(); x.user = rs[i][0]; x.file = rs[i][1]; x.slot = rs[i][2].equals("1"); x.speed = Integer.parseInt(rs[i][3]); x.size = 1000;
      g.add(x);
    }
    StringBuilder sb = new StringBuilder();
    for (Object o : g.rows(null)) sb.append(o instanceof ResultGroups.User ? "U:" + ((ResultGroups.User) o).name
        : o instanceof ResultGroups.Dir ? " D:" + ResultGroups.shortDir(((ResultGroups.Dir) o).path) : " F:" + Util.baseName(((Result) o).file)).append(";");
    String want = "U:fastfree; D:C/D; F:3.mp3;U:slowfree; D:A/Alb; F:1.flac; F:4.mp3; D:M/Other; F:5.mp3;U:queued; D:X/B; F:2.mp3;";
    if (!sb.toString().equals(want)) throw new Error("grouping: " + sb);
    int mp3 = g.rows(new ResultGroups.Filter() { public boolean passes(Result r) { return r.file.endsWith(".mp3"); } }).size();
    if (mp3 != 11 || g.total() != 5) throw new Error("filter rows " + mp3);
    if (!ResultGroups.shortDir("").equals("(top folder)") || !ResultGroups.shortDir("Solo").equals("Solo")) throw new Error("shortDir");
    System.out.println("grouping ok");
    System.out.println(Util.ip(0x7F000001) + " " + Util.size(5_500_000) + " " + Util.urlEscape("Bjørk - 01 Hyper ballad.mp3"));
  }
}
