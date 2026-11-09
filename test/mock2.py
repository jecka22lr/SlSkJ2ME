# Mock server + peers for social/sharing features. Prints CHECK lines; exits 0 only if all pass.
import socket, struct, threading, zlib, sys, time
SRV, PEER, PARENT, SEARCHER = 22520, 22530, 22540, 22550
LO = struct.pack('<I', 0x7F000001)
checks = {}
def ok(name, cond=True):
    if cond and name not in checks:
        checks[name] = True; print('CHECK ok', name, flush=True)
def s(x): b = x.encode(); return struct.pack('<I', len(b)) + b
def frame(code, p): return struct.pack('<II', len(p) + 4, code) + p
def rx(c, n):
    b = b''
    while len(b) < n:
        k = c.recv(n - len(b))
        if not k: raise EOFError
        b += k
    return b
def rmsg(c):
    l, = struct.unpack('<I', rx(c, 4)); b = rx(c, l); return struct.unpack('<I', b[:4])[0], b[4:]
def rinit(c):
    l, = struct.unpack('<I', rx(c, 4)); b = rx(c, l); return b[0], b[1:]
def rstr(b, o):
    l, = struct.unpack_from('<I', b, o); return b[o+4:o+4+l].decode('utf-8', 'replace'), o + 4 + l
SHARED = open('/tmp/slskfs/E:/Share/Album/02 Song.mp3', 'rb').read()
st = {}
def listen(port, fn):
    ls = socket.socket(); ls.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1); ls.bind(('127.0.0.1', port)); ls.listen()
    def loop():
        while True:
            c, _ = ls.accept(); threading.Thread(target=fn, args=(c,), daemon=True).start()
    threading.Thread(target=loop, daemon=True).start()
ADDR = {'peer1': PEER, 'searcher': SEARCHER, 'parent': PARENT}

def server(c):
    st['client'] = c
    while True:
        code, p = rmsg(c)
        if code == 1:
            c.sendall(frame(1, b'\x01' + s('hi') + LO + s('x') + b'\x00'))
            if st.get('dropped'): ok('reconnect_login'); continue
            c.sendall(frame(64, struct.pack('<I', 3) + s('nokia') + s('j2me') + s('music') + struct.pack('<IIII', 3, 12, 40, 7)))
            c.sendall(frame(104, struct.pack('<I', 2)))   # wishlist every 2 s (client clamps to 60, first runs now)
            c.sendall(frame(22, struct.pack('<II', 42, 0) + s('peer1') + s('hey, got any symbian stuff?') + b'\x01'))
        elif code == 23:
            ok('pm_acked', struct.unpack_from('<I', p)[0] == 42)
        elif code == 35:
            d, f = struct.unpack('<II', p)
            print('SERVER shares', d, f, flush=True)
            if f > 0:
                ok('share_counts', (d, f) == (2, 3))
                c.sendall(frame(102, struct.pack('<I', 1) + s('parent') + LO + struct.pack('<I', PARENT)))
        elif code == 14:
            room, o = rstr(p, 0)
            if room == 'secret':
                ok('priv_create', len(p) >= o + 4 and struct.unpack_from('<I', p, o)[0] == 1)
                c.sendall(frame(14, s(room) + struct.pack('<I', 1) + s('jecka')))
                c.sendall(frame(133, s(room) + struct.pack('<I', 1) + s('jecka')))
                continue
            if st.get('dropped'): ok('reconnect_rejoin', room == 'nokia')
            c.sendall(frame(14, s(room) + struct.pack('<I', 2) + s('peer1') + s('jecka')))
            c.sendall(frame(13, s(room) + s('spammer') + s('buy my stuff')))  # ignored by the client
            c.sendall(frame(13, s(room) + s('peer1') + s('welcome to ' + room)))
        elif code == 13:
            room, o = rstr(p, 0); msg, _ = rstr(p, o)
            if msg == 'drop me':
                st['dropped'] = True; print('SERVER dropping client', flush=True); c.close(); return
            ok('room_say', msg == 'hello room')
            c.sendall(frame(13, s(room) + s('jecka') + s(msg)))
        elif code == 22:
            u, o = rstr(p, 0); msg, _ = rstr(p, o); ok('pm_sent', u == 'peer1' and msg == 'yes, j2me stuff')
        elif code == 103:
            tok, = struct.unpack_from('<I', p); q, _ = rstr(p, 4)
            ok('wishlist_search', q == 'rare album'); st['wtok'] = tok
            c.sendall(frame(18, s('peer1') + s('P') + LO + struct.pack('<III', PEER, 901, 0) + b'\x00'))
        elif code == 5:
            u, _ = rstr(p, 0)
            c.sendall(frame(5, s(u) + b'\x01' + struct.pack('<IIQII', 2, 5000, 10, 1234, 56) + s('PT')))
            if u == 'buddy1' and not st.get('buddy_sent'):
                st['buddy_sent'] = True; ok('watch_buddy')
                def later(cc=c):
                    time.sleep(1.0); cc.sendall(frame(7, s('buddy1') + struct.pack('<I', 0) + b'\x00'))  # goes offline
                threading.Thread(target=later, daemon=True).start()
        elif code == 51:
            item, _ = rstr(p, 0); ok('like_sent', item == 'tape moths')
        elif code == 141:
            ok('priv_invites_on', p == b'\x01')
        elif code == 54:
            c.sendall(frame(54, struct.pack('<I', 2) + s('shoegaze') + struct.pack('<i', 12) + s('dream pop') + struct.pack('<i', 7) + struct.pack('<I', 0)))
        elif code == 110:
            c.sendall(frame(110, struct.pack('<I', 1) + s('peer1') + struct.pack('<I', 3)))
        elif code == 112:
            item, _ = rstr(p, 0)
            c.sendall(frame(112, s(item) + struct.pack('<I', 2) + s('peer1') + s('nokiafan')))
        elif code == 57:
            u, _ = rstr(p, 0)
            c.sendall(frame(57, s(u) + struct.pack('<I', 2) + s('nokias') + s('symbian') + struct.pack('<I', 1) + s('ads')))
        elif code == 64:
            # public rooms, then private rooms we own, then private rooms we're a member of
            c.sendall(frame(64, struct.pack('<I', 1) + s('nokia') + struct.pack('<II', 1, 12)
                + struct.pack('<I', 1) + s('secret') + struct.pack('<II', 1, 1)
                + struct.pack('<I', 1) + s('friends') + struct.pack('<II', 1, 4)
                + struct.pack('<I', 0)))
        elif code == 134:
            room, o = rstr(p, 0); u, _ = rstr(p, o); ok('priv_invite', room == 'secret' and u == 'peer1')
            c.sendall(frame(139, s('friends')))
        elif code == 120:
            room, o = rstr(p, 0); tok, = struct.unpack_from('<I', p, o); q, _ = rstr(p, o + 4)
            ok('room_search', room == 'nokia' and q == 'tape')
        elif code == 42:
            u, o = rstr(p, 0); tok, = struct.unpack_from('<I', p, o); q, _ = rstr(p, o + 4)
            if u == 'buddy1': ok('buddy_search', q == 'demo')
        elif code == 3:
            u, _ = rstr(p, 0)
            c.sendall(frame(3, s(u) + LO + struct.pack('<I', ADDR.get(u, 0))))
        elif code in (71, 126, 127):
            print('SERVER distrib code', code, p[:20], flush=True)
            if code == 71 and p == b'\x00': ok('parent_joined')

def peer1(c):  # remote user: PMs, profile, browse, wishlist hit
    code, b = rinit(c)
    if code == 0:
        tok, = struct.unpack_from('<I', b)
        if tok == 901:
            body = s('peer1') + struct.pack('<II', st['wtok'], 1) + b'\x01' + s('M\\rare album\\01.mp3') + struct.pack('<Q', 5) + s('') + struct.pack('<I', 0) + b'\x01' + struct.pack('<IIII', 0, 0, 0, 0)
            c.sendall(frame(9, zlib.compress(body)))
    try:
        while True:
            code, p = rmsg(c)
            if code == 15:
                c.sendall(frame(16, s('I collect old Nokias') + b'\x00' + struct.pack('<II', 99, 2) + b'\x01' + struct.pack('<I', 1)))
            elif code == 4:
                files = b''.join(b'\x01' + s('%02d track.mp3' % i) + struct.pack('<Q', 1000 + i) + s('mp3') + struct.pack('<I', 1) + struct.pack('<II', 0, 192) for i in range(3))
                body = struct.pack('<I', 2) + s('Music\\Empty') + struct.pack('<I', 0) + s('Music\\Album') + struct.pack('<I', 3) + files + struct.pack('<II', 0, 0)
                c.sendall(frame(5, zlib.compress(body)))
    except EOFError:
        pass

def parent(c):  # distributed parent: sends one search that matches our share
    code, b = rinit(c)
    u, o = rstr(b, 0); t, _ = rstr(b, o)
    ok('distrib_init', t == 'D')
    c.sendall(struct.pack('<IB', 5, 4) + struct.pack('<I', 2))  # BranchLevel 2
    q = struct.pack('<I', 0) + s('searcher') + struct.pack('<I', 777) + s('song -karaoke')
    c.sendall(struct.pack('<IB', len(q) + 1, 3) + q)
    try:
        while True: rx(c, 1)
    except EOFError: pass

def searcher(c):  # stranger: receives our search result, browses us, asks profile, downloads from us
    code, b = rinit(c)
    u, o = rstr(b, 0); t, _ = rstr(b, o)
    if t == 'F':
        tok, = struct.unpack('<I', rx(c, 4)); ok('upload_token', tok == st.get('utok'))
        c.sendall(struct.pack('<Q', 0))
        data = b''
        while len(data) < len(SHARED):
            k = c.recv(65536)
            if not k: break
            data += k
        ok('upload_data', data == SHARED); c.close()
        # by now the client has banned us: asking again and browsing must be refused
        time.sleep(1.5)
        pc = st.get('spconn')
        if pc:
            pc.sendall(frame(43, s(st['sname'])))
            pc.sendall(frame(4, b''))
        return
    st['spconn'] = c
    while True:
        code, p = rmsg(c)
        if code == 50:
            fn, o = rstr(p, 0); why, _ = rstr(p, o); ok('ban_denies_download', why == 'Banned')
        elif code == 9:
            body = zlib.decompress(p)
            un, o = rstr(body, 0); tok, n = struct.unpack_from('<II', body, o); o += 8
            names = []
            for i in range(n):
                o += 1; fn, o = rstr(body, o); size, = struct.unpack_from('<Q', body, o); o += 8; _, o = rstr(body, o); na, = struct.unpack_from('<I', body, o); o += 4 + 8 * na
                names.append(fn)
            print('SEARCHER got', names, flush=True)
            ok('search_reply', un == 'jecka' and tok == 777 and names == ['Share\\Album\\02 Song.mp3'])
            c.sendall(frame(4, b''))
            c.sendall(frame(15, b''))
            st['sname'] = names[0]
            c.sendall(frame(43, s(names[0])))
        elif code == 5:
            body = zlib.decompress(p); nd, = struct.unpack_from('<I', body)
            d1, _ = rstr(body, 4)
            if st.get('browsed'): ok('ban_empty_browse', nd == 0)
            else: ok('browse_us', nd == 2 and d1.startswith('Share'))
            st['browsed'] = True; print('SEARCHER browse dirs', nd, flush=True)
        elif code == 16:
            desc, _ = rstr(p, 0); ok('our_profile', desc == 'phone gang')
        elif code == 40:
            d, tok = struct.unpack_from('<II', p); fn, o = rstr(p, 8); size, = struct.unpack_from('<Q', p, o)
            ok('transfer_request', d == 1 and size == len(SHARED)); st['utok'] = tok
            c.sendall(frame(41, struct.pack('<I', tok) + b'\x01'))

listen(PEER, peer1); listen(PARENT, parent); listen(SEARCHER, searcher)
ls = socket.socket(); ls.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1); ls.bind(('127.0.0.1', SRV)); ls.listen()
def accept_loop():
    while True:
        cc = ls.accept()[0]
        threading.Thread(target=server, args=(cc,), daemon=True).start()
threading.Thread(target=accept_loop, daemon=True).start()
need = ['like_sent','priv_invites_on','priv_create','priv_invite','watch_buddy','room_search','buddy_search','ban_denies_download','ban_empty_browse','reconnect_login','reconnect_rejoin','pm_acked','share_counts','room_say','pm_sent','wishlist_search','parent_joined','distrib_init','search_reply','browse_us','our_profile','transfer_request','upload_token','upload_data']
t0 = time.time()
while time.time() - t0 < float(sys.argv[1]) and not all(k in checks for k in need): time.sleep(0.2)
missing = [k for k in need if k not in checks]
print('MOCK2', 'ALL OK' if not missing else 'MISSING ' + ' '.join(missing), flush=True)
sys.exit(1 if missing else 0)
