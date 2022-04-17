#!/bin/bash
set -e
# shellcheck source=set-env.sh
source "$(dirname "$0")"/set-env.sh

pushd app/jni/thirdparty
build-ffmpeg-impl.sh || echo "Build failed"
popd