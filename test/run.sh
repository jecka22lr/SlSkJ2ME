#!/bin/sh
# Desktop tests: MD5/inflate vs the JDK, folder fallback, and a full
# login -> search -> download -> resume run against a mock server + peer.
# Run ./build.sh first (uses build/classes). Needs a JDK and python3.
set -e
cd "$(dirname "$0")/.."
T=build/test
rm -rf $T /tmp/slskfs
mkdir -p $T/api $T/shim $T/cls
# API interfaces from the stub jar, minus the two classes the shim replaces
(cd $T/api && jar xf ../../../lib/j2me-api.jar javax && rm javax/microedition/io/Connector.class javax/microedition/io/file/FileSystemRegistry.class)
javac -nowarn -cp $T/api -d $T/shim $(find test/shim -name '*.java') 2>/dev/null
CP=build/classes:$T/shim:$T/api
javac -nowarn -cp $CP -d $T/cls test/src/*.java
java -cp $T/cls:$CP slsk.Tests
java -cp $T/cls:$CP slsk.FolderT
rm -rf /tmp/slskfs
python3 test/mock.py 25 > $T/mock.log 2>&1 &
sleep 1
java -cp $T/cls:$CP slsk.E2E | tee $T/e2e.log
cmp /tmp/expected.bin "/tmp/slskfs/E:/Soulseek/04 Hyper-ballad.mp3"
grep -q "TRANSFER Done" $T/e2e.log
# social + sharing: rooms, PMs, profiles, browse both ways, wishlist, distributed search, upload
mkdir -p "/tmp/slskfs/E:/Share/Album" "/tmp/slskfs/E:/Share/Misc"
head -c 200000 /dev/urandom > "/tmp/slskfs/E:/Share/Album/02 Song.mp3"
echo x > "/tmp/slskfs/E:/Share/Album/03 Other.ogg"
echo notes > "/tmp/slskfs/E:/Share/Misc/notes.txt"
python3 test/mock2.py 40 > $T/mock2.log 2>&1 &
M2=$!
sleep 1
java -cp $T/cls:$CP slsk.E2E2 | tee $T/e2e2.log
wait $M2 || { cat $T/mock2.log; exit 1; }
grep MOCK2 $T/mock2.log
echo "ALL TESTS PASSED"
