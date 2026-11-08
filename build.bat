@echo off
rem Builds dist\SlskME.jar + dist\SlskME.jad on Windows.
rem Needs a JDK (java + jar on PATH), tools\ecj.jar and tools\proguard.jar
rem (or set ECJ_JAR / PROGUARD_JAR).
setlocal
cd /d "%~dp0"
set /p VERSION=<VERSION
if "%ECJ_JAR%"=="" set ECJ_JAR=tools\ecj.jar
if "%PROGUARD_JAR%"=="" set PROGUARD_JAR=tools\proguard.jar

if exist build rmdir /s /q build
if exist dist rmdir /s /q dist
mkdir build\classes build\raw dist

echo Compiling
java -jar "%ECJ_JAR%" -source 1.3 -target 1.1 -nowarn -bootclasspath lib\j2me-api.jar -d build\classes src\slsk || goto :fail

powershell -NoProfile -Command "(Get-Content res\MANIFEST.MF) -replace '@VERSION@','%VERSION%' | Set-Content -Encoding ASCII build\MANIFEST.MF"
xcopy /e /i /q build\classes\slsk build\raw\slsk >nul
copy res\icon.png build\raw\ >nul
pushd build\raw
jar cfm ..\raw.jar ..\MANIFEST.MF . || (popd & goto :fail)
popd

echo Preverifying
java -jar "%PROGUARD_JAR%" @proguard.pro || goto :fail

for %%A in (dist\SlskME.jar) do set SIZE=%%~zA
(
  echo MIDlet-1: SlskME, /icon.png, slsk.SlskMIDlet
  echo MIDlet-Name: SlskME
  echo MIDlet-Vendor: jecka22lr
  echo MIDlet-Version: %VERSION%
  echo MIDlet-Icon: /icon.png
  echo MIDlet-Description: Soulseek client for J2ME
  echo MIDlet-Jar-URL: SlskME.jar
  echo MIDlet-Jar-Size: %SIZE%
  echo MIDlet-Permissions-Opt: javax.microedition.io.Connector.socket, javax.microedition.io.Connector.serversocket, javax.microedition.io.Connector.file.read, javax.microedition.io.Connector.file.write
  echo MicroEdition-Configuration: CLDC-1.1
  echo MicroEdition-Profile: MIDP-2.0
) > dist\SlskME.jad
echo Done: dist\SlskME.jar (%SIZE% bytes)
exit /b 0
:fail
echo Build failed.
exit /b 1
