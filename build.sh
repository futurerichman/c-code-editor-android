#!/usr/bin/env bash
# UI 版构建脚本（非 Gradle 流水线）
set -euo pipefail
BT=/home/user/android-sdk/build-tools/34.0.0
AJ=/home/user/android-sdk/platforms/android-34/android.jar
JH=/home/user/jdk17
KS=/home/user/Doubao/chats/38449593145626626/CCodeApp/toolchain/ccode.keystore
OUT="${1:-C语言编译器-UI版.apk}"
cd "$(dirname "$0")"

rm -rf gen classes dex res.zip out.apk aligned.apk "$OUT" "$OUT.idsig" 2>/dev/null || true
mkdir -p gen classes dex

echo "[1/4] 编译资源"
"$BT/aapt2" compile --dir res -o res.zip

echo "[2/4] 链接+生成 R"
ASSETS=""
[ -d assets ] && ASSETS="-A assets"
"$BT/aapt2" link -o out.apk -I "$AJ" \
  --manifest src/AndroidManifest.xml \
  --java gen \
  --min-sdk-version 26 --target-sdk-version 34 \
  $ASSETS \
  res.zip

echo "[3/4] javac + d8"
"$JH/bin/javac" --release 8 -classpath "$AJ" -encoding UTF-8 \
  -d classes gen/com/ccode/mintest/R.java src/com/ccode/mintest/*.java
"$BT/d8" --release --lib "$AJ" --output dex $(find classes -name '*.class')
( cd dex && zip -q -j ../out.apk classes.dex )

echo "[4/4] 对齐+签名+验证"
"$BT/zipalign" -f 4 out.apk aligned.apk
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:ccode123 --out "$OUT" aligned.apk
"$BT/apksigner" verify --print-certs "$OUT" | head -2
"$BT/aapt2" dump badging "$OUT" | grep -E "package:|sdkVersion|targetSdk|launchable|application-label"
rm -f "$OUT.idsig"
ls -lh "$OUT"
