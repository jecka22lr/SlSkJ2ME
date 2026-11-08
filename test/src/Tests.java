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
    System.out.println(Util.ip(0x7F000001) + " " + Util.size(5_500_000) + " " + Util.urlEscape("Bjørk - 01 Hyper ballad.mp3"));
  }
}
