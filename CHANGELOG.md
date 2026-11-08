# Changelog

## 0.1.3 (first release)

- Login to server.slsknet.org (new names register automatically), cancel while connecting
- Search with results streamed in, free-slot results first, bitrate/length/queue info
- Downloads with resume, queue position, retry, per-download step trace for debugging
- Works behind mobile NAT with no open port (firewall piercing), optional listen port
- Finds a folder the phone allows writing to; "Download folder" screen tests every candidate
- Downloads reachable from every screen; results marked [DL] / [OK] / [X]
- Closes idle peer connections so phones don't run out of sockets
- Replies to share list / user info requests (shares nothing)
