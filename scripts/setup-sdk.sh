#!/bin/bash
set -e
# shellcheck source=set-env.sh
source "$(dirname "$0")"/set-env.sh "$1"

# Fetching SDK
echo "Checking Android SDK: ${ANDROID_SDK_ROOT}..." && (test -d "$ANDROID_SDK_ROOT" || mkdir -p "$ANDROID_SDK_ROOT")

# Fetching android CLI
if ! command -v android >/dev/null 2>&1; then
  echo "Installing android CLI..."
  HOST_ARCH="$(uname -m)"
  case "$PLATFORM-$HOST_ARCH" in
    mac-arm64|mac-aarch64) CLI_PLATFORM=darwin_arm64 ;;
    mac-x86_64) CLI_PLATFORM=darwin_x86_64 ;;
    linux-x86_64) CLI_PLATFORM=linux_x86_64 ;;
    *) echo "android CLI has no installer for platform: $PLATFORM ($HOST_ARCH)" && exit 1 ;;
  esac
  curl -fsSL "https://dl.google.com/android/cli/latest/${CLI_PLATFORM}/install.sh" | bash
  PATH="$HOME/.local/bin:$PATH"
fi

# Downloading packages
BUILD_TOOLS_VERSION=$(read-property.sh version.properties version.build_tools)
COMPILE_SDK_VERSION=$(read-property.sh version.properties version.sdk_compile)
SDK_PACKAGE=$(read-property.sh version.properties version.sdk_package)
CMAKE_VERSION=$(read-property.sh version.properties version.cmake)
ANDROID_NDK_VERSION_PRIMARY=$(read-property.sh version.properties version.ndk_primary)
ANDROID_NDK_VERSION_LEGACY=$(read-property.sh version.properties version.ndk_legacy)

android --no-metrics --sdk="$ANDROID_SDK_ROOT" sdk install \
  "platforms/$SDK_PACKAGE" \
  "build-tools/$BUILD_TOOLS_VERSION" \
  "ndk/$ANDROID_NDK_VERSION_PRIMARY" \
  "ndk/$ANDROID_NDK_VERSION_LEGACY" \
  "cmake/$CMAKE_VERSION"

test -d "$ANDROID_SDK_ROOT" || (echo "ANDROID_SDK_ROOT ($ANDROID_SDK_ROOT) not found!" && exit 1)
test -d "$ANDROID_SDK_ROOT/ndk/$ANDROID_NDK_VERSION_PRIMARY" || (echo "ANDROID_NDK ($ANDROID_NDK_VERSION_PRIMARY) not found!" && exit 1)
test -d "$ANDROID_SDK_ROOT/ndk/$ANDROID_NDK_VERSION_LEGACY" || (echo "ANDROID_NDK ($ANDROID_NDK_VERSION_LEGACY) not found!" && exit 1)

echo "SDK setup is now complete!"
echo "build-tools: ${BUILD_TOOLS_VERSION}, ndk_primary: ${ANDROID_NDK_VERSION_PRIMARY}, ndk_legacy: ${ANDROID_NDK_VERSION_LEGACY}"