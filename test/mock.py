# Mock Soulseek server + one peer ("peer1"), enough to drive login/search/download.
import socket, struct, threading, zlib, hashlib, sys, time
SRV, PEER = 22420, 22430
LATE = len(sys.argv) > 2
FILE = '@@abcd\\Music\\Björk\\Post\\04 Hyper-ballad.mp3'
DATA = bytes((i * 7 + i // 300) & 255 for i in range(300000))
state = {'client': None, 'token': None}
def s(x): b = x.encode(); return struct.pack('<I', len(b)) + b
def frame(code, p): return struct.pack('<II', len(p) + 4, code) + p
def initf(code, p): return struct.pack('<IB', len(p) + 1, code) + p
def rx(c, n):
    b = b''
    while len(b) < n:
        k = c.recv(n - len(b))
        if not k: raise EOFError
        b += k
    return b
def rmsg(c):
    l, = struct.unpack('<I', rx(c, 4)); b = rx(c, l); return struct.unpack('<I', b[:4])[0], b[4:]
def rstr(b, o):
    l, = struct.unpack_from('<I', b, o); return b[o+4:o+4+l].decode(), o + 4 + l
LO = struct.pack('<I', 0x7F000001)
def server():
    ls = socket.socket(); ls.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1); ls.bind(('127.0.0.1', SRV)); ls.listen()
    c, _ = ls.accept(); state['client'] = c
    while True:
        code, p = rmsg(c)
        if code == 1:
            u, o = rstr(p, 0); pw, o = rstr(p, o); ver, = struct.unpack_from('<I', p, o); h, o = rstr(p, o + 4)
            assert h == hashlib.md5((u + pw).encode()).hexdigest(), 'bad hash'
            print('SERVER login', u, 'ver', ver, 'hash ok', flush=True)
            c.sendall(frame(1, b'\x01' + s('Welcome to mock Soulseek') + LO + s(hashlib.md5(pw.encode()).hexdigest()) + b'\x00'))
        elif code == 26:
            tok, = struct.unpack_from('<I', p, 0); q, _ = rstr(p, 4); state['token'] = tok
            print('SERVER search', repr(q), 'token', tok, flush=True)
            c.sendall(frame(18, s('peer1') + s('P') + LO + struct.pack('<III', PEER, 777, 0) + b'\x00'))
        elif code == 3:
            u, _ = rstr(p, 0); print('SERVER GetPeerAddress', u, flush=True)
            c.sendall(frame(3, s(u) + LO + struct.pack('<I', PEER)))
        else:
            print('SERVER got code', code, flush=True)
def peer_conn(c):
    l, = struct.unpack('<I', rx(c, 4)); b = rx(c, l)
    if b[0] == 0:
        tok, = struct.unpack_from('<I', b, 1); print('PEER pierce token', tok, flush=True)
        if tok == 777:
            files = b''
            for i, name in enumerate([FILE, '@@abcd\\Music\\Björk\\Post\\05 Enjoy.mp3']):
                files += b'\x01' + s(name) + struct.pack('<Q', len(DATA) if i == 0 else 9000000) + s('') + struct.pack('<IIIII', 2, 0, 320, 1, 211)
            body = s('peer1') + struct.pack('<II', state['token'], 2) + files + b'\x01' + struct.pack('<III', 150000, 0, 0) + struct.pack('<I', 0)
            c.sendall(frame(9, zlib.compress(body)))
            # also send a response for a stale token: must be ignored
            c.sendall(frame(9, zlib.compress(s('peer1') + struct.pack('<II', 1, 1) + b'\x01' + s('stale.mp3') + struct.pack('<Q', 1) + s('') + struct.pack('<I', 0) + b'\x01' + struct.pack('<III', 0, 0, 0))))
            peer_msgs(c)
        elif tok == 999:
            c.sendall(frame(40, struct.pack('<II', 1, 555) + s(state['file']) + struct.pack('<Q', len(DATA))))
            peer_msgs(c)
        elif tok == 888:
            c.sendall(struct.pack('<I', 555))
            off, = struct.unpack('<Q', rx(c, 8)); print('PEER F offset', off, flush=True)
            c.sendall(DATA[off:]); time.sleep(0.5); c.close()
    elif b[0] == 1:
        u, o = rstr(b, 1); t, _ = rstr(b, o); print('PEER PeerInit from', u, t, flush=True)
        peer_msgs(c)
def peer_msgs(c):
    try:
        while True:
            code, p = rmsg(c)
            if code == 43:
                f, _ = rstr(p, 0); print('PEER QueueUpload', f, flush=True)
                c.sendall(frame(44, s(f) + struct.pack('<I', 3)))
                if LATE:
                    state['file'] = f
                    def later():
                        time.sleep(28); print('PEER reconnecting late via pierce 999', flush=True)
                        state['client'].sendall(frame(18, s('peer1') + s('P') + LO + struct.pack('<III', PEER, 999, 0) + b'\x00'))
                    threading.Thread(target=later, daemon=True).start()
                    continue
                time.sleep(0.3)
                c.sendall(frame(40, struct.pack('<II', 1, 555) + s(f) + struct.pack('<Q', len(DATA))))
            elif code == 41:
                tok, = struct.unpack_from('<I', p, 0); print('PEER TransferResponse', tok, 'allowed', p[4], flush=True)
                state['client'].sendall(frame(18, s('peer1') + s('F') + LO + struct.pack('<III', PEER, 888, 0) + b'\x00'))
            else:
                print('PEER got code', code, flush=True)
    except EOFError:
        pass
def peer():
    ls = socket.socket(); ls.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1); ls.bind(('127.0.0.1', PEER)); ls.listen()
    while True:
        c, _ = ls.accept(); threading.Thread(target=peer_conn, args=(c,), daemon=True).start()
threading.Thread(target=server, daemon=True).start()
threading.Thread(target=peer, daemon=True).start()
open('/tmp/expected.bin', 'wb').write(DATA)
time.sleep(float(sys.argv[1]) if len(sys.argv) > 1 else 30)
