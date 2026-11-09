#!/bin/sh
# Screenshot tour of SlskME against ui-mock.py. Usage: tour.sh path/to/SlskME.jar outdir
set -e
JAR=$(readlink -f "$1"); OUT=${2:-shots}; mkdir -p "$OUT"; OUT=$(readlink -f "$OUT")
D="$(cd "$(dirname "$0")" && pwd)"; cd "$D"
pgrep -f ui-mock.py >/dev/null || (python3 ui-mock.py >/tmp/uimock.log 2>&1 &)
rm -rf home; mkdir home
./run-emu.sh "$JAR"; export DISPLAY=:99; setxkbmap us
E=./emu.py
$E shot $OUT/01-login.png
$E clear 20; $E type jecka; $E key Down; $E type pw; $E key Down 2; $E clear 25; $E type 127.0.0.1; $E key Down; $E clear 6; $E type 22620
$E soft right; sleep 3; $E shot $OUT/02-main.png
$E fire; sleep 1; $E type "tape moths"; $E soft right; sleep 5; $E shot $OUT/03-results.png
$E key Down 7; $E shot $OUT/04-results-scrolled.png
$E key Down 4; $E shot $OUT/05-results-more.png
$E soft left; sleep 0.5; $E shot $OUT/06-results-menu.png
$E key Escape; sleep 0.3
