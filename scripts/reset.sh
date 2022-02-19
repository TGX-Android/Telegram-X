#!/bin/bash
set -e
# shellcheck source=set-env.sh
source "$(dirname "$0")"/set-env.sh
# shellcheck source=force-clean.sh
source "$(dirname "$0")"/force-clean.sh

echo "Executing ./gradlew clean..."
./gradlew clean --console=plain --parallel --configure-on-demand --max-workers="$CPU_COUNT" || true

echo "Resetting libvpx..."
pushd app/jni/thirdparty/libvpx
make distclean || true
rm -rf build
git clean -f -d
git reset --hard
popd

echo "Resetting FFMpeg..."
pushd app/jni/thirdparty/ffmpeg
make clean || true
rm config.h || true
rm config.asm || true
rm .version || true
rm -rf build
git clean -f -d
git reset --hard
popd

echo "Resetting opus..."
pushd app/jni/thirdparty/opus > /dev/null
git clean -f -d
git reset --hard
popd > /dev/null

echo "Resetting webp..."
pushd app/jni/thirdparty/webp > /dev/null
(test -f Android.mk.bak && rm Android.mk && mv Android.mk.bak Android.mk) || true
git clean -f -d
git reset --hard
popd > /dev/null

echo "Resetting ExoPlayer..."
(test -d app/jni/thirdparty/exoplayer && rm -rf app/jni/thirdparty/exoplayer) || true