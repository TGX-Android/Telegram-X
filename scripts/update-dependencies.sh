#!/bin/bash
set -e
# shellcheck source=set-env.sh
source "$(pwd)/scripts/set-env.sh"

reset.sh

simple_modules=( \
  thirdparty/androidx-media \
  app/jni/thirdparty/jni-utils \
  app/jni/thirdparty/libtgvoip \
  app/jni/thirdparty/rlottie \
  vkryl/leveldb \
  vkryl/leveldb/jni/leveldb \
  vkryl/leveldb/jni/jni-utils \
  tdlib \
)
for module in "${simple_modules[@]}"; do
  echo "Patching $module..."
  pushd "$module" > /dev/null
  git pull
  popd > /dev/null
done

echo "Patching androidx-media..."
patch-androidx-media-impl.sh

remote_modules=( webp libyuv ffmpeg lz4 flac opus opusfile ogg libvpx )
for module in "${remote_modules[@]}"; do
  version=$(read-property.sh version.properties "version.$module")
  echo "Patching $module to $version..."
  pushd "app/jni/thirdparty/$module" > /dev/null
  git pull origin "$version" || (echo "Pulling $module $version failed" && exit 1)
  popd > /dev/null
done