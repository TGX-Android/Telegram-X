#!/bin/bash
set -e

# == Setup SDK & NDK ==
if [[ "$1" == "--skip-sdk-setup" ]]; then
  # shellcheck source=set-env.sh
  source "$(pwd)/scripts/set-env.sh"
else
  # shellcheck source=setup-sdk.sh
  source "$(pwd)/scripts/setup-sdk.sh"
fi

if [[ -f local.properties ]]; then
  echo -e "${STYLE_INFO}local.properties already exists. Cleaning...${STYLE_END}"
  reset.sh
fi

# == Setup thirdparty libraries ==

if [ "$WIN_PATCH_REQUIRED" = true ]; then
  patch-windows-impl.sh
fi

# Patch opus
patch-opus-impl.sh

# Patch ExoPlayer sources
patch-exoplayer-impl.sh

# Build boringssl
run-cmake-impl.sh "$THIRDPARTY_LIBRARIES/boringssl"

# Build and configure libvpx
build-vpx-impl.sh

# Build and configure ffmpeg
build-ffmpeg-impl.sh

# == Copy local.properties ===

if [[ ! -f local.properties ]]; then
  setup-properties.sh
fi

echo -e "${STYLE_INFO}Configure finished!${STYLE_END}"

