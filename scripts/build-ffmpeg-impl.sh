#!/bin/bash
set -e

function validate_file {
  test -f "$1" || (echo "File not found: $1" && false)
}
function validate_dir {
  test -d "$1" || (echo "Directory not found: $1" && false)
}
function build_one {
  LIBVPX_INCLUDE="-I$THIRDPARTY_LIBRARIES/libvpx/build/$CPU/include/"
  LIBVPX_LIB="-L$THIRDPARTY_LIBRARIES/libvpx/build/$CPU/lib/"

  AR="${PREBUILT}/bin/llvm-ar"
  NM="${PREBUILT}/bin/llvm-nm"
  STRIP="${PREBUILT}/bin/llvm-strip"
  RANLIB="${PREBUILT}/bin/llvm-ranlib"
  YASM=$PREBUILT/bin/yasm

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

  echo "Configuring... ${NDK}"

  ./configure \
  --nm=${NM} \
  --ar=${AR} \
  --as=${AS} \
  --strip=${STRIP} \
  --cc=${CC} \
  --cxx=${CXX} \
  --ranlib=${RANLIB} \
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
  --extra-cflags="-w -Werror -Wl,-Bsymbolic -Os -DCONFIG_LINUX_PERF=0 -DANDROID $OPTIMIZE_CFLAGS $LIBVPX_INCLUDE --static -fPIC" \
  --extra-ldflags="$LIBVPX_LIB -Wl,-Bsymbolic -nostdlib -lc -lm -ldl -fPIC" \
  --extra-libs="-lunwind" \
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
  --disable-postproc \
  --disable-debug \
  --disable-programs \
  --disable-network \
  --disable-ffplay \
  --disable-ffprobe \
  --disable-postproc \
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

  if [ -z "$ANDROID_NDK" -a "$ANDROID_NDK" == "" ]; then
    echo -e "${STYLE_ERROR}Failed! NDK is empty. Run 'export NDK=[PATH_TO_NDK]'${STYLE_END}"
    exit
  fi

  validate_dir "$ANDROID_NDK"
  test "$CPU_COUNT"
}

echo "Checking pre-requisites..."
checkPreRequisites

## common

PREBUILT=$ANDROID_NDK/toolchains/llvm/prebuilt/$BUILD_PLATFORM
SYSROOT=$PREBUILT/sysroot

validate_dir "$PREBUILT"
validate_dir "$SYSROOT"

pushd ffmpeg

#x86_64
ANDROID_API=21
LINK=$SYSROOT/usr/lib/x86_64-linux-android/$ANDROID_API
CROSS_PREFIX=$PREBUILT/bin/x86_64-linux-android
CC=${CROSS_PREFIX}${ANDROID_API}-clang
CXX=${CROSS_PREFIX}${ANDROID_API}-clang++
LD=$CC
AS=$CC
ARCH=x86_64
CPU=x86_64
PREFIX=./build/$CPU
ADDITIONAL_CONFIGURE_FLAG="--disable-asm"
OPTIMIZE_CFLAGS=""
build_one

#arm64-v8a
ANDROID_API=21
LINK=$SYSROOT/usr/lib/aarch64-linux-android/$ANDROID_API
CROSS_PREFIX=$PREBUILT/bin/aarch64-linux-android
CC=${CROSS_PREFIX}${ANDROID_API}-clang
CXX=${CROSS_PREFIX}${ANDROID_API}-clang++
LD=$CC
AS=$CC
ARCH=arm64
CPU=arm64-v8a
PREFIX=./build/$CPU
ADDITIONAL_CONFIGURE_FLAG="--disable-asm --enable-optimizations"
OPTIMIZE_CFLAGS=""
# FIXME ADDITIONAL_CONFIGURE_FLAG="--enable-neon --enable-optimizations"
build_one

#arm v7n
ANDROID_API=16
LINK=$SYSROOT/usr/lib/arm-linux-androideabi/$ANDROID_API
CROSS_PREFIX=$PREBUILT/bin/arm-linux-androideabi
CC=$PREBUILT/bin/armv7a-linux-androideabi${ANDROID_API}-clang
CXX=$PREBUILT/bin/armv7a-linux-androideabi${ANDROID_API}-clang++
LD=$CC
AS=$CC
ARCH=arm
CPU=armv7-a
PREFIX=./build/$CPU
ADDITIONAL_CONFIGURE_FLAG="--enable-neon"
OPTIMIZE_CFLAGS="-marm -march=$CPU"
build_one

#x86 platform
ANDROID_API=16
LINK=$SYSROOT/usr/lib/i686-linux-android/$ANDROID_API
CROSS_PREFIX=$PREBUILT/bin/i686-linux-android
CC=${CROSS_PREFIX}${ANDROID_API}-clang
CXX=${CROSS_PREFIX}${ANDROID_API}-clang++
LD=$CC
AS=$CC
ARCH=x86
CPU=i686
PREFIX=./build/$CPU
ADDITIONAL_CONFIGURE_FLAG="--disable-x86asm --disable-inline-asm --disable-asm"
OPTIMIZE_CFLAGS="-march=$CPU"
build_one

# Copy headers to all platform-specific folders
cp libavformat/dv.h build/arm64-v8a/include/libavformat/dv.h
cp libavformat/isom.h build/arm64-v8a/include/libavformat/isom.h
cp libavformat/dv.h build/armv7-a/include/libavformat/dv.h
cp libavformat/isom.h build/armv7-a/include/libavformat/isom.h
cp libavformat/dv.h build/i686/include/libavformat/dv.h
cp libavformat/isom.h build/i686/include/libavformat/isom.h
cp libavformat/dv.h build/x86_64/include/libavformat/dv.h
cp libavformat/isom.h build/x86_64/include/libavformat/isom.h

popd