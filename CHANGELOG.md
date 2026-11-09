# Changelog

## Alpha 0.2.1

- Search keeps at most 25 results per user, so a few users with big lossless folders
  can't fill the list and crowd out everyone else (total cap raised to 600)
- Results "Filter": all / MP3 only / FLAC only / lossy / lossless / free slots only.
  Title shows shown/total when a filter is on.

## Alpha 0.2.0

- Sharing: pick a folder on the phone, it gets scanned and reported to the server
- Uploads: people can queue and download your shared files (upload slots configurable)
- Joins the distributed search network, so other users' searches find your files
- Others can browse your shares and read your profile
- Chat rooms: room list (biggest first), join by name, read/write, user list, leave
- Private messages: conversations list, new message, unread counts, vibrate + ticker on arrival
- User menu (from results, rooms, messages or Find user): profile with picture, browse files,
  search their files, send message
- Browse other users' shares by folder, download single files or whole folders
- Wishlist: saved searches run on the server's schedule, hits get their own results list
- Profile & sharing screen: profile text, online/away, shared folder, upload slots

## Alpha 0.1.4

- About screen (main menu, or the login screen's menu): version, author, source link, license,
  protocol credits, phone platform, free memory and the current download folder

## Alpha 0.1.3 (first release)

- Login to server.slsknet.org (new names register automatically), cancel while connecting
- Search with results streamed in, free-slot results first, bitrate/length/queue info
- Downloads with resume, queue position, retry, per-download step trace for debugging
- Works behind mobile NAT with no open port (firewall piercing), optional listen port
- Finds a folder the phone allows writing to; "Download folder" screen tests every candidate
- Downloads reachable from every screen; results marked [DL] / [OK] / [X]
- Closes idle peer connections so phones don't run out of sockets
- Replies to share list / user info requests (shares nothing)
