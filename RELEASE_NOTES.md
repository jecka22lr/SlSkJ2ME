Sharing actually works now, and you can see what uploads and browse requests are doing.

**New in Beta 0.4.2**
- Picking a folder or pressing Rescan turns "Share files" on. Before, it stayed on "Don't share" and Rescan silently did nothing
- Rescan stays on the sharing screen and shows "Sharing N files in N folders", or how many files were skipped and why
- Downloads that finish inside your shared folder are shared right away, no rescan needed
- Files the phone won't open one by one are sized through their folder instead of being skipped
- Browse/profile requests are no longer dropped when searches have filled the peer slots
- Log shows each step when someone browses you (connecting back, browsing, file list sent) and why it failed if it did
- Uploads get a step trace like downloads (Uploads > Details), and finished/failed uploads show in the Log
- About screen says Beta; the folder picker's "Use this folder" soft key no longer gets cut off

**About closed ports:** on mobile data or a hotspot the phone can't have an open port, so it can only connect with people whose port is open. If both of you have closed ports, Soulseek can't link you.

**Install:** copy `SlskME.jar` to the phone and open it (or use the `.jad` for OTA). Uninstall the previous version first.
