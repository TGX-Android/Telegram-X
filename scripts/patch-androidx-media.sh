#!/bin/bash
set -e
# shellcheck source=set-env.sh
source "$(pwd)/scripts/set-env.sh"

patch-androidx-media-impl.sh || (echo "androidx-media patch failed" && exit 1)