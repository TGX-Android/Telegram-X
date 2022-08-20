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

# Configure libvpx
#pushd app/jni/thirdparty/libvpx
#configure-libvpx.sh
#popd
# UPD: No longer needed after switching to unified libvpx

# Patch opus
pushd app/jni/thirdparty/opus
patch-opus.sh
popd

# Disable building webp executables
#pushd app/jni/thirdparty/webp
#sed -i.bak -E 's/(^include \$\(WEBP_SRC_PATH\)\/(imageio|examples)\/Android\.mk)/# \1/g' Android.mk
#popd
# UPD: No longer needed after switching to CMake

# Patch ExoPlayer sources
patch-exoplayer.sh

# Build and configure libvpx
pushd app/jni/thirdparty
build-libvpx-impl.sh
popd

# Build and configure ffmpeg
pushd app/jni/thirdparty
build-ffmpeg-impl.sh
popd

# == Copy local.properties ===

if [[ ! -f local.properties ]]; then
  setup-properties.sh
fi

echo -e "${STYLE_INFO}Configure finished!${STYLE_END}"

