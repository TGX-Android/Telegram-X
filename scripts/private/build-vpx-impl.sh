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
  CPUFEATURES_DIR="$ANDROID_SDK_ROOT/ndk/$ANDROID_NDK_VERSION/sources/android/cpufeatures"
  CFLAGS_="-DANDROID -fpic -fpie"
  LDFLAGS_=""
  case ${FLAVOR} in
    latest)
      ANDROID_API=23
    ;;
    lollipop)
      ANDROID_API=21
    ;;
    legacy)
      ANDROID_API=16
    ;;
  esac
  case ${ABI} in
    arm64-v8a)
      ANDROID_NDK_VERSION=$ANDROID_NDK_VERSION_PRIMARY
      TARGET="arm64-android-gcc"
      NDK_ABIARCH="aarch64-linux-android"
      CFLAGS="${CFLAGS_} -O3 -march=armv8-a -I${CPUFEATURES_DIR}"
      LDFLAGS="${LDFLAGS_}"
      ASFLAGS=""
      CPU=arm64-v8a
    ;;
	  armeabi-v7a)
      ANDROID_NDK_VERSION=$ANDROID_NDK_VERSION_LEGACY
      TARGET="armv7-android-gcc --enable-neon --disable-neon-asm"
      NDK_ABIARCH="armv7a-linux-androideabi"
      CFLAGS="${CFLAGS_} -Os -march=armv7-a -marm -mfloat-abi=softfp -mfpu=neon -mthumb -D__thumb__ -I${CPUFEATURES_DIR}"
      LDFLAGS="${LDFLAGS_}"
      ASFLAGS=""
      CPU=armv7-a
    ;;
    x86_64)
      ANDROID_NDK_VERSION=$ANDROID_NDK_VERSION_PRIMARY
      TARGET="x86_64-android-gcc"
      NDK_ABIARCH="x86_64-linux-android"
      CFLAGS="${CFLAGS_} -O3 -march=x86-64 -msse4.2 -mpopcnt -m64 -fPIC"
      LDFLAGS=""
      ASFLAGS="-D__ANDROID__"
      CPU=x86_64
    ;;
    x86)
      ANDROID_NDK_VERSION=$ANDROID_NDK_VERSION_LEGACY
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

  export PREFIX=./build/$FLAVOR/$CPU
}

configure_make() {
  FLAVOR=$1
  ABI=$2

  echo -e "${STYLE_INFO}- libvpx build started for ${FLAVOR}-${ABI}${STYLE_END}"
  configure_abi

  make clean || echo -e "[info] running configure for the first time"

  if [[ $ABI == "arm64-v8a" || $ABI == "armeabi-v7a" ]]; then
    CPU_DETECT="--enable-runtime-cpu-detect"
  else
    CPU_DETECT="--disable-runtime-cpu-detect"
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

for ABI in arm64-v8a armeabi-v7a x86_64 x86 ; do
  for FLAVOR in latest lollipop legacy ; do
    if [[ "$FLAVOR" != "legacy" || $ABI == "armeabi-v7a" || $ABI == "x86" ]]; then
      echo -e "${STYLE_INFO}- libvpx build start: ${ABI} ${FLAVOR}${STYLE_END}"
      configure_make "$FLAVOR" "$ABI"
      echo -e "${STYLE_INFO}- libvpx build finish: ${ABI} ${FLAVOR}${STYLE_END}"
    fi
  done
done

popd

echo -e "${STYLE_INFO}- libvpx build done!${STYLE_END}"