#!/bin/bash
set -e

CMAKE_DIR="$ANDROID_SDK_ROOT/cmake/${CMAKE_VERSION}"
CMAKE_BIN="$CMAKE_DIR/bin/cmake"
NINJA_BIN="$CMAKE_DIR/bin/ninja"
TARGET_DIR="$1"
CMAKE_ARGS=${@:2}

validate_file "$CMAKE_BIN"
validate_file "$NINJA_BIN"
validate_dir "$TARGET_DIR"

function run_cmake {
  ARG_ABI="$1"
  ARG_API_LEVEL="$2"
  test -d "$ARG_ABI" || mkdir "$ARG_ABI"
  pushd "$ARG_ABI" > /dev/null

  if (( $ARG_API_LEVEL >= 21 )); then
    ANDROID_NDK_ROOT="$ANDROID_SDK_ROOT/ndk/$ANDROID_NDK_VERSION_PRIMARY"
  else
    ANDROID_NDK_ROOT="$ANDROID_SDK_ROOT/ndk/$ANDROID_NDK_VERSION_LEGACY"
  fi
  validate_dir "$ANDROID_NDK_ROOT"

  $CMAKE_BIN -DANDROID_ABI="${ARG_ABI}" \
    -DCMAKE_TOOLCHAIN_FILE="${ANDROID_NDK_ROOT}/build/cmake/android.toolchain.cmake" \
    -DANDROID_NATIVE_API_LEVEL="${ARG_API_LEVEL}" \
    ${CMAKE_ARGS} \
    -GNinja ../..
  $NINJA_BIN

  popd > /dev/null
}

pushd "$TARGET_DIR"

test -d build || mkdir build
pushd build > /dev/null

run_cmake arm64-v8a 21
run_cmake x86_64 21
run_cmake armeabi-v7a 16
run_cmake x86 16

popd > /dev/null
popd