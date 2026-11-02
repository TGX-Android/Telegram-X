#!/usr/bin/env bash
# Cold and warm starts of the installed build with test hooks by adb, for phones whose Android
# tests of tests/ don't support, e.g. 4.2 (API 17). A cold start stops the process first, a warm
# one leaves the app by back and keeps the process, after one cold start that isn't counted, e.g.
# the first after an install or a restore of a snapshot. Each start is timed by am start -W, a cold
# one also by the marks of StartMarks, logged until the chats are shown. Writes starts.tsv into the
# output folder and prints the median of each column.
# Usage: ANDROID_SERIAL=<device> tests/legacy/start.sh <starts of each kind> <output folder>
set -uo pipefail
N=$1
OUT=$2
PKG=org.example.tgx
ACTIVITY=$PKG/org.thunderdog.challegram.MainActivity
mkdir -p "$OUT"
sh() { adb shell "$@" < /dev/null | tr -d '\r'; }
: > "$OUT/starts.tsv"
sh am force-stop $PKG
sh am start -W -n $ACTIVITY > /dev/null
sleep 20
for kind in cold warm; do
  for i in $(seq "$N"); do
    if [ $kind = cold ]; then
      sh am force-stop $PKG
    else
      sh input keyevent 4
    fi
    sleep 5
    adb logcat -c
    total=$(sh am start -W -n $ACTIVITY | awk '/TotalTime/ { print $2 }')
    # The chats come after the first frame, and marks are logged once for each process
    sleep 15
    # Logcat of Android 4.2 has no format without the tag and the process, so it prints the brief
    # one on every version and strips its start
    marks=$(adb logcat -d -v brief -s StartMarks:I < /dev/null | tr -d '\r' \
      | sed -E 's/^[VDIWEF]\/StartMarks\( *[0-9]+\): //' \
      | awk '/ ms, / { sub(/ \+[0-9]+ ms, /, "="); sub(/ ms$/, ""); printf "\t%s", $0 }')
    printf '%s\t%s\tTotalTime=%s%s\n' $kind "$i" "$total" "$marks" | tee -a "$OUT/starts.tsv"
  done
done
sh am force-stop $PKG
awk -F'\t' -f "$(dirname "$0")/medians.awk" "$OUT/starts.tsv" | tee "$OUT/medians.tsv"
