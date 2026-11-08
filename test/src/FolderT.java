package slsk;
public class FolderT { public static void main(String[] a) {
  System.setProperty("fileconn.dir.music", "file:///DENY/Music/");
  System.setProperty("fileconn.dir.photos", "file:///E:/Images/");
  System.out.println(Store.candidates("file:///DENY/Soulseek/"));
  System.out.println("probe denied: " + Store.probe("file:///DENY/Soulseek/"));
  System.out.println("writable: " + Store.findWritable("file:///DENY/Soulseek/"));
}}
