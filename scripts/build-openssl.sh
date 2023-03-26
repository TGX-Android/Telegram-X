#!/bin/bash
set -e
# shellcheck source=set-env.sh
source "$(dirname "$0")"/set-env.sh

build-openssl-impl.sh || (echo "openssl build failed" && exit 1)