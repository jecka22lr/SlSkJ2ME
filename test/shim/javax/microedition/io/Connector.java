package javax.microedition.io;
import java.io.*; import java.lang.reflect.Proxy; import java.net.Socket; import java.net.ServerSocket;
public class Connector {
  public static final int READ=1, WRITE=2, READ_WRITE=3;
  public static Connection open(String u) throws IOException { return open(u, READ_WRITE, false); }
  public static Connection open(String u, int m) throws IOException { return open(u, m, false); }
  public static Connection open(String u, int m, boolean t) throws IOException {
    if (u.startsWith("socket://:")) {
      final ServerSocket ss = new ServerSocket(Integer.parseInt(u.substring(10)));
      return (Connection) Proxy.newProxyInstance(Connector.class.getClassLoader(), new Class[]{ServerSocketConnection.class}, (p, me, a) -> {
        switch (me.getName()) { case "acceptAndOpen": return sock(ss.accept()); case "close": ss.close(); return null; case "getLocalPort": return ss.getLocalPort(); default: return null; } });
    }
    if (u.startsWith("socket://")) {
      String hp = u.substring(9); int i = hp.lastIndexOf(':');
      return sock(new Socket(hp.substring(0, i), Integer.parseInt(hp.substring(i + 1))));
    }
    if (u.startsWith("file://")) return javax.microedition.io.file.FileSystemRegistry.file(u);
    throw new ConnectionNotFoundException(u);
  }
  static Connection sock(final Socket s) {
    return (Connection) Proxy.newProxyInstance(Connector.class.getClassLoader(), new Class[]{SocketConnection.class}, (p, me, a) -> {
      switch (me.getName()) {
        case "openInputStream": return s.getInputStream();
        case "openDataInputStream": return new DataInputStream(s.getInputStream());
        case "openOutputStream": return s.getOutputStream();
        case "openDataOutputStream": return new DataOutputStream(s.getOutputStream());
        case "close": s.close(); return null;
        default: return null; } });
  }
}
