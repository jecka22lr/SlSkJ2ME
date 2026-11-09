# Mock server + 3 sharing peers for UI screenshots. Any login works.
import socket, struct, threading, zlib, time
SRV = 22620; PEERS = {'vinylhead': 22631, 'nokiafan': 22632, 'flacmonster': 22633}
LO = struct.pack('<I', 0x7F000001)
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
st = {}
LIB = {
 'vinylhead': (True, 380000, [('Music\\Tape Moths\\Glasshouse Weather (2003)', ['01 Low Orbit.mp3','02 Paper Lanterns.mp3','03 Salt Flats.mp3','04 The Long Hallway.mp3'], 320, 'mp3'),
                              ('Music\\Tape Moths\\Second Summer (2006)', ['01 Ferris Light.mp3','02 Rooftops.mp3'], 256, 'mp3')]),
 'nokiafan': (True, 95000, [('shared\\tapemoths\\singles', ['Copper Sky.mp3','Night Bus.mp3'], 192, 'mp3')]),
 'flacmonster': (False, 2100000, [('Lossless\\Tape Moths\\2003 - Glasshouse Weather [FLAC]', ['01. Low Orbit.flac','02. Paper Lanterns.flac','03. Salt Flats.flac'], 0, 'flac')]),
}
def peer(name):
    def handle(c):
        rx(c, 4); rx(c, 5)  # pierce frame (len + code + token)
        slot, speed, dirs = LIB[name]
        files = b''; n = 0
        for d, fs, br, ext in dirs:
            for i, f in enumerate(fs):
                attrs = struct.pack('<III', 2, 0, br) + struct.pack('<II', 1, 200 + 17 * i) if br else struct.pack('<I', 1) + struct.pack('<II', 1, 260 + 9 * i)
                files += b'\x01' + s(d + '\\' + f) + struct.pack('<Q', (9 if ext == 'mp3' else 31) * 1000000 + i * 123456) + s(ext) + attrs
                n += 1
        body = s(name) + struct.pack('<II', st['tok'], n) + files + (b'\x01' if slot else b'\x00') + struct.pack('<IIII', speed, 0 if slot else 14, 0, 0)
        c.sendall(frame(9, zlib.compress(body)))
        try:
            while True: rmsg(c)
        except EOFError: pass
    ls = socket.socket(); ls.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1); ls.bind(('127.0.0.1', PEERS[name])); ls.listen()
    def loop():
        while True:
            c, _ = ls.accept(); threading.Thread(target=handle, args=(c,), daemon=True).start()
    threading.Thread(target=loop, daemon=True).start()
def server(c):
    while True:
        code, p = rmsg(c)
        if code == 1:
            c.sendall(frame(1, b'\x01' + s('Welcome to the mock server') + LO + s('x') + b'\x00'))
        elif code == 26:
            st['tok'], = struct.unpack_from('<I', p)
            for i, (name, port) in enumerate(PEERS.items()):
                time.sleep(0.4)
                c.sendall(frame(18, s(name) + s('P') + LO + struct.pack('<III', port, 900 + i, 0) + b'\x00'))
for n in PEERS: peer(n)
ls = socket.socket(); ls.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1); ls.bind(('127.0.0.1', SRV)); ls.listen()
while True:
    c, _ = ls.accept(); threading.Thread(target=server, args=(c,), daemon=True).start()
