#!/bin/bash
# DEPRECATED. DO NOT USE
# TODO: make it work again

set -e
# shellcheck source=set-env.sh
source "$(dirname "$0")"/set-env.sh

if [ "$1" = "-f" ]; then
  force-clean.sh
fi

IFS=" " read -r -a abis <<< "$(< app/jni/Application.mk grep -Eoh '^APP_ABI := .+' | grep -Eoh ':= .+' | grep -Eoh '[a-zA-Z][a-zA-Z0-9 \-]+')"

echo "Building .so for abis: " "${abis[@]}" "..."

pushd vkryl/leveldb > /dev/null
#for abi in "${abis[@]}"; do
#  prebuilt="$ANDROID_NDK/toolchains/llvm/prebuilt/$BUILD_PLATFORM"
#  test -d "$prebuilt"
#  case $abi in
#  x86_64 )
#    platform=21
#    prefix="$prebuilt/bin/x86_64-linux-android$platform"
#    cc="$prefix-clang"
#    cxx="$prefix-clang++"
#    test -f "$cc" || (echo "CC not found: $cc" && exit 1)
#    test -f "$cxx" || (echo "CXX not found: $cxx" && exit 1)
#    ;;
#  arm64-v8a )
#    platform=21
#    prefix="$prebuilt/bin/aarch64-linux-android$platform"
#    cc="$prefix-clang"
#    cxx="$prefix-clang++"
#    test -f "$cc" || (echo "CC not found: $cc" && exit 1)
#    test -f "$cxx" || (echo "CXX not found: $cxx" && exit 1)
#    ;;
#  armabi-v7a )
#    platform=16
#    prefix="$prebuilt/bin/arm-linux-androideabi/bin/armv7a-linux-androideabi$platform"
#    cc="$prefix-clang"
#    cxx="$prefix-clang++"
#    test -f "$cc" || (echo "CC not found: $cc" && exit 1)
#    test -f "$cxx" || (echo "CXX not found: $cxx" && exit 1)
#    ;;
#  x86 )
#    platform=16
#    prefix="$prebuilt/bin/i686-linux-android$platform"
#    cc="$prefix-clang"
#    cxx="$prefix-clang++"
#    test -f "$cc" || (echo "CC not found: $cc" && exit 1)
#    test -f "$cxx" || (echo "CXX not found: $cxx" && exit 1)
#    ;;
#  * )
#    echo "Unknown abi \"$abi\"!"
#    exit 1
#    ;;
#  esac
#
#  echo "Building leveldb for $abi, android-$platform..."
#  cmake -B".cxx/cmake/debug/$abi" \
#    -DANDROID_ABI="$abi" \
#    -DANDROID_PLATFORM="android-$platform" \
#    -DANDROID_NDK="$NDK_VERSION" \
#    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
#    -DCMAKE_MAKE_PROGRAM="$(command -v ninja)" \
#    -G Ninja .
#done
popd > /dev/null

pushd app > /dev/null
if "$ANDROID_NDK/ndk-build" NDK_LIBS_OUT=./jniLibs NDK_OUT=./.externalNativeBuild -S -o jni/Android.mk -j "$CPU_COUNT"; then
  echo "Installed fresh libraries:"
  ls jniLibs/*/*.so
fi
popd > /dev/null