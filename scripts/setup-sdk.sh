g#!/bin/bash
set -e
# shellcheck source=set-env.sh
source "$(dirname "$0")"/set-env.sh "$1"

# Fetching SDK
echo "Checking Android SDK: ${ANDROID_SDK_ROOT}..." && (test -d "$ANDROID_SDK_ROOT" || mkdir -p "$ANDROID_SDK_ROOT")

# Fetching cmdline-tools
CMDLINE_VERSION=$(read-property.sh version.properties version.cmdline_tools)

pushd "$ANDROID_SDK_ROOT"
if [ ! -d "$ANDROID_SDK_ROOT/cmdline-tools/latest" ]; then
  test -f cmdline-tools.zip || (echo "Downloading cmdline-tools..." && wget -O cmdline-tools.zip "https://dl.google.com/android/repository/commandlinetools-${PLATFORM}-${CMDLINE_VERSION}_latest.zip")
  echo "Installing cmdline-tools..."
  unzip cmdline-tools.zip -d cmdline-tools
  mv -f cmdline-tools/cmdline-tools cmdline-tools/latest
fi
popd

# Downloading packages
BUILD_TOOLS_VERSION=$(read-property.sh version.properties version.build_tools)
NDK_VERSION=$(read-property.sh version.properties version.ndk)
COMPILE_SDK_VERSION=$(read-property.sh version.properties version.sdk_compile)
CMAKE_VERSION=$(read-property.sh version.properties version.cmake)

yes | "$ANDROID_SDK_ROOT"/cmdline-tools/latest/bin/sdkmanager --licenses
yes | "$ANDROID_SDK_ROOT"/cmdline-tools/latest/bin/sdkmanager --update
yes | "$ANDROID_SDK_ROOT"/cmdline-tools/latest/bin/sdkmanager --install \
  "platforms;android-$COMPILE_SDK_VERSION" \
  "build-tools;$BUILD_TOOLS_VERSION" \
  "ndk;$NDK_VERSION" \
  "cmake;$CMAKE_VERSION"

test -d "$ANDROID_SDK_ROOT" || (echo "ANDROID_SDK_ROOT ($ANDROID_SDK_ROOT) not found!" && exit 1)
test -d "$ANDROID_NDK" || (echo "ANDROID_NDK ($ANDROID_NDK) not found!" && exit 1)

echo "SDK setup is now complete!"