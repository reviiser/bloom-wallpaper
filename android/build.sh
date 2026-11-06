#!/bin/bash
# Builds the installable APK with the bare Android tools, no Gradle. Output: android/dist/bloom-by-reviiser.apk
set -e
cd "$(dirname "$(readlink -f "$0")")"
T="$HOME/.local/share/bloom-android"; export JAVA_HOME="$T/jdk"; export PATH="$JAVA_HOME/bin:$PATH"
B="$T/sdk/build-tools/34.0.0"; J="$T/sdk/platforms/android-34/android.jar"; O="$T/build/app"
rm -rf "$O"; mkdir -p "$O/classes" "$O/res" dist
[ -f app/res/drawable-nodpi/icon.png ] || /usr/bin/python3 make_art.py
"$B/aapt2" compile --dir app/res -o "$O/res/res.zip"
"$B/aapt2" link -I "$J" --manifest app/AndroidManifest.xml -o "$O/app.unsigned.apk" "$O/res/res.zip"
find app/src -name '*.java' > "$O/sources.txt"
javac -source 8 -target 8 -nowarn -Xlint:none -bootclasspath "$J" -cp "$J" -sourcepath "" -d "$O/classes" @"$O/sources.txt" 2>&1 | grep -v "bootstrap\|obsolete\|suppress\|^Note" || true
[ -f "$O/classes/com/reviiser/bloom/BloomDream.class" ] || { echo "did not compile"; exit 1; }
find "$O/classes" -name '*.class' > "$O/classes.txt"
"$B/d8" --release --min-api 22 --lib "$J" --output "$O" @"$O/classes.txt"
(cd "$O" && zip -qj app.unsigned.apk classes.dex)
"$B/zipalign" -f 4 "$O/app.unsigned.apk" "$O/app.aligned.apk"
K="$T/release.keystore"
[ -f "$K" ] || keytool -genkeypair -keystore "$K" -storepass bloomforest -keypass bloomforest -alias bloom -keyalg RSA -keysize 2048 -validity 20000 -dname "CN=Reviiser, O=Bloom" 2>/dev/null
"$B/apksigner" sign --ks "$K" --ks-pass pass:bloomforest --ks-key-alias bloom --key-pass pass:bloomforest --out dist/bloom-by-reviiser.apk "$O/app.aligned.apk"
rm -f dist/bloom-by-reviiser.apk.idsig
ls -la dist/bloom-by-reviiser.apk
