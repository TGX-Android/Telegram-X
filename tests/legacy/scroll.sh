#!/usr/bin/env bash
# Frames of scrolling of the chat list of the installed build with test hooks by adb, for phones
# whose Android tests of tests/ don't support, e.g. 4.2 (API 17). Turns on debug.hwui.profile,
# starts the app, waits for the chats and makes flings of the same length, down and up in turn.
# dumpsys gfxinfo of Android 4.1-4.3 clears the times of frames that it prints, so a dump before
# each fling and one after it give the frames of that fling, at most 128, each the sum of Draw,
# Process and Execute; from Android 6.0 on, gfxinfo is reset before each fling and its framestats
# give each frame from its vsync to its completion, at most 120. Writes flings.tsv into the output
# folder, a line for each fling, and prints the totals: frames, their mean, 90th percentile and
# longest time, and frames over BUDGET ms, one refresh of the screen, 16.7 by default, e.g. 8.3 at
# 120 Hz. The property is set back after, to false when it wasn't set, since it can't be unset
# without root.
# Usage: ANDROID_SERIAL=<device> [BUDGET=8.3] tests/legacy/scroll.sh <flings> <output folder>
#   [x y1 y2]
set -uo pipefail
N=$1
OUT=$2
X=${3:-240}
Y1=${4:-650}
Y2=${5:-150}
PKG=org.example.tgx
BUDGET=${BUDGET:-16.67}
mkdir -p "$OUT"
sh() { adb shell "$@" < /dev/null | tr -d '\r'; }
SDK=$(sh getprop ro.build.version.sdk)
DIR=$(dirname "$0")
before=$(sh getprop debug.hwui.profile)
trap 'sh setprop debug.hwui.profile "${before:-false}"' EXIT
sh setprop debug.hwui.profile true
sh am force-stop $PKG
sh am start -W -n $PKG/org.thunderdog.challegram.MainActivity > /dev/null
sleep 25
: > "$OUT/frames.txt"
: > "$OUT/flings.tsv"
for i in $(seq "$N"); do
  if ((SDK >= 23)); then
    sh dumpsys gfxinfo $PKG reset > /dev/null
  else
    sh dumpsys gfxinfo $PKG > /dev/null
  fi
  if ((i % 2)); then
    sh input swipe "$X" "$Y1" "$X" "$Y2"
  else
    sh input swipe "$X" "$Y2" "$X" "$Y1"
  fi
  sleep 3
  if ((SDK >= 23)); then
    sh dumpsys gfxinfo $PKG framestats | awk -F, -f "$DIR/framestats.awk" > "$OUT/fling.txt"
  else
    sh dumpsys gfxinfo $PKG | awk -f "$DIR/profile.awk" > "$OUT/fling.txt"
  fi
  cat "$OUT/fling.txt" >> "$OUT/frames.txt"
  awk -v i="$i" -v budget="$BUDGET" -f "$DIR/frames.awk" "$OUT/fling.txt" | tee -a "$OUT/flings.tsv"
done
sh am force-stop $PKG
awk -v i=all -v budget="$BUDGET" -f "$DIR/frames.awk" "$OUT/frames.txt" | tee "$OUT/total.tsv"
