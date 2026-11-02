#!/usr/bin/env bash
# Installs a build with test hooks on a phone whose Android tests of tests/ don't support, e.g. 4.2
# (API 17), and restores a snapshot of accounts in it: the app takes it from its external files at
# its first start, and the copy on the phone is deleted right after, as tests delete theirs.
# An install that waits for a dialog of another app stops after 5 minutes and prints the focus.
# Usage: ANDROID_SERIAL=<device> tests/legacy/install.sh <apk> <snapshot.zip>
set -uo pipefail
APK=$1
SNAPSHOT=$2
PKG=org.example.tgx
sh() { adb shell "$@" < /dev/null | tr -d '\r'; }
if [ -n "$(sh pm path org.thunderdog.challegram)" ]; then
  echo "install.sh: Telegram X of the user is on the phone, nothing is done" >&2
  exit 1
fi
sh pm uninstall $PKG > /dev/null
adb install "$APK" < /dev/null > /dev/null 2>&1 &
pid=$!
waited=0
while kill -0 $pid 2> /dev/null && ((waited < 300)); do
  sleep 5
  waited=$((waited + 5))
done
if kill -0 $pid 2> /dev/null; then
  kill $pid
  focus=$(sh dumpsys window windows | grep -m1 mCurrentFocus)
  echo "install.sh: stopped after 5 minutes, $focus" >&2
  exit 1
fi
wait $pid || { echo "install.sh: install failed" >&2; exit 1; }
# The app takes the snapshot from its media folder, or from its external files before Android 5.0
if (($(sh getprop ro.build.version.sdk) >= 21)); then
  DIR=/sdcard/Android/media/$PKG/tgx-session
else
  DIR=/sdcard/Android/data/$PKG/files/tgx-session
fi
sh mkdir -p $DIR
adb push "$SNAPSHOT" $DIR/snapshot.zip > /dev/null 2>&1
sh am start -n $PKG/org.thunderdog.challegram.MainActivity > /dev/null
sleep 30
sh rm $DIR/snapshot.zip
sh am force-stop $PKG
echo "installed ${APK##*/}, snapshot restored and deleted: $(sh ls $DIR | wc -l) files left in $DIR"
