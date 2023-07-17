#!/bin/bash
set -e
# shellcheck source=set-env.sh
source "$(dirname "$0")"/set-env.sh

validate_dir "$THIRDPARTY_LIBRARIES"

# shellcheck source=force-clean.sh
source "$(dirname "$0")"/force-clean.sh

echo "Executing ./gradlew clean..."
./gradlew clean --console=plain --parallel --configure-on-demand --max-workers="$CPU_COUNT" || true

echo "Resetting libvpx..."
pushd "$THIRDPARTY_LIBRARIES/libvpx" > /dev/null
make distclean || true
rm -rf build
git clean -f -d
git reset --hard
popd > /dev/null

echo "Resetting FFMpeg..."
pushd "$THIRDPARTY_LIBRARIES/ffmpeg" > /dev/null
make clean || true
rm config.h || true
rm config.asm || true
rm .version || true
rm -rf build
git clean -f -d
git reset --hard
popd > /dev/null

echo "Resetting opus..."
pushd "$THIRDPARTY_LIBRARIES/opus" > /dev/null
git clean -f -d
git reset --hard
popd > /dev/null

echo "Resetting webp..."
pushd "$THIRDPARTY_LIBRARIES/webp" > /dev/null
(test -f Android.mk.bak && rm Android.mk && mv Android.mk.bak Android.mk) || true
git clean -f -d
git reset --hard
popd > /dev/null

echo "Resetting ExoPlayer..."
(test -d "$THIRDPARTY_LIBRARIES/exoplayer" && rm -rf "$THIRDPARTY_LIBRARIES/exoplayer") || true