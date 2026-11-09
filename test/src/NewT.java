package slsk;

import java.io.*;

/** 0.5.0 features that don't need a network: encoding fallback, throttle, .part files, folders, share cache. */
public class NewT {
  static void check(boolean ok, String what) { if (!ok) throw new Error("FAIL " + what); System.out.println("NewT ok " + what); }
  public static void main(String[] a) {
    try { run(); System.exit(0); } catch (Throwable e) { e.printStackTrace(); System.exit(1); }
  }
  static int sizeOf(byte[] b) throws Exception {
    int[] wh = ChatColors.imageSize(new ByteArrayInputStream(b));
    return wh == null ? -1 : wh[0] * 10000 + wh[1];
  }
  static byte[] png(int w, int h) {
    byte[] b = new byte[24];
    byte[] sig = { (byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 13, 'I', 'H', 'D', 'R' };
    System.arraycopy(sig, 0, b, 0, 16);
    b[16] = (byte) (w >>> 24); b[17] = (byte) (w >>> 16); b[18] = (byte) (w >>> 8); b[19] = (byte) w;
    b[20] = (byte) (h >>> 24); b[21] = (byte) (h >>> 16); b[22] = (byte) (h >>> 8); b[23] = (byte) h;
    return b;
  }
  static byte[] jpeg(int w, int h, boolean exif) {
    ByteArrayOutputStream o = new ByteArrayOutputStream();
    o.write(0xff); o.write(0xd8);
    if (exif) { o.write(0xff); o.write(0xe1); int len = 5000; o.write(len >> 8); o.write(len & 255); for (int i = 0; i < len - 2; i++) o.write(0x55); }
    o.write(0xff); o.write(0xdb); o.write(0); o.write(4); o.write(0); o.write(0); // a quant table stub
    o.write(0xff); o.write(0xc0); o.write(0); o.write(11); o.write(8);
    o.write(h >> 8); o.write(h & 255); o.write(w >> 8); o.write(w & 255);
    for (int i = 0; i < 6; i++) o.write(0);
    return o.toByteArray();
  }
  static void run() throws Exception {
    // encoding: CP1252/Latin-1 name shows readable and goes back out as the same bytes
    byte[] latin = { 'C', 'a', 'f', (byte) 0xe9, ' ', '0', '1', '.', 'm', 'p', '3' };
    check(!Util.validUtf8(latin, 0, latin.length), "latin-1 bytes are not utf-8");
    String s = Util.decode(latin, 0, latin.length);
    check(s.equals("Caf\u00e9 01.mp3"), "decoded as latin-1: " + s);
    check(java.util.Arrays.equals(Util.utf8(s), latin), "re-encoded to the original bytes");
    byte[] u8 = "Caf\u00e9".getBytes("UTF-8");
    check(Util.validUtf8(u8, 0, u8.length) && Util.decode(u8, 0, u8.length).equals("Caf\u00e9"), "real utf-8 untouched");
    byte[] cut = { 'a', (byte) 0xc3 };
    check(!Util.validUtf8(cut, 0, 2), "truncated sequence rejected");

    // a download with a non-UTF-8 name keeps its bytes even if the name cache gets cleared
    Transfer tr = new Transfer("u", s, 1);
    for (int i = 0; i < 3100; i++) Util.rememberRaw("filler" + i, new byte[] { 1 });
    check(Util.rawBytes(s) == null, "name cache was cleared");
    if (tr.raw != null) Util.rememberRaw(tr.file, tr.raw); // what Client.queue does
    check(java.util.Arrays.equals(Util.utf8(s), latin), "transfer restores its raw name bytes");

    // chat nicknames
    check(ChatView.nameOf("jecka: hi: there").equals("jecka"), "nickname before the first ': '");
    check(ChatView.nameOf("no nickname here").equals(""), "line without nickname");
    check(ChatView.nameOf(": odd").equals(""), "empty nickname ignored");

    // background image size from headers, without decoding
    check(sizeOf(png(1920, 1080)) == 1920 * 10000 + 1080, "PNG header size");
    byte[] gif = { 'G', 'I', 'F', '8', '9', 'a', (byte) 0x40, 0x01, (byte) 0xf0, 0x00 };
    check(sizeOf(gif) == 320 * 10000 + 240, "GIF header size");
    check(sizeOf(jpeg(3000, 4000, true)) == 3000 * 10000 + 4000, "JPEG size after an EXIF segment");
    check(sizeOf(jpeg(640, 480, false)) == 640 * 10000 + 480, "JPEG size, no EXIF");
    byte[] bmp = new byte[26]; bmp[0] = 'B'; bmp[1] = 'M'; bmp[18] = (byte) 0xe0; bmp[19] = 0x01; bmp[22] = 0x20; bmp[23] = 0x03;
    check(sizeOf(bmp) == 480 * 10000 + 800, "BMP header size");
    check(ChatColors.tooBig(1600, 1200) == null && ChatColors.tooBig(480, 800) == null, "up to ~2 MP allowed");
    check(ChatColors.tooBig(4000, 3000) != null && ChatColors.tooBig(2048, 1536) != null, "bigger photos refused");
    check(sizeOf(new byte[] { 1, 2, 3, 4 }) == -1, "unknown format: no size, decoder decides");

    // throttle: 200 KB at 100 KB/s takes about 2 s
    Throttle th = new Throttle();
    th.kbps = 100;
    long t0 = System.currentTimeMillis();
    for (int i = 0; i < 25; i++) th.took(8192);
    long ms = System.currentTimeMillis() - t0;
    check(ms >= 1500 && ms < 4000, "throttle ~2 s for 200 KB at 100 KB/s (" + ms + " ms)");
    th.kbps = 0;
    t0 = System.currentTimeMillis();
    for (int i = 0; i < 100; i++) th.took(8192);
    check(System.currentTimeMillis() - t0 < 200, "no limit = no waiting");

    // .part while downloading, renamed when done, inside the folder's own subfolder
    Transfer t = new Transfer("u", "Music\\Some Album\\01 Song.mp3", 5);
    t.subdir = "Some Album";
    Store.Sink k = Store.open("file:///E:/DL/", t);
    check(k.url.endsWith("01%20Song.mp3.part"), "writes to .part: " + k.url);
    k.write("hello".getBytes(), 0, 5);
    String fin = k.finish();
    check(fin.equals("file:///E:/DL/Some%20Album/01%20Song.mp3"), "renamed to final name: " + fin);
    check(new File("/tmp/slskfs/E:/DL/Some Album/01 Song.mp3").length() == 5, "final file has the data");
    check(!new File("/tmp/slskfs/E:/DL/Some Album/01 Song.mp3.part").exists(), "no .part left behind");
    // same name, different (bigger) existing file: download next to it as "(2)"
    Transfer t2 = new Transfer("v", "Other\\01 Song.mp3", 3);
    t2.subdir = "Some Album";
    k = Store.open("file:///E:/DL/", t2);
    k.write("abc".getBytes(), 0, 3);
    fin = k.finish();
    check(fin.endsWith("01%20Song%20(2).mp3"), "name taken -> (2): " + fin);
    // partial resume: an existing .part continues where it stopped
    Transfer t3 = new Transfer("w", "x\\03 Part.mp3", 10);
    k = Store.open("file:///E:/DL/", t3);
    k.write("12345".getBytes(), 0, 5);
    k.close();
    k = Store.open("file:///E:/DL/", t3);
    check(k.offset == 5 && k.url.endsWith(".part"), "resumes .part at offset 5");
    k.close();
    check(Store.guessPath("file:///E:/DL/", t).equals("file:///E:/DL/Some%20Album/01%20Song.mp3"), "guessPath");

    // share cache round trip
    Shares sh = new Shares();
    int n = sh.scan("file:///E:/ShareT/");
    ByteArrayOutputStream bo = new ByteArrayOutputStream();
    sh.writeCache(new DataOutputStream(bo));
    Shares sh2 = new Shares();
    int m = sh2.readCache(new DataInputStream(new ByteArrayInputStream(bo.toByteArray())), "file:///E:/ShareT/");
    check(m == n && sh2.dirs() == sh.dirs() && sh2.find("ShareT\\Album\\01 Song.mp3") != null
        && sh2.find("ShareT\\Album\\01 Song.mp3").url.equals("file:///E:/ShareT/Album/01%20Song.mp3"), "share cache round trip (" + m + ")");
    check(new Shares().readCache(new DataInputStream(new ByteArrayInputStream(bo.toByteArray())), "file:///E:/Other/") == -1,
        "cache for another folder is ignored");
    new File("/tmp/slskfs/E:/ShareT/Album/04 x.mp3.part").createNewFile();
    check(new Shares().scan("file:///E:/ShareT/") == n, ".part files aren't shared");
  }
}
