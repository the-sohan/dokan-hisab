#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
BT="$ANDROID_SDK_ROOT/build-tools/35.0.0"
API="$ANDROID_SDK_ROOT/platforms/android-35/android.jar"
ADB="$ANDROID_SDK_ROOT/platform-tools/adb"
mkdir -p build/instrumentation/classes build/instrumentation/dex
"$BT/aapt2" link -o build/instrumentation/resources.apk --manifest tests/AndroidManifest.xml -I "$API"
"$JAVA_HOME/bin/javac" --release 8 -encoding UTF-8 -cp "$API:build/classes" -d build/instrumentation/classes tests/LedgerInstrumentation.java tests/BackupInstrumentation.java tests/TestBackupProvider.java
"$JAVA_HOME/bin/jar" cf build/instrumentation/tests.jar -C build/instrumentation/classes .
"$BT/d8" --lib "$API" --classpath build/classes.jar --min-api 26 --output build/instrumentation/dex build/instrumentation/tests.jar
cp build/instrumentation/resources.apk build/instrumentation/unsigned.apk
(cd build/instrumentation/dex && zip -q -u ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 build/instrumentation/unsigned.apk build/instrumentation/aligned.apk
"$BT/apksigner" sign --ks build/keys/local-test.jks --ks-key-alias dokan --ks-pass pass:android --key-pass pass:android --out build/instrumentation/tests.apk build/instrumentation/aligned.apk
"$ADB" install -r build/dokan-hisab-0.2.0.apk
"$ADB" install -r build/instrumentation/tests.apk
"$ADB" shell am instrument -w bd.dokanhisab.tests/bd.dokanhisab.tests.LedgerInstrumentation
