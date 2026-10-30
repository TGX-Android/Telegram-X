#!/bin/bash
# Installs Rust of version.rust of version.properties with its targets of Android by rustup, which
# builds tlottie, and prints the value of rust.dir for local.properties. Run it again after a bump
# of version.rust to install the new version.
set -e
# shellcheck source=set-env.sh
IGNORE_SDK=1 source "$(dirname "$0")"/set-env.sh

if ! command -v rustup > /dev/null; then
  echo -e "${STYLE_ERROR}rustup not found, e.g. on Ubuntu: # apt install rustup${STYLE_END}" >&2
  exit 1
fi

RUST_VERSION=$(scripts/./read-property.sh version.properties version.rust)
TARGETS=aarch64-linux-android,armv7-linux-androideabi,i686-linux-android,x86_64-linux-android

rustup toolchain install "$RUST_VERSION" --profile minimal --target "$TARGETS"

RUST_DIR=$(rustup show home)
TOOLCHAIN=$(find "$RUST_DIR/toolchains" -maxdepth 1 -name "$RUST_VERSION-*" | head -n 1)
if [ -z "$TOOLCHAIN" ]; then
  echo -e "${STYLE_ERROR}Rust $RUST_VERSION not found in $RUST_DIR/toolchains${STYLE_END}" >&2
  exit 1
fi
INSTALLED=$("$TOOLCHAIN/bin/rustc" --version | cut -d ' ' -f 2)
if [ "$INSTALLED" != "$RUST_VERSION" ]; then
  echo -e "${STYLE_ERROR}rustc $INSTALLED in $TOOLCHAIN instead of $RUST_VERSION${STYLE_END}" >&2
  exit 1
fi
for TARGET in ${TARGETS//,/ }; do
  test -d "$TOOLCHAIN/lib/rustlib/$TARGET" || {
    echo -e "${STYLE_ERROR}Target $TARGET not found in $TOOLCHAIN${STYLE_END}" >&2
    exit 1
  }
done

echo "Rust $RUST_VERSION is installed with targets $TARGETS"
echo -e "${STYLE_INFO}rust.dir=$RUST_DIR${STYLE_END}"
