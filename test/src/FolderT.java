package slsk;
public class FolderT { public static void main(String[] a) {
  System.setProperty("fileconn.dir.music", "file:///DENY/Music/");
  System.setProperty("fileconn.dir.photos", "file:///E:/Images/");
  System.out.println(Store.candidates("file:///DENY/Soulseek/"));
  System.out.println("probe denied: " + Store.probe("file:///DENY/Soulseek/"));
  System.out.println("writable: " + Store.findWritable("file:///DENY/Soulseek/"));
  System.setProperty("fileconn.dir.memorycard", "file:///E:/");
  System.out.println("default: " + Store.defaultDir() + " = " + Store.friendly(Store.defaultDir()));
  System.out.println("friendly: " + Store.friendly("file:///C:/Data/Sounds/My%20Music/") + " | " + Store.friendly("file:///E:/Images/Soulseek/"));
  if (!Store.friendly(Store.defaultDir()).equals("Memory card/Soulseek/")) throw new Error("friendly");
}}
