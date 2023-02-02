#!/bin/bash
set -e
# shellcheck source=set-env.sh
source "$(dirname "$0")"/set-env.sh

build-vpx-impl.sh || (echo "vpx build failed" && exit 1)