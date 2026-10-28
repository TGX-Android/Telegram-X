#!/bin/bash
# Checks test tools after their changes in tests/core or tests/buildSrc: helpers of drawing,
# animations and scrolling, screenshots and recordings, check results before and after a change,
# benchmarks and their comparison, microbenchmarks and minSdk check. Checks and benchmarks run on
# connected test device, never on device with Telegram X, which may have real accounts.
# Smoke checks are in tests/<module>/tools. Checks of a change in tests/<module>/checks aren't
# built, so that the result depends only on the tools.
# describeTestToolsCheck task writes report to build/test-tools-check/report.md, where home path
# and device serial are replaced. Report notes when connection to the device was lost,
# which fails tests regardless of code.
#
# Usage: scripts/check-test-tools.sh [-y] [checks] [benchmark] [minsdk]
# Without steps runs all of them, -y skips confirmation.

# Keeps computer awake during long builds and tests
if [ -z "$TGX_AWAKE" ]; then
  export TGX_AWAKE=1
  if command -v caffeinate >/dev/null 2>&1; then
    exec caffeinate -i bash "$0" "$@"
  elif command -v systemd-inhibit >/dev/null 2>&1 &&
    systemd-inhibit --what=idle:sleep true >/dev/null 2>&1; then
    exec systemd-inhibit --what=idle:sleep --why="scripts/check-test-tools.sh" bash "$0" "$@"
  fi
fi

# shellcheck source=set-env.sh
source "$(pwd)/scripts/set-env.sh"

fail() {
  echo -e "${STYLE_ERROR}$*${STYLE_END}" >&2
  exit 1
}

ASSUME_YES=false
STEPS=""
for arg in "$@"; do
  case $arg in
    -y) ASSUME_YES=true ;;
    checks|benchmark|minsdk) STEPS="${STEPS:+$STEPS }$arg" ;;
    *) fail "Unknown argument: $arg. Steps: checks, benchmark, minsdk" ;;
  esac
done
[ -n "$STEPS" ] || STEPS="checks benchmark minsdk"

has_step() {
  case " $STEPS " in
    *" $1 "*) return 0 ;;
  esac
  return 1
}

OUT=build/test-tools-check
REPORT=$OUT/report.md
SCALE_NAMES="window_animation_scale transition_animation_scale animator_duration_scale"
DISABLED_SCALES=""
PACKAGES_BEFORE=""
FINISHED=false
STEP=""
PROGRESS=""
RUNNING=""

anonymize() {
  local patterns=(-e "s|$HOME|~|g")
  if [ -n "$USERPROFILE" ]; then
    patterns+=(-e "s|${USERPROFILE//\\//}|~|g" -e "s|${USERPROFILE//\\/\\\\}|~|g")
  fi
  if [ -n "$ANDROID_SERIAL" ]; then
    patterns+=(-e "s|$ANDROID_SERIAL|<serial>|g")
  fi
  sed "${patterns[@]}"
}

gradle() {
  ./gradlew --console=plain --continue -Ptgx.testTools -Ptgx.checks=false "$@"
}

run() {
  local name=$1
  shift
  echo
  echo -e "${STYLE_INFO}$name: $*${STYLE_END}"
  local start log_size
  start=$(date +%s)
  log_size=$(adb_log_size)
  if $NEEDS_DEVICE && [[ " $* " == *AndroidTest\ * ]]; then
    show_started_tests
  fi
  RUNNING=$name
  "$@" 2>&1 | tee "$OUT/$name.log"
  local code=${PIPESTATUS[0]}
  hide_started_tests
  printf '%s\t%s\t%s\t%s\t%s\n' "$name" "$STEP" "$code" "$(( $(date +%s) - start ))" "$*" \
    >> "$OUT/runs.tsv"
  RUNNING=""
  if $NEEDS_DEVICE; then
    save_disconnects "$name" "$log_size"
  fi
}

# Gradle prints tests on device only when all of them finish,
# test runner logs each test when it starts
show_started_tests() {
  local since
  since=$(adb_shell date +%s)
  # Stopped background job must not run traps of the script, e.g. cleanup
  (
    trap - EXIT INT TERM
    exec "$ADB" -s "$ANDROID_SERIAL" logcat -T "$since.000" -v tag -s TestRunner:I -e started:
  ) &
  PROGRESS=$!
}

hide_started_tests() {
  if [ -n "$PROGRESS" ]; then
    kill "$PROGRESS" 2>/dev/null || true
    wait "$PROGRESS" 2>/dev/null || true
    PROGRESS=""
  fi
}

adb_log_size() {
  if [ -f "$ADB_LOG" ]; then
    wc -l < "$ADB_LOG" | tr -d ' '
  else
    echo 0
  fi
}

save_disconnects() {
  local lines
  lines=$(
    tail -n "+$(( $2 + 1 ))" "$ADB_LOG" 2>/dev/null |
      grep -F -e "$ANDROID_SERIAL: connection terminated" -e 'Android Debug Bridge version' || true
  )
  if [ -n "$lines" ]; then
    echo -e "${STYLE_WARN}Connection to the device was lost during $1${STYLE_END}"
    printf '%s\n' "$lines" > "$OUT/$1.disconnects.txt"
  fi
}

# Next runs replace results of tests, so the report reads their copies
save_results() {
  local name=$1
  shift
  local directory
  for directory in "$@"; do
    if [ -d "$directory" ]; then
      mkdir -p "$OUT/$name-results/$directory"
      cp -R "$directory/." "$OUT/$name-results/$directory"
    fi
  done
}

adb_shell() {
  "$ADB" -s "$ANDROID_SERIAL" shell "$@" | tr -d '\r'
}

animation_scales() {
  local name
  for name in $SCALE_NAMES; do
    printf '%s=%s ' "$name" "$(adb_shell settings get global "$name")"
  done
}

# Test apps have namespaces of tests modules as packages, test app of app module adds .test
test_packages() {
  adb_shell pm list packages | sed -n 's/^package://p' |
    grep -E "^(${APP_ID//./\\.}|tgx\.test)(\.|$)" | sort
}

# Interrupted tests keep running on device until their apps are removed
remove_test_packages() {
  local package
  for package in $(comm -13 <(printf '%s\n' "$PACKAGES_BEFORE") <(test_packages)); do
    echo -e "${STYLE_WARN}Removing $package from $MODEL${STYLE_END}"
    "$ADB" -s "$ANDROID_SERIAL" uninstall "$package" > /dev/null
  done
}

# Interrupted tests leave screen recording and Perfetto trace of macrobenchmarks running,
# the trace until reboot, which may change results of next tests
stop_test_processes() {
  local name
  for name in screenrecord perfetto tracebox; do
    if [ -n "$(adb_shell pgrep -x "$name")" ]; then
      echo -e "${STYLE_WARN}Stopping $name on $MODEL${STYLE_END}"
      adb_shell pkill -INT -x "$name"
    fi
  done
}

# Report task fails when build logic of tests doesn't compile
write_fallback_report() {
  {
    cat "$OUT/header.md" 2>/dev/null
    printf '\n## describeTestToolsCheck failed\n\n```text\n'
    grep -vE '^> Task .* (UP-TO-DATE|SKIPPED|NO-SOURCE|FROM-CACHE)$' "$OUT/report-task.log" |
      tail -n 60
    printf '```\n\n## Runs\n\n| Run | Command | Exit code | Time |\n|---|---|---|---|\n'
    awk -F '\t' '{ printf "| `%s` | `%s` | %s | %s s |\n", $1, $5, $3, $4 }' "$OUT/runs.tsv" \
      2>/dev/null
  } | anonymize > "$REPORT"
}

cleanup() {
  # Report is written even when the device is disconnected
  set +e
  hide_started_tests
  if $NEEDS_DEVICE; then
    remove_test_packages
    stop_test_processes
    local name
    for name in $SCALE_NAMES; do
      adb_shell settings put global "$name" 1
    done
    local entry
    for entry in $DISABLED_SCALES; do
      adb_shell settings put global "${entry%%=*}" "${entry#*=}"
    done
  fi
  if ! $FINISHED; then
    echo "$RUNNING" > "$OUT/interrupted"
  fi
  echo
  local report_ok=true
  gradle describeTestToolsCheck > "$OUT/report-task.log" 2>&1 || report_ok=false
  local seconds=$(( $(date +%s) - STARTED ))
  local total="total time $(( seconds / 60 )) min $(( seconds % 60 )) s"
  if $report_ok; then
    echo -e "${STYLE_INFO}$(tail -n 1 "$REPORT"), $total, report: $REPORT${STYLE_END}"
  else
    write_fallback_report
    echo -e "${STYLE_ERROR}describeTestToolsCheck failed, $total, report: $REPORT${STYLE_END}"
  fi
}

FLAVOR=Latest
ABI=arm64
HAS_SNAPSHOT=false
if [ -f tests/core/src/main/assets/snapshot.zip ]; then
  HAS_SNAPSHOT=true
fi
NEEDS_DEVICE=false
if has_step checks || has_step benchmark; then
  NEEDS_DEVICE=true
fi

if $NEEDS_DEVICE; then
  ADB=$(command -v adb) || fail "adb is not found, set ANDROID_SDK_ROOT"

  if [ -z "$ANDROID_SERIAL" ]; then
    DEVICES=$("$ADB" devices | tr -d '\r' | awk -F'\t' '$2 == "device" { print $1 }')
    COUNT=$(printf '%s' "$DEVICES" | grep -c . || true)
    [ "$COUNT" -eq 1 ] || fail "$COUNT devices are connected. Leave one or select it:" \
      "ANDROID_SERIAL=<serial from adb devices> $0"
    ANDROID_SERIAL=$DEVICES
  fi
  export ANDROID_SERIAL
  # Commands below start adb server before runs, so its start during a run means a restart
  if [ -n "$ANDROID_ADB_LOG_PATH" ]; then
    ADB_LOG=$ANDROID_ADB_LOG_PATH
  elif [ "$PLATFORM" = windows ]; then
    # Server writes to temporary folder of the process that started it,
    # e.g. of MSYS2 or Android Studio
    ADB_LOG=${TMP:-/tmp}/adb.log
    STUDIO_ADB_LOG=$(cygpath -u "$LOCALAPPDATA")/Temp/adb.log
    if [ "$STUDIO_ADB_LOG" -nt "$ADB_LOG" ]; then
      ADB_LOG=$STUDIO_ADB_LOG
    fi
  else
    ADB_LOG=${TMPDIR:-/tmp}/adb.$(id -u).log
  fi

  SDK_INT=$(adb_shell getprop ro.build.version.sdk)
  MODEL=$(adb_shell getprop ro.product.model)
  RELEASE=$(adb_shell getprop ro.build.version.release)
  ABI=$(adb_shell getprop ro.product.cpu.abi)
  case $ABI in
    arm64-v8a) ABI=arm64 ;;
    armeabi-v7a) ABI=arm32 ;;
    x86_64) ABI=x64 ;;
  esac
  if [ "$SDK_INT" -ge 33 ]; then
    FLAVOR=Latest
  elif [ "$SDK_INT" -ge 29 ]; then
    FLAVOR=Qrs
  elif [ "$SDK_INT" -ge 24 ]; then
    FLAVOR=Tens
  else
    fail "UI tests and benchmarks require Android 7.0+, $MODEL has Android $RELEASE"
  fi

  APP_ID=$(read-property.sh local.properties app.id) ||
    APP_ID=$(read-property.sh local.properties.sample app.id)
  INSTALLED=$(
    {
      adb_shell pm list packages | sed -n 's/^package://p' | grep -F challegram
      if adb_shell dumpsys package "$APP_ID" | grep -qF "Package [$APP_ID]"; then
        echo "$APP_ID"
      fi
    } | sort -u | tr '\n' ' '
  )
  if [ -n "$INSTALLED" ]; then
    fail "Telegram X is installed on $MODEL: $INSTALLED
Tests replace the app and clear its data,
so they never run on device where Telegram X may have real accounts. Use dedicated test device.
If installed app is a test build without real accounts, remove it yourself:
adb -s <serial> uninstall <package>"
  fi
  if adb_shell ps -A -o ARGS 2>/dev/null | grep -qE '(^| )instrument( |$)'; then
    fail "Tests are already running on $MODEL, e.g. generation of baseline profiles." \
      "Wait until they finish"
  fi
fi

echo "Steps: $STEPS"
if $NEEDS_DEVICE; then
  echo "Device: $MODEL, Android $RELEASE (API $SDK_INT), $ABI, flavor $FLAVOR," \
    "app.id $APP_ID, snapshot.zip: $HAS_SNAPSHOT"
  echo -e "${STYLE_WARN}The script:
- installs builds of $APP_ID and test apps on $MODEL, clears their data
  and removes them after tests, also when the script is interrupted;
- stops recordings of screen and Perfetto traces that interrupted tests left running;
- sets animations to normal speed during tests and in the end,
  or turns them off again when they were off;
- deletes earlier results of checks (clearCheckResults), copies in tests/ui/checks/media
  and results of benchmarks.
It takes from half an hour to a couple of hours, mostly for builds.${STYLE_END}"
fi
if ! $ASSUME_YES; then
  read -r -p "Continue? [y/N] " answer
  case $answer in
    y|Y) ;;
    *) exit 1 ;;
  esac
fi

rm -rf "$OUT"
mkdir -p "$OUT"

if $NEEDS_DEVICE; then
  stop_test_processes
  PACKAGES_BEFORE=$(test_packages)
fi

STARTED=$(date +%s)
trap cleanup EXIT
trap 'exit 130' INT TERM

{
  echo "# Test tools check"
  echo
  echo "- Date: $(date '+%Y-%m-%d %H:%M')"
  echo "- Commit: $(git log -1 --format='%h %s')"
  echo "- Submodules: $(
    git submodule status vkryl/android vkryl/core vkryl/annotation 2>/dev/null |
      awk '{ print $2 " " substr($1, 1, 9) }' | tr '\n' ',' | sed 's/,$//'
  )"
  echo "- Changed files: $(git status --short | wc -l | tr -d ' ')"
  echo "- Steps: $STEPS"
  echo "- $(
    ./gradlew --version 2>/dev/null | tr -d '\r' |
      grep -E '^(Gradle|Kotlin|Daemon JVM|Launcher JVM|OS)' |
      tr -s ' ' | tr '\n' ';' | sed 's/;$//; s/;/; /g'
  )"
  if $NEEDS_DEVICE; then
    echo "- Device: $MODEL, Android $RELEASE (API $SDK_INT), $ABI, flavor $FLAVOR," \
      "snapshot.zip: $HAS_SNAPSHOT"
    echo "- Animation scales before tests: $(animation_scales)"
  fi
} > "$OUT/header.md"

if $NEEDS_DEVICE; then
  for name in $SCALE_NAMES; do
    value=$(adb_shell settings get global "$name")
    case $value in
      0|0.0)
        DISABLED_SCALES="$DISABLED_SCALES $name=$value"
        ;;
    esac
    # Tests expect animations of normal speed,
    # while slower ones may stay after recording of interrupted test
    adb_shell settings put global "$name" 1
  done
fi

step_checks() {
  local ui_filter=(-Pandroid.testInstrumentationRunnerArguments.package=tgx)
  if ! $HAS_SNAPSHOT; then
    ui_filter+=(-Pandroid.testInstrumentationRunnerArguments.notClass=tgx.test.AuthorizedLaunchTest,tgx.smoke.NotificationFloodCheck)
  fi
  animation_scales > "$OUT/scales-before-ui.txt"
  run clear gradle clearCheckResults
  local state
  for state in before after; do
    if [ $state = after ]; then
      "$ADB" -s "$ANDROID_SERIAL" logcat -c || true
    fi
    run "$state-app" gradle ":app:test${FLAVOR}DebugUnitTest" \
      ":app:connected${FLAVOR}DebugAndroidTest" "-Pabi=$ABI"
    save_results "$state-app" app/build/test-results app/build/outputs/androidTest-results
    if [ $state = after ]; then
      "$ADB" -s "$ANDROID_SERIAL" logcat -d -v raw -s TgxSmoke:I | tr -d '\r' | grep -v '^-' \
        > "$OUT/smoke-numbers.txt" || true
    fi
    run "$state-ui" gradle ":tests:ui:connected${FLAVOR}NonMinifiedReleaseAndroidTest" \
      "-Pabi=$ABI" "${ui_filter[@]}" \
      "-Pandroid.testInstrumentationRunnerArguments.tgx.smoke.state=$state"
    save_results "$state-ui" tests/ui/build/outputs/androidTest-results
    if [ $state = before ]; then
      run save gradle saveCheckResults
    fi
  done
  animation_scales > "$OUT/scales-after-ui.txt"
  run describe gradle describeCheckResults
}

step_benchmark() {
  # Microbenchmarks join runs of the same build with runs saved by earlier checks,
  # while expectations count runs of this check
  rm -rf tests/benchmark/build/benchmark-results tests/microbenchmark/build/benchmark-results
  local task=":tests:benchmark:connected${FLAVOR}BenchmarkReleaseAndroidTest"
  local filter=-Pandroid.testInstrumentationRunnerArguments.class=tgx.smoke.SmokeBenchmark
  local results=tests/benchmark/build/outputs/androidTest-results
  run benchmark-before gradle "$task" "-Pabi=$ABI" "$filter"
  save_results benchmark-before "$results"
  run benchmark-save gradle :tests:benchmark:saveBenchmarkResults
  run benchmark-after gradle "$task" "-Pabi=$ABI" "$filter"
  save_results benchmark-after "$results"
  run benchmark-compare gradle :tests:benchmark:compareBenchmarkResults
  local micro=":tests:microbenchmark:connected${FLAVOR}NonMinifiedReleaseAndroidTest"
  local suite=-Pandroid.testInstrumentationRunnerArguments.class=tgx.test.TextSuite
  results=tests/microbenchmark/build/outputs/androidTest-results
  "$ADB" -s "$ANDROID_SERIAL" logcat -c || true
  run micro gradle "$micro" :tests:microbenchmark:saveBenchmarkResults \
    :tests:microbenchmark:describeBenchmarkResults "-Pabi=$ABI" "$suite"
  save_results micro "$results"
  local tags="TestRunner|TestExecutor|TestRequestBuilder|AndroidJUnitRunner|MonitoringInstr"
  tags="$tags|AndroidRuntime|Benchmark"
  "$ADB" -s "$ANDROID_SERIAL" logcat -d -v brief | tr -d '\r' \
    | grep -E -e "^./($tags) *\(" -e "^./ActivityManager *\(.*($APP_ID|tgx\.test\.)" \
    | head -n 100 > "$OUT/micro-logcat.txt" || true
  run micro-control gradle :tests:microbenchmark:describeBenchmarkResults \
    -Pcontrol=BoringOrStaticLayout
  run micro-compare gradle "$micro" :tests:microbenchmark:compareBenchmarkResults "-Pabi=$ABI" \
    "$suite" -Pcontrol=BoringOrStaticLayout -Pimplementation=Text
  save_results micro-compare "$results"
  # Runs of the same build are compared together, a few tests are enough to check it
  run micro-repeat gradle "$micro" :tests:microbenchmark:compareBenchmarkResults "-Pabi=$ABI" \
    "$suite" -Pandroid.testInstrumentationRunnerArguments.tests_regex=text=short \
    -Pcontrol=BoringOrStaticLayout -Pimplementation=Text
  save_results micro-repeat "$results"
  run micro-profile gradle "$micro" :tests:microbenchmark:describeBenchmarkProfiles "-Pabi=$ABI" \
    "$suite" -Pandroid.testInstrumentationRunnerArguments.tests_regex=TextBenchmarks#layout_Text \
    -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.profiling.mode=MethodTracing \
    "-Pbenchmark=layout_Text[text=long]"
  save_results micro-profile "$results"
}

step_minsdk() {
  # Reports of earlier runs may stay from modules that are not checked anymore
  find . \( -name .git -o -name .gradle -o -name .cxx -o -name node_modules \) -prune \
    -o -type d -path '*/build/reports/minSdk' -prune -exec rm -rf {} +
  run minsdk gradle checkMinSdk "-Pabi=$ABI"
  # Legacy flavor of the app is built only with older NDK, which supports only 32-bit ABIs
  run minsdk-legacy gradle checkMinSdk -PuseLegacyNdk=true -Pabi=arm32
}

for STEP in $STEPS; do
  "step_$STEP"
done
FINISHED=true
