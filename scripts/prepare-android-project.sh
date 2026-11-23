#!/usr/bin/env bash
# Builds the Android Studio / Gradle project in ./android-project
#
#   1. downloads the official LÖVE Android project (love-android, tag from project.env)
#   2. zips ./game  ->  app/src/embed/assets/game.love
#   3. applies app name / package id / version / orientation / icon
#
# Afterwards you can either open ./android-project in Android Studio
# or run:  cd android-project && ./gradlew assembleEmbedNoRecordDebug
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

set -a
# shellcheck disable=SC1091
. ./project.env
set +a

VERSION_CODE="${VERSION_CODE_OVERRIDE:-$VERSION_CODE}"
OUT="$ROOT/android-project"

echo ">> love-android $LOVE_ANDROID_TAG  ->  $OUT"
rm -rf "$OUT"
git -c advice.detachedHead=false clone --quiet --depth 1 --branch "$LOVE_ANDROID_TAG" \
    --recurse-submodules --shallow-submodules \
    https://github.com/love2d/love-android.git "$OUT"

# no nested git repos inside the generated project
find "$OUT" -name .git -prune -exec rm -rf {} +

echo ">> packing game/ into game.love"
ASSETS="$OUT/app/src/embed/assets"
mkdir -p "$ASSETS"
rm -f "$ASSETS/game.love"
if command -v zip >/dev/null 2>&1; then
    (cd "$ROOT/game" && zip -q -9 -r "$ASSETS/game.love" . -x "*.DS_Store")
else
    python3 - "$ROOT/game" "$ASSETS/game.love" <<'PY'
import os, sys, zipfile
src, dst = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    for base, _, files in os.walk(src):
        for f in files:
            if f == ".DS_Store":
                continue
            p = os.path.join(base, f)
            z.write(p, os.path.relpath(p, src))
PY
fi

echo ">> gradle.properties  ($APP_NAME / $APP_ID / v$VERSION_NAME #$VERSION_CODE)"
cat > "$OUT/gradle.properties" <<PROPS
app.name=$APP_NAME
app.application_id=$APP_ID
app.orientation=$ORIENTATION
app.version_code=$VERSION_CODE
app.version_name=$VERSION_NAME

org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8

# No need to modify anything past this line!
android.enableJetifier=false
android.useAndroidX=true
android.defaults.buildfeatures.buildconfig=true
android.nonTransitiveRClass=true
android.nonFinalResIds=true
PROPS

echo ">> launcher icon"
for d in ldpi mdpi hdpi xhdpi xxhdpi xxxhdpi; do
    mkdir -p "$OUT/app/src/main/res/drawable-$d"
    cp "$ROOT/android/icon.png" "$OUT/app/src/main/res/drawable-$d/love.png"
done

echo
echo "Done."
echo "  Android Studio : File > Open > $OUT"
echo "  Command line   : cd android-project && ./gradlew assembleEmbedNoRecordDebug"
