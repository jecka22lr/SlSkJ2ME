#!/bin/sh
# Builds dist/SlskME.jar + dist/SlskME.jad.
# Needs a JDK (java + jar), ECJ and ProGuard 7. Override paths with ECJ / PROGUARD_JAR.
set -e
cd "$(dirname "$0")"
VERSION=$(tr -d '\r\n' < VERSION)
ECJ=${ECJ:-"java -jar tools/ecj.jar"}
PROGUARD_JAR=${PROGUARD_JAR:-tools/proguard.jar}

rm -rf build dist
mkdir -p build/classes build/raw dist

echo "Compiling (Java 1.3 bytecode against CLDC 1.1 / MIDP 2.0 / JSR-75)"
$ECJ -source 1.3 -target 1.1 -nowarn -bootclasspath lib/j2me-api.jar -d build/classes src/slsk/*.java

sed "s/@VERSION@/$VERSION/" res/MANIFEST.MF > build/MANIFEST.MF
cp -r build/classes/slsk build/raw/
cp res/icon.png build/raw/
(cd build/raw && jar cfm ../raw.jar ../MANIFEST.MF .)

echo "Preverifying with ProGuard"
java -jar "$PROGUARD_JAR" @proguard.pro

SIZE=$(wc -c < dist/SlskME.jar | tr -d ' ')
{
  grep -v -e '^Manifest-Version' -e '^Created-By' -e '^ ' build/MANIFEST.MF | grep -v '^MIDlet-Permissions-Opt'
  echo "MIDlet-Permissions-Opt: javax.microedition.io.Connector.socket, javax.microedition.io.Connector.serversocket, javax.microedition.io.Connector.file.read, javax.microedition.io.Connector.file.write"
  echo "MIDlet-Jar-URL: SlskME.jar"
  echo "MIDlet-Jar-Size: $SIZE"
} | grep -v '^$' > dist/SlskME.jad

(cd dist && rm -f SlskME-$VERSION.zip && zip -q SlskME-$VERSION.zip SlskME.jar SlskME.jad) 2>/dev/null || true
echo "Done: dist/SlskME.jar ($SIZE bytes), dist/SlskME.jad"
