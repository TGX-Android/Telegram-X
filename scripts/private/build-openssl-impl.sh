#!/bin/bash
set -e

# Credits:
# https://github.com/tdlib/td/blob/master/example/android/build-openssl.sh

OPENSSL_DIR="$THIRDPARTY_LIBRARIES/openssl"
OPENSSL_INSTALL_DIR="$OPENSSL_DIR/android_static"

if [ -e "$OPENSSL_INSTALL_DIR" ] ; then
  rm -rf "$OPENSSL_INSTALL_DIR"
fi

mkdir -p $OPENSSL_INSTALL_DIR || exit 1

pushd "$OPENSSL_DIR" > /dev/null

export ANDROID_NDK_ROOT=$ANDROID_NDK  # for OpenSSL 3.0
export ANDROID_NDK_HOME=$ANDROID_NDK_ROOT                           # for OpenSSL 1.1.1
PATH=$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/$BUILD_PLATFORM/bin:$PATH

if ! clang --help >/dev/null 2>&1 ; then
  echo "Error: failed to run clang from Android NDK."
  if [[ "$OS_NAME" == "linux" ]] ; then
    echo "Prebuilt Android NDK binaries are linked against glibc, so glibc must be installed."
  fi
  exit 1
fi

ANDROID_API32=16
ANDROID_API64=21

for ABI in arm64-v8a armeabi-v7a x86_64 x86 ; do
  if [[ $ABI == "x86" ]] ; then
    ./Configure android-x86 no-shared -U__ANDROID_API__ -D__ANDROID_API__=$ANDROID_API32 || exit 1
  elif [[ $ABI == "x86_64" ]] ; then
    ./Configure android-x86_64 no-shared -U__ANDROID_API__ -D__ANDROID_API__=$ANDROID_API64 || exit 1
  elif [[ $ABI == "armeabi-v7a" ]] ; then
    ./Configure android-arm no-shared -U__ANDROID_API__ -D__ANDROID_API__=$ANDROID_API32 -D__ARM_MAX_ARCH__=8 || exit 1
  elif [[ $ABI == "arm64-v8a" ]] ; then
    ./Configure android-arm64 no-shared -U__ANDROID_API__ -D__ANDROID_API__=$ANDROID_API64 || exit 1
  fi

  sed -i.bak 's/-O3/-O3 -ffunction-sections -fdata-sections/g' Makefile || exit 1

  make depend -s || exit 1
  make -j4 -s || exit 1

  mkdir -p $OPENSSL_INSTALL_DIR/$ABI/lib/ || exit 1
  cp libcrypto.a libssl.a $OPENSSL_INSTALL_DIR/$ABI/lib/ || exit 1
  cp -r include $OPENSSL_INSTALL_DIR/$ABI/ || exit 1

  make distclean || exit 1
done

popd > /dev/null