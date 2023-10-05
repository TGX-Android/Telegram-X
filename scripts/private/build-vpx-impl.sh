#!/bin/bash
set -e

function checkPreRequisites {
  test "$BUILD_PLATFORM" && echo "build platform: ${BUILD_PLATFORM}"
  test "$CPU_COUNT" && echo "parallel jobs: ${CPU_COUNT}"

  if ! [ -d "libvpx" ] || ! [ "$(ls -A libvpx)" ]; then
    echo -e "${STYLE_ERROR}Failed! Submodule 'libvpx' not found!${STYLE_END}"
    echo -e "${STYLE_ERROR}Try to run: 'git submodule init && git submodule update --init --recursive'${STYLE_END}"
    exit
  fi

  if [ -z "$ANDROID_SDK_ROOT" -a "$ANDROID_SDK_ROOT" == "" ]; then
    echo -e "${STYLE_ERROR}Failed! ANDROID_SDK_ROOT is empty. Run 'export ANDROID_SDK_ROOT=[PATH_TO_SDK]'${STYLE_END}"
    exit
  fi

  validate_dir "$ANDROID_SDK_ROOT"
  test "$CPU_COUNT"
}

pushd "$THIRDPARTY_LIBRARIES" > /dev/null
echo "Checking pre-requisites..."
checkPreRequisites
popd > /dev/null

## build process

pushd "$THIRDPARTY_LIBRARIES/libvpx"

# the function itself

configure_abi() {
  CFLAGS_="-DANDROID -fpic -fpie"
  LDFLAGS_=""
  case ${ABI} in
    arm64-v8a)
      ANDROID_NDK_VERSION=$ANDROID_NDK_VERSION_PRIMARY
      ANDROID_API=21
      TARGET="arm64-android-gcc"
      NDK_ABIARCH="aarch64-linux-android"
      CFLAGS="${CFLAGS_} -O3 -march=armv8-a"
      LDFLAGS="${LDFLAGS_}"
      ASFLAGS=""
      CPU=arm64-v8a
    ;;
    x86_64)
      ANDROID_NDK_VERSION=$ANDROID_NDK_VERSION_PRIMARY
      ANDROID_API=21
      TARGET="x86_64-android-gcc"
      NDK_ABIARCH="x86_64-linux-android"
      CFLAGS="${CFLAGS_} -O3 -march=x86-64 -msse4.2 -mpopcnt -m64 -fPIC"
      LDFLAGS=""
      ASFLAGS="-D__ANDROID__"
      CPU=x86_64
    ;;
	  armeabi-v7a)
      ANDROID_NDK_VERSION=$ANDROID_NDK_VERSION_LEGACY
      ANDROID_API=16
      TARGET="armv7-android-gcc --enable-neon --disable-neon-asm"
      NDK_ABIARCH="armv7a-linux-androideabi"
      CFLAGS="${CFLAGS_} -Os -march=armv7-a -marm -mfloat-abi=softfp -mfpu=neon -mthumb -D__thumb__"
      LDFLAGS="${LDFLAGS_}"
      ASFLAGS=""
      CPU=armv7-a
    ;;
    x86)
      ANDROID_NDK_VERSION=$ANDROID_NDK_VERSION_LEGACY
      ANDROID_API=16
      TARGET="x86-android-gcc"
      NDK_ABIARCH="i686-linux-android"
      CFLAGS="${CFLAGS_} -O3 -march=i686 -msse3 -mfpmath=sse -m32 -fPIC"
      LDFLAGS="-m32"
      ASFLAGS="-D__ANDROID__"
      CPU=i686
    ;;
  esac

  ANDROID_NDK_ROOT="$ANDROID_SDK_ROOT/ndk/$ANDROID_NDK_VERSION"
  PREBUILT="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/$BUILD_PLATFORM"
  SYSROOT="$PREBUILT/sysroot"

  validate_dir "$ANDROID_NDK_ROOT"
  echo "${STYLE_INFO}- Using NDK ${ANDROID_NDK_ROOT}${STYLE_END}"

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
}

for ABI in x86 armeabi-v7a x86_64 arm64-v8a ; do
  configure_make "$ABI"
  echo -e "${STYLE_INFO}- libvpx build ended for ${ABI}${STYLE_END}"
done

popd

echo -e "${STYLE_INFO}- libvpx build done!${STYLE_END}"