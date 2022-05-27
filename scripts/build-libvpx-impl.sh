#!/bin/bash
set -e

function validate_file {
  test -f "$1" || (echo "File not found: $1" && false)
}
function validate_dir {
  test -d "$1" || (echo "Directory not found: $1" && false)
}

function checkPreRequisites {
  test "$BUILD_PLATFORM" && echo "build platform: ${BUILD_PLATFORM}"
  test "$CPU_COUNT" && echo "parallel jobs: ${CPU_COUNT}"

  if ! [ -d "libvpx" ] || ! [ "$(ls -A libvpx)" ]; then
    echo -e "${STYLE_ERROR}Failed! Submodule 'libvpx' not found!${STYLE_END}"
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

## build process

# configuration

PREBUILT=$ANDROID_NDK/toolchains/llvm/prebuilt/$BUILD_PLATFORM
SYSROOT=$PREBUILT/sysroot
ABIS=("armeabi-v7a" "arm64-v8a" "x86" "x86_64")
CFLAGS_="-DANDROID -fpic -fpie"
LDFLAGS_=""

# the function itself

configure_abi() {
  case ${ABI} in
	  armeabi-v7a)
      ANDROID_API=16
      TARGET="armv7-android-gcc --enable-neon --disable-neon-asm"
      NDK_ABIARCH="armv7a-linux-androideabi"
      CFLAGS="${CFLAGS_} -Os -march=armv7-a -mfloat-abi=softfp -mfpu=neon -mtune=cortex-a8 -mthumb -D__thumb__"
      LDFLAGS="${LDFLAGS_} -march=armv7-a"
      ASFLAGS=""
      CPU=armv7-a
      export CROSS_PREFIX=${PREBUILT}/bin/arm-linux-androideabi-
    ;;
    arm64-v8a)
      ANDROID_API=21
      TARGET="arm64-android-gcc"
      NDK_ABIARCH="aarch64-linux-android"
      CFLAGS="${CFLAGS_} -O3 -march=armv8-a"
      LDFLAGS="${LDFLAGS_}"
      ASFLAGS=""
      CPU=arm64-v8a
      export CROSS_PREFIX=${PREBUILT}/bin/${NDK_ABIARCH}-
    ;;
    x86)
      ANDROID_API=16
      TARGET="x86-android-gcc"
      NDK_ABIARCH="i686-linux-android"
      CFLAGS="${CFLAGS_} -O3 -march=i686 -mtune=i686 -msse3 -mfpmath=sse -m32 -fPIC"
      LDFLAGS="-m32"
      ASFLAGS="-D__ANDROID__"
      CPU=i686
      export CROSS_PREFIX=${PREBUILT}/bin/${NDK_ABIARCH}-
    ;;
    x86_64)
      ANDROID_API=21
      TARGET="x86_64-android-gcc"
      NDK_ABIARCH="x86_64-linux-android"
      CFLAGS="${CFLAGS_} -O3 -march=x86-64 -mtune=x86-64 -msse4.2 -mpopcnt -m64 -fPIC"
      LDFLAGS=""
      ASFLAGS="-D__ANDROID__"
      CPU=x86_64
      export CROSS_PREFIX=${PREBUILT}/bin/${NDK_ABIARCH}-
    ;;
  esac

  export CFLAGS="${CFLAGS}"
  export CPPFLAGS="${CFLAGS}"
  export CXXFLAGS="${CFLAGS} -std=c++11"
  export ASFLAGS="${ASFLAGS}"
  export LDFLAGS="${LDFLAGS} -L${SYSROOT}/usr/lib"

  export CROSS_PREFIX_CLANG=${PREBUILT}/bin/${NDK_ABIARCH}${ANDROID_API}-

  export PATH=${PREBUILT}/bin:$PATH

  export AR="${PREBUILT}/bin/llvm-ar"
  export CC="${CROSS_PREFIX_CLANG}clang"
  export CXX="${CROSS_PREFIX_CLANG}clang++"
  export CPP="$CXX"
  export AS="$CC"
  export LD="$CC"
  export STRIP="${PREBUILT}/bin/llvm-strip"
  export RANLIB="${PREBUILT}/bin/llvm-ranlib"
  export NM="${PREBUILT}/bin/llvm-nm"

  validate_file "$AR"
  validate_file "$CC"
  validate_file "$CXX"
  validate_file "$CPP"
  validate_file "$AS"
  validate_file "$LD"
  validate_file "$STRIP"
  validate_file "$RANLIB"
  validate_file "$NM"

  PREFIX=./build/$CPU
}

configure_make() {
  pushd "libvpx" || exit

  ABI=$1;
  echo -e "${STYLE_INFO}- libvpx build started for ${ABI}${STYLE_END}"
  configure_abi

  make clean || echo -e "[info] running configure for the first time"

  CPU_DETECT="--disable-runtime-cpu-detect"
  if [[ $1 =~ x86.* ]]; then
    CPU_DETECT="--enable-runtime-cpu-detect"
  fi

  ./configure \
    --libc=${SYSROOT} \
    --prefix=${PREFIX} \
    --target=${TARGET} \
    ${CPU_DETECT} \
    --as=auto \
    --disable-docs \
    --enable-pic \
    --enable-libyuv \
    --enable-static \
    --enable-small \
    --enable-optimizations \
    --enable-better-hw-compatibility \
    --enable-realtime-only \
    --enable-vp8 \
    --enable-vp9 \
    --disable-webm-io \
    --disable-examples \
    --disable-tools \
    --disable-debug \
    --disable-unit-tests || exit 1

  make -j"$CPU_COUNT" install
  popd || true
}

for ((i=0; i < ${#ABIS[@]}; i++))
do
  configure_make "${ABIS[i]}"
  echo -e "${STYLE_INFO}- libvpx build ended for ${ABIS[i]}${STYLE_END}"
done

echo -e "${STYLE_INFO}- libvpx build done!${STYLE_END}"