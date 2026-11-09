# Emulator screenshots

Runs the real `dist/SlskME.jar` in MicroEmulator on a virtual X display and drives it with xdotool,
against `ui-mock.py` (a mock server plus three sharing peers with made-up music).

    sudo apt-get install -y xvfb xdotool imagemagick libasm-java openbox x11-xkb-utils
    test/emu/setup-emu.sh                 # once: clones and builds MicroEmulator
    test/emu/tour.sh dist/SlskME.jar shots   # login, search, results, menus -> shots/*.png

`emu.py` has the primitives (type, key, soft left/right, fire, shot) for writing new tours.
