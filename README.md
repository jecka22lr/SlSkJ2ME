# SlskME

A Soulseek client for old Java phones. J2ME (CLDC 1.1 / MIDP 2.0 + JSR-75 FileConnection),
no proxy server, talks to the real Soulseek network directly.

Aimed at Nokia S60v3/v5 (N82, C6-00...), Nokia S40 with JSR-75, and Windows Mobile 6 Java runtimes.
The jar is about 38 KB. I have 0 fucking clue if it works elsewhere, we will see about that later.

## Features

- Log in to `server.slsknet.org:2242` (unknown names are registered on first login like usual)
- Search, results arrive live, free slots sorted first, bitrate/length/queue/speed in Details
- Download to the memory card, resume after a dropped connection, queue position, retry
- Works on mobile data with no open port: peers are reached through the server's firewall-piercing
  messages, and the connection a result arrived on is reused to queue the download
- Optional listen port for when you do have one (Wi-Fi with port forwarding)
- Finds a folder the phone lets an unsigned app write to; **Download folder** in the menu tests them all
- Downloads list reachable from any screen, results marked `[DL]` queued, `[OK]` done, `[X]` failed
- Each download keeps a step trace (open Details) so failures say exactly where they broke
- About screen with version, source link, phone platform and free memory (useful for bug reports)

- Sharing and uploads: share a folder from the phone, people can browse, search and download it
- Joins the distributed search network so your shares turn up in other people's searches
- Chat rooms: room list, join, chat, user list
- Private messages with vibrate/ticker notifications
- User profiles (with picture), browse anyone's files, search one user's files, download whole folders
- Wishlist: saved searches that run on the server's schedule
- Profile text and online/away status

Uploads only reach people with an open port when you're on mobile data (you can't accept incoming
connections behind carrier NAT). Big share lists may not fit in phone memory when browsing.

## Install

Copy `SlskME.jar` to the phone and open it, or serve `SlskME.jad` + jar over HTTP for OTA install.
It's unsigned, so the phone will ask for network and file access. If your phone has app permission
settings, set network and user data access to the most permissive option offered.

## Build

Needs a JDK (8 or newer), [ECJ](https://repo1.maven.org/maven2/org/eclipse/jdt/ecj/3.32.0/) 3.32.0
(newer ECJ versions may refuse `-source 1.3`) and [ProGuard](https://github.com/Guardsquare/proguard/releases) 7.4.2.
Put them at `tools/ecj.jar` and `tools/proguard.jar`, then:

    build.bat        (Windows)
    ./build.sh       (Linux/macOS)

Output lands in `dist/`. javac can't be used: it won't emit Java 1.3 bytecode and its string
concatenation needs `StringBuilder`, which CLDC doesn't have. ProGuard's `-microedition` mode
adds the StackMap attributes CLDC needs, replacing the old WTK preverifier.

## Tests

    ./test/run.sh

Runs on a desktop JVM with a small shim standing in for the phone's sockets and filesystem:
MD5 and the zlib decoder against the JDK, the writable-folder fallback, and a full
login, search, download and resume against `test/mock.py` (a mock server plus one peer), and
`test/mock2.py` for rooms, PMs, profiles, browsing both ways, wishlist, distributed search and uploads.

## Layout

| File | What it does |
| --- | --- |
| `Client.java` | Protocol core: server connection, search, peers, transfers, chat, uploads. No UI code. |
| `Shares.java` | Shared file index: scan, search matching, share list / search responses |
| `SocialUI.java` | Rooms, messages, users, browse, wishlist, profile & sharing, uploads screens |
| `PeerConn.java` | Peer connections: PierceFirewall / PeerInit, messages ("P") and file data ("F") |
| `Inflate.java` | zlib decoder (search results are compressed, CLDC has no java.util.zip) |
| `MD5.java` | Login hash |
| `Store.java` | JSR-75 file writing, resume, writable-folder search |
| `SlskMIDlet.java` | LCDUI front end, settings in RMS |
| `Msg.java`, `Rd.java`, `Util.java` | Little-endian message building/parsing, helpers |

Protocol reference: the Nicotine+ project's Soulseek protocol documentation.

## License

MIT
