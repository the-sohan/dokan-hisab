#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT to the Android SDK directory}"
: "${JAVA_HOME:?Set JAVA_HOME to JDK 17 or newer}"
BT="$ANDROID_SDK_ROOT/build-tools/35.0.0"
API="$ANDROID_SDK_ROOT/platforms/android-35/android.jar"
rm -rf build/classes build/generated build/dex
mkdir -p build/classes build/generated build/dex build/keys
"$BT/aapt2" compile --dir res -o build/resources.zip
"$BT/aapt2" link -o build/resources.apk --manifest AndroidManifest.xml -I "$API" --java build/generated build/resources.zip
find src build/generated -name '*.java' > build/sources.txt
"$JAVA_HOME/bin/javac" -encoding UTF-8 --release 8 -classpath "$API" -d build/classes @build/sources.txt
"$JAVA_HOME/bin/jar" cf build/classes.jar -C build/classes .
"$BT/d8" --lib "$API" --min-api 26 --output build/dex build/classes.jar
cp build/resources.apk build/unsigned.apk
(cd build/dex && zip -q -u ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk
# Keep this local testing key to install future builds without uninstalling.
if [ ! -f build/keys/local-test.jks ]; then
  "$JAVA_HOME/bin/keytool" -genkeypair -noprompt -keystore build/keys/local-test.jks -storepass android -keypass android -alias dokan -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Dokan Hisab Local Test'
fi
"$BT/apksigner" sign --ks build/keys/local-test.jks --ks-key-alias dokan --ks-pass pass:android --key-pass pass:android --out build/dokan-hisab-0.3.0.apk build/aligned.apk
"$BT/apksigner" verify --verbose build/dokan-hisab-0.3.0.apk
mkdir -p build/unit-tests
"$JAVA_HOME/bin/javac" -encoding UTF-8 -d build/unit-tests src/bd/dokanhisab/app/Money.java tests/MoneyTest.java
"$JAVA_HOME/bin/java" -cp build/unit-tests MoneyTest
