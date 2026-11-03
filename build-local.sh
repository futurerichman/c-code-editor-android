#!/usr/bin/env bash
# 本地 Windows（Git Bash）构建脚本
set -euo pipefail

BT=/c/Android/build-tools/36.0.0
AJ=/c/Android/platforms/android-36/android.jar
JH=/c/Android/jdk17
KS=/c/Android/ccode-keystore-new.jks
KS_PASS_FILE=/c/Android/ccode-ks.pass

OUT="${1:-C语言编译器-UI版.apk}"
VER_NAME="${2:-}"
VER_CODE="${3:-}"

cd "$(dirname "$0")"
KS_PASS=$(cat "$KS_PASS_FILE")

TMPROOT="${TMPDIR:-/tmp}"
B="$TMPROOT/ccode_build_$(date +%Y%m%d_%H%M%S)_$$"
mkdir -p "$B/gen" "$B/classes" "$B/dex"

echo "[1/4] 编译资源"
"$BT/aapt2" compile --dir res -o "$B/res.zip"

echo "[2/4] 链接+生成 R"
ASSETS=""
[ -d assets ] && ASSETS="-A assets"
"$BT/aapt2" link -o "$B/out.apk" -I "$AJ" \
  --manifest src/AndroidManifest.xml \
  --java "$B/gen" \
  --min-sdk-version 26 --target-sdk-version 34 \
  $ASSETS \
  "$B/res.zip"

if [ -n "$VER_NAME" ]; then
  python - "$VER_NAME" "$VER_CODE" <<'EOF'
import sys
ver_name, ver_code = sys.argv[1], sys.argv[2]
with open('src/AndroidManifest.xml', 'r', encoding='utf-8') as f:
    s = f.read()
import re
s = re.sub(r'android:versionName="[^"]*"', 'android:versionName="%s"' % ver_name, s)
s = re.sub(r'android:versionCode="[^"]*"', 'android:versionCode="%s"' % ver_code, s)
with open('src/AndroidManifest.xml', 'w', encoding='utf-8') as f:
    f.write(s)
print("versionName ->", ver_name, " versionCode ->", ver_code)
EOF
  "$BT/aapt2" link -o "$B/out.apk" -I "$AJ" \
    --manifest src/AndroidManifest.xml \
    --java "$B/gen" \
    --min-sdk-version 26 --target-sdk-version 34 \
    $ASSETS \
    "$B/res.zip"
fi

echo "[3/4] javac + d8"
"$JH/bin/javac" --release 8 -classpath "$AJ" -encoding UTF-8 \
  -d "$B/classes" "$B/gen/com/ccode/mintest/R.java" src/com/ccode/mintest/*.java
"$BT/d8.bat" --release --lib "$AJ" --output "$B/dex" $(find "$B/classes" -name '*.class' | tr '\n' ' ')
python - "$B" <<'EOF'
import sys, zipfile
b = sys.argv[1]
with zipfile.ZipFile(b + '/out.apk', 'a') as z:
    z.write(b + '/dex/classes.dex', 'classes.dex')
print('classes.dex added')
EOF

echo "[4/4] 对齐+签名+验证"
"$BT/zipalign" -f 4 "$B/out.apk" "$B/aligned.apk"
"$BT/apksigner.bat" sign --ks "$KS" --ks-pass "pass:$KS_PASS" \
  --ks-key-alias ccode --key-pass "pass:$KS_PASS" --out "$OUT" "$B/aligned.apk" 2>/dev/null
"$BT/apksigner.bat" verify --print-certs "$OUT" 2>/dev/null | grep -E "DN|SHA-256" | head -2
"$BT/aapt2" dump badging "$OUT" | grep -E "package:|launchable" | head -2
rm -f "$OUT.idsig" 2>/dev/null || true
ls -lh "$OUT"
echo "中间产物：$B"
