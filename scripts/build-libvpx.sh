#!/bin/bash
set -e
# shellcheck source=set-env.sh
source "$(dirname "$0")"/set-env.sh

pushd app/jni/thirdparty
build-libvpx-impl.sh || echo "libvpx build failed"
popd