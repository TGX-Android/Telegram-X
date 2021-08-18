#!/bin/bash
set -e

export IGNORE_SDK=true
# shellcheck source=set-env.sh
source "$(pwd)/scripts/set-env.sh"

echo "Executing on Docker image: /bin/bash $1"
keystore_dir=$(dirname "$(scripts/./read-property.sh local.properties keystore.file)")

test -d "$keystore_dir" || (echo "keystore.file not found!" && exit 1)
test -f Dockerfile || (echo "Dockerfile not found!" && exit 1)

docker run \
  -v "$(pwd):/project" \
  -v "$keystore_dir:$keystore_dir:ro" \
  -w /project tgx-android /bin/bash $1
echo "Done!"
