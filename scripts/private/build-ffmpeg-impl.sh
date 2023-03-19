#!/bin/bash
set -e

function build_one {
  LIBVPX_INCLUDE_DIR="$THIRDPARTY_LIBRARIES/libvpx/build/$CPU/include"
  LIBVPX_LIB_DIR="$THIRDPARTY_LIBRARIES/libvpx/build/$CPU/lib"

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

  LIBS=${PREBUILT}/lib64/clang/12.0.9/lib/linux
  validate_dir "$LIBS"

  echo "Cleaning..."
  rm -f config.h
  make clean || true

  echo "Configuring... ${NDK}"

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
  --extra-libs="-lunwind $EXTRA_LIBS" \
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

pushd "$THIRDPARTY_LIBRARIES" > /dev/null
echo "Checking pre-requisites..."
checkPreRequisites
popd > /dev/null

## common

PREBUILT=$ANDROID_NDK/toolchains/llvm/prebuilt/$BUILD_PLATFORM
SYSROOT=$PREBUILT/sysroot

validate_dir "$PREBUILT"
validate_dir "$SYSROOT"

pushd "$THIRDPARTY_LIBRARIES/ffmpeg"

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
EXTRA_LIBS=""
EXTRA_LDFLAGS=""
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
EXTRA_LIBS=""
EXTRA_LDFLAGS=""
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
OPTIMIZE_CFLAGS="-marm -march=$CPU -mfloat-abi=softfp"
EXTRA_LDFLAGS=-L${PREBUILT}/lib64/clang/12.0.9/lib/linux
EXTRA_LIBS=-lclang_rt.builtins-arm-android
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
EXTRA_LDFLAGS=-L${PREBUILT}/lib64/clang/12.0.9/lib/linux
EXTRA_LIBS=-lclang_rt.builtins-i686-android
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