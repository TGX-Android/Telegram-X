#!/usr/bin/env bash
# Builds a flavor with test hooks for the measurements of this folder, with the marks of the start
# of start-marks.patch, which stay out of the code of the app so that no release has them: the
# patch is applied to the clone for the time of the build and taken back after, also when the
# build fails. Prints the path of the APK.
# Usage: tests/legacy/build.sh <flavor> [arguments of Gradle], e.g.
# tests/legacy/build.sh legacy -Pabi=arm32 -PuseLegacyNdk=true
set -uo pipefail
cd "$(dirname "$0")/../.." || exit 1
FLAVOR=$1
shift
PATCH=tests/legacy/start-marks.patch
git apply "$PATCH" || { echo "build.sh: $PATCH doesn't apply to the clone" >&2; exit 1; }
trap 'git apply -R "$PATCH"' EXIT
TASK=":app:assemble$(echo "${FLAVOR:0:1}" | tr a-z A-Z)${FLAVOR:1}BenchmarkRelease"
./gradlew -q "$TASK" "$@" || exit 1
ls -t app/build/outputs/apk/"$FLAVOR"/benchmarkRelease/*.apk | head -1
