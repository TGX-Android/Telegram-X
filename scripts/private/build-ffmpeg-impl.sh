#!/bin/bash
set -e

function build_one {
  if [ -z "$ANDROID_NDK_ROOT" -a "$ANDROID_NDK_ROOT" == "" ]; then
    echo -e "${STYLE_ERROR}Failed! NDK is empty. Run 'export ANDROID_NDK_ROOT=[PATH_TO_NDK]'${STYLE_END}"
    exit
  fi
  validate_dir "$ANDROID_NDK_ROOT"

  LIBVPX_INCLUDE_DIR="$THIRDPARTY_LIBRARIES/libvpx/build/$FLAVOR/$PLATFORM/include"
  LIBVPX_LIB_DIR="$THIRDPARTY_LIBRARIES/libvpx/build/$FLAVOR/$PLATFORM/lib"

  validate_dir "$LIBVPX_INCLUDE_DIR"
  validate_dir "$LIBVPX_LIB_DIR"

  AR="${PREBUILT}/bin/llvm-ar"
  NM="${PREBUILT}/bin/llvm-nm"
  STRIP="${PREBUILT}/bin/llvm-strip"
  RANLIB="${PREBUILT}/bin/llvm-ranlib"
  YASM="${PREBUILT}/bin/yasm"

  validate_file "$CC"
  validate_file "$CXX"
  validate_file "$AS"
  validate_file "$AR"
  validate_file "$LD"
  validate_file "$NM"
  validate_file "$STRIP"
  validate_file "$YASM"
  validate_file "$RANLIB"
  validate_dir "$LINK"

  echo "Cleaning..."
  rm -f config.h
  make clean || true

  echo "Configuring ffmpeg: ${PLATFORM} ${FLAVOR} (${ANDROID_API}), NDK: ${ANDROID_NDK_ROOT}"

  ./configure \
  --nm="${NM}" \
  --ar="${AR}" \
  --as="${AS}" \
  --strip="${STRIP}" \
  --cc="${CC}" \
  --cxx="${CXX}" \
  --ranlib="${RANLIB}" \
  --enable-stripping \
  --arch="$ARCH" \
  --target-os=linux \
  --enable-cross-compile \
  --x86asmexe="$YASM" \
  --prefix="$PREFIX" \
  --enable-pic \
  --disable-shared \
  --enable-static \
  --enable-asm \
  --enable-inline-asm \
  --enable-x86asm \
  --cross-prefix="$CROSS_PREFIX"- \
  --sysroot="$SYSROOT" \
  --extra-cflags="-fvisibility=hidden -ffunction-sections -fdata-sections -g -fno-omit-frame-pointer -w -Werror -Wl,-Bsymbolic -Os -DCONFIG_LINUX_PERF=0 -DANDROID $OPTIMIZE_CFLAGS -I$LIBVPX_INCLUDE_DIR --static -fPIC" \
  --extra-ldflags="-L$LIBVPX_LIB_DIR $EXTRA_LDFLAGS -L -lvpx -Wl,-Bsymbolic -nostdlib -lc -lm -ldl -fPIC" \
  --extra-libs="$EXTRA_LIBS" \
  \
  --enable-version3 \
  --enable-gpl \
  \
  --disable-linux-perf \
  \
  --disable-doc \
  --disable-htmlpages \
  --disable-avx \
  \
  --disable-everything \
  --disable-network \
  --disable-zlib \
  --disable-avdevice \
  --disable-debug \
  --disable-programs \
  --disable-network \
  --disable-ffplay \
  --disable-ffprobe \
  --disable-avdevice \
  \
  --enable-runtime-cpudetect \
  --enable-pthreads \
  --enable-filter="scale" \
  --enable-filter="overlay" \
  --enable-protocol=file \
  \
  --enable-decoder=h264 \
  --enable-decoder=mpeg4 \
  --enable-decoder=gif \
  --enable-decoder=alac \
  --enable-decoder=aac \
  \
  --enable-libvpx \
  --enable-decoder=libvpx_vp9 \
  \
  --enable-demuxer=mov \
  --enable-demuxer=matroska \
  --enable-demuxer=gif \
  --enable-hwaccels \
  $ADDITIONAL_CONFIGURE_FLAG

  make -j"$CPU_COUNT"
  make install
}

function checkPreRequisites {
  test "$BUILD_PLATFORM" && echo "build platform: ${BUILD_PLATFORM}"
  test "$CPU_COUNT" && echo "parallel jobs: ${CPU_COUNT}"

  if ! [ -d "ffmpeg" ] || ! [ "$(ls -A ffmpeg)" ]; then
    echo -e "${STYLE_ERROR}Failed! Submodule 'ffmpeg' not found!${STYLE_END}"
    echo -e "${STYLE_ERROR}Try to run: 'git submodule init && git submodule update --init --recursive'${STYLE_END}"
    exit
  fi

  test "$CPU_COUNT"
}

pushd "$THIRDPARTY_LIBRARIES" > /dev/null
echo "Checking pre-requisites..."
checkPreRequisites
popd > /dev/null

## common

pushd "$THIRDPARTY_LIBRARIES/ffmpeg"

# 64-bit, minSdk 21
ANDROID_NDK_VERSION=$ANDROID_NDK_VERSION_PRIMARY
ANDROID_NDK_ROOT="$ANDROID_SDK_ROOT/ndk/$ANDROID_NDK_VERSION"
PREBUILT="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/$BUILD_PLATFORM"
SYSROOT="$PREBUILT/sysroot"

validate_dir "$ANDROID_NDK_ROOT"
validate_dir "$PREBUILT"
validate_dir "$SYSROOT"

# arm64-v8a
CROSS_PREFIX=$PREBUILT/bin/aarch64-linux-android
ARCH=aarch64
CPU=armv8-a
PLATFORM=arm64-v8a
ADDITIONAL_CONFIGURE_FLAG="--enable-optimizations --disable-x86asm"
OPTIMIZE_CFLAGS=""
EXTRA_LIBS="-lunwind"
EXTRA_LDFLAGS=""
# FIXME ADDITIONAL_CONFIGURE_FLAG="--enable-neon --enable-optimizations"

# latest-arm64
FLAVOR="latest"
PREFIX=./build/$FLAVOR/$PLATFORM
ANDROID_API=23
LINK=$SYSROOT/usr/lib/aarch64-linux-android/$ANDROID_API
CC=${CROSS_PREFIX}${ANDROID_API}-clang
CXX=${CROSS_PREFIX}${ANDROID_API}-clang++
LD=$CC
AS=$CC
build_one

# lollipop-arm64
FLAVOR="lollipop"
PREFIX=./build/$FLAVOR/$PLATFORM
ANDROID_API=21
LINK=$SYSROOT/usr/lib/aarch64-linux-android/$ANDROID_API
CC=${CROSS_PREFIX}${ANDROID_API}-clang
CXX=${CROSS_PREFIX}${ANDROID_API}-clang++
LD=$CC
AS=$CC
build_one

# x86_64
CROSS_PREFIX=$PREBUILT/bin/x86_64-linux-android
ARCH=x86_64
CPU=x86_64
PLATFORM=x86_64
ADDITIONAL_CONFIGURE_FLAG="--disable-asm --disable-x86asm"
OPTIMIZE_CFLAGS=""
EXTRA_LIBS="-lunwind"
EXTRA_LDFLAGS=""

# latest-x64
FLAVOR="latest"
PREFIX=./build/$FLAVOR/$PLATFORM
ANDROID_API=23
LINK=$SYSROOT/usr/lib/x86_64-linux-android/$ANDROID_API
CC=${CROSS_PREFIX}${ANDROID_API}-clang
CXX=${CROSS_PREFIX}${ANDROID_API}-clang++
LD=$CC
AS=$CC
build_one

# lollipop-x64
FLAVOR="lollipop"
PREFIX=./build/$FLAVOR/$PLATFORM
ANDROID_API=21
LINK=$SYSROOT/usr/lib/x86_64-linux-android/$ANDROID_API
CC=${CROSS_PREFIX}${ANDROID_API}-clang
CXX=${CROSS_PREFIX}${ANDROID_API}-clang++
LD=$CC
AS=$CC
build_one

# 32-bit, minSdk 16
ANDROID_NDK_VERSION=$ANDROID_NDK_VERSION_LEGACY
ANDROID_NDK_ROOT="$ANDROID_SDK_ROOT/ndk/$ANDROID_NDK_VERSION"
PREBUILT="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/$BUILD_PLATFORM"
SYSROOT="$PREBUILT/sysroot"

validate_dir "$PREBUILT"
validate_dir "$SYSROOT"

# armeabi-v7a
CROSS_PREFIX=$PREBUILT/bin/arm-linux-androideabi
ARCH=arm
CPU=armv7-a
PLATFORM=armv7-a
ADDITIONAL_CONFIGURE_FLAG="--enable-neon --disable-x86asm"
OPTIMIZE_CFLAGS="-marm -march=$CPU -mfloat-abi=softfp"
if [[ ${ANDROID_NDK_VERSION%%.*} -ge 23 ]]; then
  LD=$CC
  LIBS_DIR="${PREBUILT}/lib64/clang/12.0.9/lib/linux"
  validate_dir "$LIBS_DIR"
  EXTRA_LDFLAGS="-L${LIBS_DIR} -Wl,--fix-cortex-a8"
  EXTRA_LIBS="-lunwind -lclang_rt.builtins-arm-android"
else
  LD="${PREBUILT}/arm-linux-androideabi/bin/ld.gold"
  EXTRA_LDFLAGS=""
  EXTRA_LIBS="-lgcc"
fi

# latest-arm32
FLAVOR="latest"
PREFIX=./build/$FLAVOR/$PLATFORM
ANDROID_API=23
LINK=$SYSROOT/usr/lib/arm-linux-androideabi/$ANDROID_API
CC=$PREBUILT/bin/armv7a-linux-androideabi${ANDROID_API}-clang
CXX=$PREBUILT/bin/armv7a-linux-androideabi${ANDROID_API}-clang++
AS=$CC
build_one

# lollipop-arm32
FLAVOR="lollipop"
PREFIX=./build/$FLAVOR/$PLATFORM
ANDROID_API=21
LINK=$SYSROOT/usr/lib/arm-linux-androideabi/$ANDROID_API
CC=$PREBUILT/bin/armv7a-linux-androideabi${ANDROID_API}-clang
CXX=$PREBUILT/bin/armv7a-linux-androideabi${ANDROID_API}-clang++
AS=$CC
build_one

# legacy-arm32
FLAVOR="legacy"
PREFIX=./build/$FLAVOR/$PLATFORM
ANDROID_API=16
LINK=$SYSROOT/usr/lib/arm-linux-androideabi/$ANDROID_API
CC=$PREBUILT/bin/armv7a-linux-androideabi${ANDROID_API}-clang
CXX=$PREBUILT/bin/armv7a-linux-androideabi${ANDROID_API}-clang++
AS=$CC
build_one

# x86
CROSS_PREFIX=$PREBUILT/bin/i686-linux-android
ARCH=x86
CPU=i686
PLATFORM=i686
ADDITIONAL_CONFIGURE_FLAG="--disable-asm --disable-x86asm"
OPTIMIZE_CFLAGS="-march=$CPU"
if [[ ${ANDROID_NDK_VERSION%%.*} -ge 23 ]]; then
  LD=$CC
  LIBS_DIR="${PREBUILT}/lib64/clang/12.0.9/lib/linux"
  validate_dir "$LIBS_DIR"
  EXTRA_LDFLAGS="-L${LIBS_DIR}"
  EXTRA_LIBS=-lclang_rt.builtins-i686-android
else
  LD="${PREBUILT}/i686-linux-android/bin/ld.gold"
  EXTRA_LDFLAGS=""
  EXTRA_LIBS="-lgcc"
fi

# latest-x86
FLAVOR="latest"
PREFIX=./build/$FLAVOR/$PLATFORM
ANDROID_API=23
LINK=$SYSROOT/usr/lib/i686-linux-android/$ANDROID_API
CC=${CROSS_PREFIX}${ANDROID_API}-clang
CXX=${CROSS_PREFIX}${ANDROID_API}-clang++
AS=$CC
build_one

# lollipop-x86
FLAVOR="lollipop"
PREFIX=./build/$FLAVOR/$PLATFORM
ANDROID_API=21
LINK=$SYSROOT/usr/lib/i686-linux-android/$ANDROID_API
CC=${CROSS_PREFIX}${ANDROID_API}-clang
CXX=${CROSS_PREFIX}${ANDROID_API}-clang++
AS=$CC
build_one

# legacy-x86
FLAVOR="legacy"
PREFIX=./build/$FLAVOR/$PLATFORM
ANDROID_API=16
LINK=$SYSROOT/usr/lib/i686-linux-android/$ANDROID_API
CC=${CROSS_PREFIX}${ANDROID_API}-clang
CXX=${CROSS_PREFIX}${ANDROID_API}-clang++
AS=$CC
build_one

popd