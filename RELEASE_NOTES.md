First release of SlskME, a Soulseek client for J2ME phones (CLDC 1.1 / MIDP 2.0 / JSR-75).

**Install:** copy `SlskME.jar` to the phone and open it (or use the `.jad` for OTA). The zip has both.

**What works:** login, search, downloads with resume and queue position, and NAT-friendly connections, so mobile data works with no port forwarding. It picks a writable download folder automatically.

**Not yet:** sharing/uploads, chat rooms, private messages, browsing users, wishlist. Since it shares nothing, some users will deny downloads.

Unsigned build: expect the phone to ask for network and file access. If downloads fail with a file permission error, open **Download folder** in the menu and pick one marked OK.
