package javax.microedition.io.file;
import java.io.*; import java.lang.reflect.*; import java.util.*;
public class FileSystemRegistry {
  public static Enumeration listRoots() { return new Vector(Arrays.asList("E:/")).elements(); }
  public static boolean addFileSystemListener(FileSystemListener l) { return false; }
  public static boolean removeFileSystemListener(FileSystemListener l) { return false; }
  public static String ROOT = "/tmp/slskfs/";
  public static javax.microedition.io.Connection file(String url) throws IOException {
    if (url.contains("DENY")) throw new SecurityException("Access denied");
    String path = java.net.URLDecoder.decode(url.substring("file:///".length()), "UTF-8");
    final File f = new File(ROOT + path);
    return (javax.microedition.io.Connection) Proxy.newProxyInstance(FileSystemRegistry.class.getClassLoader(), new Class[]{FileConnection.class}, (p, me, a) -> {
      switch (me.getName()) {
        case "exists": return f.exists();
        case "mkdir": f.mkdirs(); return null;
        case "create": f.createNewFile(); return null;
        case "fileSize": return f.length();
        case "truncate": new RandomAccessFile(f, "rw").setLength((Long) a[0]); return null;
        case "openOutputStream":
          if (a == null) return new FileOutputStream(f);
          final RandomAccessFile r = new RandomAccessFile(f, "rw"); r.seek((Long) a[0]);
          return new OutputStream() { public void write(int b) throws IOException { r.write(b); } public void write(byte[] b, int o, int l) throws IOException { r.write(b, o, l); } public void close() throws IOException { r.close(); } };
        default: return null; } });
  }
}
