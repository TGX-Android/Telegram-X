#!/bin/bash
set -e
# shellcheck source=set-env.sh
source "$(pwd)/scripts/set-env.sh"

patch-exoplayer-impl.sh || (echo "ExoPlayer patch failed" && exit 1)