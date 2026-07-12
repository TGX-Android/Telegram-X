#!/bin/bash
set -e

# shellcheck source=set-env.sh
source "$(pwd)/scripts/set-env.sh"

if [ -z "$FLAVORS" ]; then
  echo -e "${STYLE_ERROR}Failed! FLAVORS is empty. Run 'export FLAVORS=[version.flavors]'${STYLE_END}"
  exit
fi

echo "Clearing build files..."

validate_dir "$THIRDPARTY_LIBRARIES"

rm -rf .gradle buildSrc/.gradle
rm -rf build buildSrc/build app/build vkryl/core/build vkryl/android/build vkryl/td/build vkryl/leveldb/build tdlib/build
for FLAVOR in $FLAVORS; do
  rm -rf "$THIRDPARTY_LIBRARIES/androidx-media/$FLAVOR"
done
rm -rf vkryl/leveldb/jni/leveldb/out
rm -rf app/.cxx vkryl/leveldb/.cxx
rm -rf app/.externalNativeBuild vkryl/leveldb/.externalNativeBuild
rm -rf app/obj vkryl/leveldb/obj app/jniLibs

echo "Removed build files."