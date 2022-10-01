#!/bin/bash
set -e
# shellcheck source=set-env.sh
source "$(dirname "$0")"/set-env.sh

run-cmake-impl.sh "$THIRDPARTY_LIBRARIES/boringssl" || (echo "boringssl build failed" && exit 1)