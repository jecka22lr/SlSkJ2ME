# Changelog

## Beta 0.5.0

- Buddies (main menu): add people, see who's online, get a ticker + vibrate when a buddy comes
  online or goes offline. Buddies go to the front of your upload queue. Search all buddies' files at once
- Ignore hides someone's room lines and private messages. Ban refuses their downloads ("Banned"),
  gives them an empty file list and no search results. Both from the user menu; undo under Buddies > Blocked
- Search: Recent searches (Search > History), Search this room (in a chat room), and More filters
  (Results > Filter > More): minimum bitrate, minimum size, words to hide. Filters are remembered
- Whole-folder downloads go into a subfolder named after the folder
- Unfinished downloads are saved as "name.part" and renamed when done; if the name is taken the file
  becomes "name (2).ext". Downloads started by older versions still resume
- Failed downloads retry on their own: after 2, 4, 8, 16, then every 30 minutes, up to 20 times
- My downloads > Play opens a finished file in the phone's own player
- Profile & sharing: max files per person (default 50), upload and download speed limits in KB/s.
  Uploads go one file at a time per person so one big queue can't hold every slot
- The share scan is saved, so logging in doesn't walk the whole shared folder again (Rescan refreshes it).
  Finished downloads keep it up to date
- Filenames that aren't valid UTF-8 (older Windows clients) are shown as Latin-1 and the original
  bytes are sent back, so those files download instead of failing with "File not shared"

## Beta 0.4.2

- Uploads keep a step-by-step trace like downloads (Uploads > Details > Steps): queued, offered,
  accepted, how the data connection was made or why it failed, resume offset, file opened, done.
  Finished and failed uploads also show up in the Log
- Sharing screen: picking a folder or pressing Rescan turns "Share files" on (it used to stay on
  "Don't share", so Rescan silently did nothing)
- Save/Rescan stays on the sharing screen and shows the result under "Now sharing", including
  how many files were skipped and why, instead of jumping back to the main menu
- Files the phone won't open one by one are sized through the folder's own connection instead
  of being skipped
- Finished downloads saved inside the shared folder are shared right away, no rescan needed
- When someone's browse/profile request can't reach the phone, the Log says so and the server is
  told right away (CantConnectToPeer) instead of leaving them waiting
- Browse/profile requests are no longer silently dropped when searches have filled the peer slots
- About screen says Beta; folder picker's "Use this folder" soft key no longer gets cut off

## Beta 0.4.1

- Commands have short soft-key labels (Get, Open, Downloads, Next...), so they no longer get
  cut off on narrow screens; the menu still shows the full names
- Next/Previous page only appear when there's more than one page
- Emulator screenshot testing: `test/emu/` builds MicroEmulator from source and drives the real
  jar against a mock server on a virtual display

## Beta 0.4.0

- Search results regrouped like desktop clients: user (free slot / queue, speed),
  then each folder, then its files with size, bitrate and length
- Small icons for user, folder and file rows
- Select a user row to open their user menu, a folder row to download the whole folder
  (asks first), a file row to download it; Details works on folders too
- Free-slot users first, then fastest; paging repeats the user/folder header when a page
  starts mid-group (80 rows per page)

## Beta 0.3.2

- Testing a save location no longer leaves empty "Soulseek" folders behind
  (older versions created them in every folder they tried, including app-private storage)
- Folder picker lists real drives (C:/, E:/) first; extra named roots some phones add are marked "(shortcut)"
- Private folders are labelled "app storage, hidden" and are never picked automatically

## Beta 0.3.1

- Folder picker: browse drives and folders, go up, create a new folder.
  Used by **Profile & sharing > Pick shared folder** and **Save location > Browse folders**
  (save locations are write-tested before they're accepted)

## Beta 0.3.0

- Auto-reconnect: a dropped connection retries by itself (5 s, doubling up to 2 min),
  rejoins your chat rooms and re-sends queued download requests
- Download list survives restarts; unfinished downloads re-queue and resume after login
- Exit asks first while downloads or uploads are running
- Memory card found via the phone's own setting; folders shown as "Memory card/...",
  "Phone memory/...", "Music/..." instead of raw paths
- Results list says "Searching... N s", then "No results yet (still listening)",
  instead of the phone's misleading "No results"
- Clearer names: "Download file" vs "My downloads", "Save location" vs "Download whole folder"
- Nokia JAD attributes (application category, no on-screen keypad on touch S60)

## Beta 0.2.2

- Search results are paged, 100 per page (Next page / Previous page). Title shows p2/7.
- Keeps up to 2000 results per search, or fewer if the phone's memory runs low
- Results list repaints in batches once a search is busy, so the UI stays responsive

## Beta 0.2.1

- Search keeps at most 25 results per user, so a few users with big lossless folders
  can't fill the list and crowd out everyone else (total cap raised to 600)
- Results "Filter": all / MP3 only / FLAC only / lossy / lossless / free slots only.
  Title shows shown/total when a filter is on.

## Beta 0.2.0

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

## Beta 0.1.4

- About screen (main menu, or the login screen's menu): version, author, source link, license,
  protocol credits, phone platform, free memory and the current download folder

## Beta 0.1.3 (first release)

- Login to server.slsknet.org (new names register automatically), cancel while connecting
- Search with results streamed in, free-slot results first, bitrate/length/queue info
- Downloads with resume, queue position, retry, per-download step trace for debugging
- Works behind mobile NAT with no open port (firewall piercing), optional listen port
- Finds a folder the phone allows writing to; "Download folder" screen tests every candidate
- Downloads reachable from every screen; results marked [DL] / [OK] / [X]
- Closes idle peer connections so phones don't run out of sockets
- Replies to share list / user info requests (shares nothing)
