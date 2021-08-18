#!/bin/bash
set -e

echo "Clearing build files..."

rm -rf .gradle buildSrc/.gradle
rm -rf build buildSrc/build app/build vkryl/core/build vkryl/android/build vkryl/td/build vkryl/leveldb/build tdlib/build
rm -rf app/jni/thirdparty/exoplayer
rm -rf vkryl/leveldb/jni/leveldb/out
rm -rf app/.cxx vkryl/leveldb/.cxx
rm -rf app/.externalNativeBuild vkryl/leveldb/.externalNativeBuild
rm -rf app/obj vkryl/leveldb/obj app/jniLibs

echo "Removed build files."