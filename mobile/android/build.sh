#!/usr/bin/env bash
# Monta o EmDia.apk sem Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}"
BT="$SDK/build-tools/35.0.0"
JAR="$SDK/platforms/android-35/android.jar"
OUT=build
rm -rf "$OUT" && mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

# 1. Recursos + manifesto + o quadro (pasta www) como assets
"$BT/aapt2.exe" compile --dir res -o "$OUT/res.zip"
"$BT/aapt2.exe" link -o "$OUT/app.unaligned.apk" -I "$JAR" --manifest AndroidManifest.xml \
  --min-sdk-version 26 --target-sdk-version 34 --version-code 3 --version-name 1.2 \
  -A ../www --java "$OUT/gen" "$OUT/res.zip"

# 2. Código Java
javac -nowarn --release 11 -classpath "$JAR" -d "$OUT/classes" \
  $(find src "$OUT/gen" -name '*.java') 2>&1 | grep -v '^warning\|^Note\|^1 warning' || true
"$BT/d8.bat" --release --min-api 26 --lib "$JAR" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')

# 3. Junta o classes.dex no apk
python - "$OUT/app.unaligned.apk" "$OUT/dex/classes.dex" <<'PY'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1], "a", zipfile.ZIP_DEFLATED) as z:
    z.write(sys.argv[2], "classes.dex")
PY

# 4. Alinha e assina
[ -f quadro.keystore ] || keytool -genkeypair -keystore quadro.keystore -storepass quadrosemanal -keypass quadrosemanal \
  -alias quadro -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Quadro Semanal" >/dev/null 2>&1
"$BT/zipalign.exe" -p -f 4 "$OUT/app.unaligned.apk" "$OUT/app.aligned.apk"
"$BT/apksigner.bat" sign --ks quadro.keystore --ks-pass pass:quadrosemanal --ks-key-alias quadro \
  --out ../EmDia.apk "$OUT/app.aligned.apk"
"$BT/apksigner.bat" verify ../EmDia.apk && echo "APK pronto: mobile/EmDia.apk"
