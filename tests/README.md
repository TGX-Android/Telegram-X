# Tests

| Kind | Location | Runs | Use for |
|---|---|---|---|
| Unit tests | `tests/unit` | On JVM, without device | Logic that doesn't call Android framework |
| Integration tests | `tests/integration` | On device, inside debug build of the app | Code that needs initialized app: TDLib, `Settings`, `Lang`, resources |
| UI tests | `tests/ui` | On device, in separate process | Screens and user flows |
| Benchmarks | `tests/benchmark` | On device, in separate process | Startup time, frame timing and other performance metrics |
| Microbenchmarks | `tests/microbenchmark` | On device, inside build of the app without R8 | Time and allocations of code that runs often, e.g. text layout, compared between implementations |
| Baseline profiles | `tests/baseline-profile` | On device, in separate process | Generating baseline profiles only |

Unit and integration tests are compiled together with app code and can use any of its classes, so they are run by `:app` tasks. Other tests are separate modules that see the app only from outside, except microbenchmarks, which run inside app process and call its classes.

`tests/core` contains test libraries and helpers shared by tests on device: app launch, element lookup, session snapshot. `tests/buildSrc` contains build configuration of tests, it's compiled as a part of `buildSrc`.

Tests use JUnit 4, [Truth](https://truth.dev) assertions, [UI Automator](https://developer.android.com/training/testing/other-components/ui-automator), [Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview) and [Microbenchmark](https://developer.android.com/topic/performance/benchmarking/microbenchmark-overview). Tests running in separate process require Android 7.0+, so they don't cover app variants for older versions.

## Workflow

Each change follows [pull request rules](../docs/PULL_REQUEST_TEMPLATE.md), is verified by [checks](#tests-and-checks) and is [described](#pull-request-description) the same way:

* **Bug fix.** Starts with a check that reproduces the bug and fails.
  * The fix removes the root cause with the smallest change, only in affected flavors. Conditions, catches or delays that only hide the symptom are not fixes. Commit description explains the cause.
  * The check runs on flavors that may have the bug to find the affected ones, see [Flavors](#flavors). When no check can run on them, e.g. the bug is only on Android 5.0, checks on device require Android 7.0+ and a unit check doesn't reproduce it, the bug is fixed without a check, and the description says how the fix was checked instead.
  * Visual bugs, e.g. of layout or glitches, are shown in the description by [screenshots or recordings](#screenshots-and-recordings) before and after the fix.
  * When the bug appeared after a version without it, `git bisect run` with the command that runs the check finds the commit that caused it. Checks are ignored by git, so they stay in place while bisect switches commits.
  * Stack traces of release builds are deobfuscated with `retrace mapping.txt stacktrace.txt` from Android SDK command-line tools and the mapping file of that build.
* **Optimization.** Measure before and after the change on the same device, with the metric that the change targets, see [Optimizing code](#optimizing-code) for each part of the app. The change is submitted when it improves the metric clearly, so that its whole confidence interval is below zero. When an optimization requires redesign, it's described with measurements and discussed before implementing. A bug found while measuring is fixed as a bug.
* **Feature.** New screens and elements get UI checks, new logic gets unit or integration checks, new drawing and animation code is checked with `assertDrawsWithoutAllocations` and `assertAnimatesSmoothly`, animations are measured with `FRAME_METRICS`. New UI is shown [before and after](#screenshots-and-recordings) the change.

## Drawing and animations

* Code that runs on every frame — `onDraw`, animation updates, scrolling and touch handling — doesn't allocate objects: allocations cause garbage collections, which drop frames and waste battery. Objects are created in advance and reused. Allocating once, e.g. creating a cache on the first frame, is acceptable, but allocations on every frame usually mean that drawing or animation itself is designed wrong.
* Animations use `me.vkryl.android.animatorx`: one animator drives all elements that change together, so they stay in sync and change without reflection, unlike with `ObjectAnimator`. Its animators don't allocate objects on frames: `BoolAnimator` switches between two states, `FloatAnimator` animates a number, `IntAnimator` an integer and `ArgbAnimator` a color, in `ColorInterpolation.SRGB` by default or `OKLAB` to keep transitions between hues bright. Existing code also uses older `me.vkryl.android.animator`, see [guide](../docs/GUIDE.md#4-animating-things).
* Animation listener applies only changes needed in the current state. `BoolAnimatorListener.onValueChange` receives `state` (`FALSE`, `INTERMEDIATE` or `TRUE`), `prevState` and `stateChanged`: what doesn't change while animating, e.g. visibility, is applied once when state changes.
* Animations don't request layout on every frame. Frames change only what is drawn (`invalidate`) or properties that don't need layout: translation, scale, alpha. Dimensions are applied in final states or once when state changes between `FALSE`, `INTERMEDIATE` and `TRUE`, e.g. view takes expanded size when it leaves `FALSE` state, and its content is revealed by drawing while animating.
* Lists scroll without allocations, layout passes and jumps: data that items draw is prepared once, not on every bind, items keep their size after they are bound, and code doesn't change scroll position while user scrolls. Old devices suffer from allocations most: on Android 4.x every garbage collection pauses the app.

## Tests and checks

Tests are kept in the repository and guard behavior that must keep working after any change, e.g. app launch with and without account in [`LaunchTests.kt`](ui/src/main/kotlin/tgx/test/LaunchTests.kt). Checks verify a single change and are not committed: reproduction of a bug, allocations and frames of new UI, its screenshots, measurements of an optimization. Checks are written and run the same way as tests, but are placed in `checks` folders ignored by git:

| Kind | Tests | Checks |
|---|---|---|
| Unit | `tests/unit/kotlin` | `tests/unit/checks` |
| Integration | `tests/integration/kotlin` | `tests/integration/checks` |
| UI | `tests/ui/src/main/kotlin` | `tests/ui/checks` |
| Benchmarks | `tests/benchmark/src/main/kotlin` | `tests/benchmark/checks` |
| Microbenchmarks | `tests/microbenchmark/src/main/kotlin` | `tests/microbenchmark/checks` |

* A check is moved to tests only when it guards a core user flow or a contract other code relies on, which changes elsewhere can break unnoticed, and it's fast and gives the same result on every run. A test that fails randomly is worse than none. Everything else stays a check, however useful it was for the change.
* Helpers that checks need are added to `tests/core`, so that next checks stay small.
* Results before and after the change are compared by Gradle tasks, which write the table for the [description](#pull-request-description) to `build/check-results.md`. Result before is taken on the commit the pull request is based on, e.g. after `git switch --detach main`, or with uncommitted changes stashed by `git stash -u`. Checks are ignored by git, so they stay in place:

```shell
./gradlew clearCheckResults
# Without the change, when checks compile without it
./gradlew :app:testLatestDebugUnitTest :tests:ui:emulatorLatestNonMinifiedReleaseAndroidTest -Pabi=arm64 --continue
./gradlew saveCheckResults
# With the change
./gradlew :app:testLatestDebugUnitTest :tests:ui:emulatorLatestNonMinifiedReleaseAndroidTest -Pabi=arm64 --continue
./gradlew describeCheckResults
```

* `-Ptgx.checks=false` builds tests without `checks` folders, e.g. to measure a commit before the change, where checks of the change don't compile.
* The table lists checks and tests from the repository that don't pass before or after, other tests are counted. `—` means the check didn't run, e.g. before the change for checks of new code.
* Checks are pushed to `checks/<branch>`, a branch with one commit on top of the pull request branch, deleted after the pull request is merged or closed. Screenshots and recordings shown in the description are added next to the checks and linked from this branch. The command keeps checks in the working tree and doesn't change the pull request branch:

```shell
git add -f 'tests/*/checks/*' && git commit -m "Add checks" && { git push -f origin HEAD:checks/<branch>; git reset HEAD~1; }
```

* To run checks of a pull request, e.g. on `main` to see the result before it, they are restored without committing:

```shell
git fetch origin checks/<branch> && git restore --source=FETCH_HEAD -- 'tests/*/checks/*'
```

## Flavors

App is built in flavors for ranges of Android versions. Code, resources, manifest entries and R8 rules in `proguard-rules.pro` needed only by some flavors are placed in `src/<folder>` that is added to exactly these flavors, instead of copies in each `src/only<Flavor>`. Manifest entries and R8 rules are added this way only in `app`. Folders are listed by `findExtraFolders` in `buildSrc`.

| Flavor | Android | Folders |
|---|---|---|
| `legacy` | 4.1–4.4W, API 16–20 | `preMarshmallow`, `onlyLegacy` |
| `lollipop` | 5.0–5.1, API 21–22 | `sinceLollipop`, `preMarshmallow`, `onlyLollipop` |
| `marshmallow` | 6.0, API 23 | `sinceLollipop`, `sinceMarshmallow`, `onlyMarshmallow` |
| `tens` | 7.0–9, API 24–28 | `sinceLollipop`, `sinceMarshmallow`, `sinceOreo`, `onlyTens` |
| `qrs` | 10–12L, API 29–32 | `sinceLollipop`, `sinceMarshmallow`, `sinceOreo`, `onlyQrs` |
| `latest` | 13 and newer, API 33+ | `sinceLollipop`, `sinceMarshmallow`, `sinceOreo`, `onlyLatest` |

* `sinceOreo` is added to `tens` too, which also runs on Android 7.x, so code there still compares `Build.VERSION.SDK_INT`.
* When no folder matches affected flavors exactly, code in the closest wider folder compares `Build.VERSION.SDK_INT`. A new folder is added to `findExtraFolders` only when several files need it.
* Checks run on a flavor with its name in the task instead of `Latest`, see [Running tests](#running-tests). Unit checks run on every flavor, e.g. `testLegacyDebugUnitTest`, checks on device only on `tens`, `qrs` and `latest`.
* Classes added after `minSdk` of a flavor are used only after version checks. Declarations can't be guarded by them: older Android fails to load or verify classes that mention new classes in supertypes, types of fields, parameters and results of methods, or caught exceptions, e.g. with `NoClassDefFoundError` or `VerifyError` on Android 4.x. `./gradlew checkMinSdk` finds such declarations and imports of new classes in debug variants of all flavors, `check<Variant>MinSdk` in one variant, and lists them in `build/reports/minSdk/<variant>.txt` of the module. It fails only in flavors for Android 4.x, where new classes are referenced by their full name only inside version checks: Dalvik fails to verify whole classes that use them elsewhere, and imports make such uses easy to miss. ART of Android 5.0+ resolves classes only when they are used, so in newer flavors the lists are only for reference. Files of classes annotated with `@RequiresApi` or `@TargetApi` may import them. `legacy` flavor of the app is built and checked only with `-PuseLegacyNdk=true`. Uses of classes and methods annotated with `@RequiresApi`, anonymous classes and calls inside methods are checked by `NewApi` check of lint instead, e.g. `./gradlew :app:lintLegacyDebug -PuseLegacyNdk=true`: without the flag newer NDK raises minSdk of `legacy` flavor. Classes of `java.util` and `java.time` are left to lint too, because core library desugaring adds many of them to older Android.

## Pull request description

Description lets the reviewer decide on the change without running it. It starts with one or two sentences about what changes for users and why, followed by:

```markdown
**Type:** bug fix, optimization, feature or other.
**Flavors:** affected flavors with Android versions, and why others are not affected.
**Cause:** root cause of the bug, for bug fixes.

**Checks:** `checks/<branch>`

| Check | Flavor, device | Before | After |
|---|---|---|---|
| `ChatTest#opensChatWithLongTitle` | `latest`, Pixel 6 - 16 | fails: `IndexOutOfBoundsException: Index 3 out of bounds for length 3` | passes |
| `ToggleViewTest#animatesSmoothly` | `latest`, Pixel 6 - 16 | — | passes |

Other tests pass: 14.

| Screenshot or recording | Device | Before | After |
|---|---|---|---|
| `ChatTest.opensChatWithLongTitle.header` | Pixel 6 - 16 | ![](…/before/….png) | ![](…/after/….png) |
| `ToggleTest.toggles.toggle` | emulator | [recording](…/before/….mp4) | [recording](…/after/….mp4) |

**Measurements:** output of `compareBenchmarkResults`, for optimizations.
**Tests:** tests added to the repository and what they guard, or none.
**Not covered:** what wasn't checked automatically and why, e.g. flavors without a device, and how it was checked instead.
```

* The tables and the line between them are `build/check-results.md` written by `describeCheckResults`, see [Tests and checks](#tests-and-checks).
* Not covered is never omitted: when everything is checked, it says so.

### Screenshots and recordings

Features and visual bug fixes, e.g. of layout or glitches, show the UI before and after the change. Screenshots show what can be seen at once, recordings show what changes over time and can't be seen on screenshots: animations, transitions, scrolling. They are taken by UI checks:

```kotlin
class ToggleTest : AuthorizedTest() {
  @Test
  fun toggles() {
    openUrl("https://t.me/tgx_android")
    device.waitForElement("msg_list")
    screenshot("chat")
    recordScreen("toggle", animationScale = 5f) {
      device.findElement("btn_toggle").click()
      device.waitForIdle()
    }
  }
}
```

* `screenshot(name)` saves screenshot, `recordScreen(name) { }` saves video of the block. `animationScale` slows animations down in the video, e.g. 5 times to see every frame of a glitch.
* The same check runs before and after the change, as described in [Tests and checks](#tests-and-checks). `saveCheckResults` moves screenshots and recordings aside together with results, `describeCheckResults` copies them to `tests/ui/checks/media` and adds a table with them, linked from `checks/<branch>`. Images are shown in the description after checks are pushed to this branch, videos are opened by their links.
* `—` before means that the screen didn't exist, e.g. for a new screen.

## Writing tests

Each test or check class is a small file in the folder of its kind, see [Tests and checks](#tests-and-checks). Test methods must return `Unit`: use block body `{ }` when the last call returns a value.

### Unit tests

```kotlin
class SegmentTreeTest {
  @Test
  fun rangeQueriesMatchLinearSearch() {
    val tree = SegmentTree(intArrayOf(3, 1, 2))
    assertThat(tree.rMaxQ(0, 2)).isEqualTo(3)
  }
}
```

Android framework methods return default values in unit tests instead of real results, so code that calls them must be tested on device.

### Integration tests

```kotlin
@RunWith(AndroidJUnit4::class)
class TextEntitiesTest {
  @Test
  fun findsMentionAndUrl() {
    val text = TdApi.FormattedText("Follow @tgx_android at https://t.me/tgx_android", arrayOf())
    assertThat(TD.parseEntities(text)).isTrue()
    assertThat(text.entities.map { it.type.constructor }).containsExactly(
      TdApi.TextEntityTypeMention.CONSTRUCTOR,
      TdApi.TextEntityTypeUrl.CONSTRUCTOR
    ).inOrder()
  }
}
```

Tests run in the same process after app initialization, without authorized account.

Because of that, integration tests check drawing and animations of views, e.g. for a view with `BoolAnimator` named `isChecked`:

```kotlin
@RunWith(AndroidJUnit4::class)
class ToggleViewTest {
  private val view = onMainThread {
    ToggleView(ApplicationProvider.getApplicationContext())
  }

  @Test
  fun drawsWithoutAllocations() =
    assertDrawsWithoutAllocations(
      view,
      width = 200,
      height = 100
    )

  @Test
  fun animatesSmoothly() = assertAnimatesSmoothly(
    view,
    width = 200,
    height = 100,
    isAnimating = {
      view.isChecked.state == BoolAnimator.State.INTERMEDIATE
    }
  ) {
    view.isChecked.animatedValue = true
  }
}
```

* Helpers are in [`Allocations.kt`](core/src/main/kotlin/tgx/test/Allocations.kt). `assertNoAllocations { }` fails when code allocates objects after its first run, `countAllocations { }` returns their number, e.g. to compare before and after optimization. Both count allocations of the current thread.
* `assertDrawsWithoutAllocations(view, width, height)` lays out view and verifies its drawing.
* `assertAnimatesSmoothly(view, width, height, isAnimating) { }` plays animation started by the block and fails when its frames allocate objects or lay out the view. On every frame view is laid out, if it requested layout, and drawn, so animators, their listeners, layout and drawing are checked together. Allocations and layout when animation starts and ends are allowed. `countAnimationFrames` returns numbers of allocations and layout passes, e.g. to compare before and after optimization.
* Animations must be enabled on device. Animation must last at least 7 frames, e.g. 120 ms on 60 Hz display: shorter animations are checked with smaller `frames`.

Scrolling of lists is checked the same way. `recyclerViewOf` creates a list of item views, e.g. for a new item view `ChatPreviewView`:

```kotlin
@RunWith(AndroidJUnit4::class)
class ChatPreviewViewTest {
  private val titles = Array(100) { "Chat $it" }
  private val list = recyclerViewOf(
    titles.size,
    createView = ::ChatPreviewView
  ) { view, position ->
    view.setTitle(titles[position])
  }

  @Test
  fun scrollsSmoothly() =
    assertScrollsSmoothly(
      list,
      width = 1080,
      height = 1920
    )
}
```

* Helpers are in [`Scrolling.kt`](core/src/main/kotlin/tgx/test/Scrolling.kt). `assertScrollsSmoothly(list, width, height)` scrolls any `RecyclerView` down and back up, so that items are created and bound once, then down again, and fails when these frames allocate objects, lay out the list or make items jump: move not by scrolled distance. On every frame list is laid out, if it requested layout, and drawn.
* `countScrollFrames` returns numbers of allocations, layout passes and jumps, and duration of frames, e.g. to compare before and after optimization on devices where benchmarks don't run.
* List must be longer than 4 of its heights. Data that binder passes to views is prepared in advance, like `titles` above: allocations in binder are counted too.
* Views, animators and other code that requires the main thread run inside `onMainThread { }`. Views that need `BaseActivity` get it from `ActivityScenario.launch(MainActivity::class.java)`.

### UI tests

```kotlin
class ChatTest : AuthorizedTest() {
  @Test
  fun openChat() {
    openUrl("https://t.me/tgx_android")
    device.waitForElement("msg_list")
  }
}
```

* `AuthorizedTest` starts each test with the session from [snapshot](#session-snapshot), `UnauthorizedTest` starts with clean app data. Both clear app data after each test. What tests may send with real accounts is limited, see [Test accounts](#test-accounts).
* Tests launch the app themselves: `launchApplication()` opens it from launcher, `openUrl(url)` opens a link in it. To wait until the app is opened, use `device.waitForNavigation()`.
* `device.waitForScreen("controller_settings")` waits until the screen is focused. Screens are named by id returned by `ViewController.getId()`. When the screen doesn't open, error tells which screen is focused instead.
* `screenshot("name")` saves screenshot to test results, e.g. to check how new UI looks. `recordScreen("name") { }` saves video of the block, e.g. for pull requests with new UI, see [Screenshots and recordings](#screenshots-and-recordings).
* Animations are on, because some bugs are visible only with them. Tests that don't need them can be annotated with `@DisableAnimations`, as well as their classes.
* Elements are found by view id: `R.id.chats_list` is `"chats_list"`. To make a new view reachable, declare id in `app/src/main/res/values/ids.xml` and assign it with `setId`.
* Helpers are in [`Extensions.kt`](core/src/main/kotlin/tgx/test/Extensions.kt): `findElement`, `waitForElement`, `failIfPresent`, `openUrl`, `runShellCommand` and others. Helpers of chats are in [`Chats.kt`](ui/src/main/kotlin/tgx/test/Chats.kt): `openSavedMessages`, the only chat where tests send messages, `sendMessage`, `lastMessages`, `visibleTopOfChat` below bars of the chat, e.g. of a pinned message, `openMessageMenu` by a tap at the time of a message, `setAppearance(AppTheme.NIGHT_BLUE, bubbles = false)`, which sets the theme and the chat style without screens of settings, and `textCopiedBy { }`, which returns what the block copied. Any `UiDevice` and `UiObject2` methods can be used as well, except `executeShellCommand`: use `runShellCommand` or `androidx.test.shell.Shell` instead.
* To see ids of elements currently displayed on device: `adb exec-out uiautomator dump /dev/tty`.
* On Windows MSYS2 and Git Bash convert arguments that look like paths, e.g. `/dev/tty`, to Windows paths. Prepend `MSYS2_ARG_CONV_EXCL='*'` to `adb` commands with paths on device.
* Notifications of new messages come without a second account and without sending anything from [`NotificationFlood`](ui/app/kotlin/tgx/test/NotificationFlood.kt), which builds of tests have inside app process: it loads incoming messages of chats of the account from their history and gives the app updates of TDLib about notifications with new ids and dates, as if they had just come. Builds of tests get notifications from TDLib like release builds, and the flood starts without them. A test starts it by a broadcast, e.g. `runShellCommand("am broadcast -n ${getApplicationId()}/tgx.test.NotificationFloodReceiver --ei count 3000 --ei perMinute 3000 --ei chats 5")`, and waits for `done` in logcat of tag `NotificationFlood`, where each second it logs the notifications it sent with the number of mappings and the native heap of the app. When the system caches the app in background, which ends a long flood by "excessive binder traffic during cached", it warns `the app is cached`.

#### Profiles of native memory

`recordNativeHeap("name") { }` records native allocations of the app while the block runs by heapprofd of Perfetto, sampled every 4 KB and dumped every 30 s, and saves the trace to test results as `<Class>.<test>.name.heapprofd.perfetto-trace`. Builds of tests are profileable by shell, and Perfetto reads the config only from `/data/misc/perfetto-configs`, where the test puts it. `describeHeapProfiles` prints memory allocated and freed during the profile, and the call sites that allocated the most, with the memory of each that wasn't freed at the end, from the code that allocates to its callers. It needs [trace processor](#analyzing-traces) in `PATH` or `-PtraceProcessor=<path>`:

```shell
./gradlew :tests:ui:connectedLatestNonMinifiedReleaseAndroidTest :tests:ui:describeHeapProfiles -Pabi=arm64 -Pandroid.testInstrumentationRunnerArguments.class=tgx.test.<Check>
```


### Benchmarks

```kotlin
class ChatsListBenchmarks : AppBenchmark() {
  @Test
  fun scroll() = measure(
    authorized = true,
    metrics = SCROLL_METRICS,
    setupBlock = {
      startActivityAndWait()
      device.waitForElement("chats_list")
    }
  ) {
    device.scrollList("chats_list")
  }
}
```

Before each iteration `measure` prepares app data the same way as UI tests and goes to home screen, then runs `setupBlock`. Only the last block is measured.

* `FRAME_METRICS` measure animations and scrolling: frame durations, `layoutCount` and `gcCount`. Layout passes and garbage collections while animating mean that code requests layout or allocates objects on every frame.
* `SCROLL_METRICS` add work of `RecyclerView`: `bindSumMs`, time spent binding items, `createViewCount`, number of created items, `listLayoutCount`, layout passes of lists, which mean that items request layout or code changes scroll position, and `listFullUpdateCount` and `listUpdateCount`, updates of lists by `notifyDataSetChanged` and other changes of adapters while scrolling. `device.scrollList(id)` flings the list the same way every time, so results before and after a change are comparable.
* `MEMORY_METRICS` measure memory used by the app at the end of the measured block. `energyMetrics()` measure energy on devices with power rails, e.g. Pixel 6 and newer.

#### Comparing results

Results are saved in `build/outputs/connected_android_test_additional_output` of the module, or `managed_device_android_test_additional_output` for emulator. To measure a change, run the same benchmark on `connected` device before and after it:

```shell
./gradlew :tests:benchmark:connectedLatestBenchmarkReleaseAndroidTest -Pabi=arm64 -Pandroid.testInstrumentationRunnerArguments.class=tgx.test.ChatsListBenchmarks
./gradlew :tests:benchmark:saveBenchmarkResults
# Apply the change and run the same benchmark again
./gradlew :tests:benchmark:compareBenchmarkResults
```

`saveBenchmarkResults` keeps results in `build/benchmark-results` of the module until `clean`. Microbenchmarks keep every run there, also of commands that neither save nor compare results, join runs of the same build, and don't save a run without measurements, e.g. a dry run or a run of checks only, as results to compare with, see [Optimizing code](#optimizing-code). `compareBenchmarkResults` shows the change of medians of each metric with 95% confidence interval of the shift between iterations, e.g. `-1.2% (-2.0%..-0.4%) lower`, and ends with a summary of clear changes. When the interval includes zero, the change is smaller than measurements can tell: more iterations narrow the interval, 4 times more make it 2 times narrower, and microbenchmarks measure code more precisely. Sampled metrics, e.g. frame durations, are compared by their percentiles. The output is included in the pull request. Tasks that read results run after tests requested in the same command, e.g. `./gradlew :tests:benchmark:connectedLatestBenchmarkReleaseAndroidTest :tests:benchmark:compareBenchmarkResults`.

* Among many metrics some change clearly by chance, so intervals are widened for the number of metrics. A change that targets one metric names it, e.g. `-Pmetric=frameDurationCpuMsP90`: its intervals are widened only for the number of benchmarks that have it, so it's measured precisely, while other metrics show only clear regressions.
* Device heats up while benchmarks run, so the next run may be slower without any change. Run benchmarks before and after the change with the same pause, e.g. let the device cool down for a few minutes.

#### Analyzing traces

Each iteration saves Perfetto trace next to results, `*.perfetto-trace`. It shows what every thread was doing: open it in [Perfetto UI](https://ui.perfetto.dev) or query with [trace processor](https://perfetto.dev/docs/analysis/trace-processor). Its binary is downloaded for the platform: `mac-arm64`, `mac-amd64`, `linux-amd64` or `windows-amd64` with `trace_processor_shell.exe`.

```shell
curl -Lo trace_processor_shell https://commondatastorage.googleapis.com/perfetto-luci-artifacts/v51.2/mac-arm64/trace_processor_shell && chmod +x trace_processor_shell
./trace_processor_shell --query-file /dev/stdin TRACE.perfetto-trace <<'SQL'
-- Longest work on the main thread, process name is app.id
select slice.name, slice.dur / 1e6 as ms from slice
join thread_track on slice.track_id = thread_track.id
join thread using (utid)
join process using (upid)
where process.name = 'org.thunderdog.challegram' and thread.is_main_thread
order by slice.dur desc limit 20
SQL
```

Other useful slices: `% GC` for garbage collections, `layout` and `measure` for layout passes, `Lock contention%` for waiting for locks, `binder transaction` for calls to other processes.

### Microbenchmarks

Microbenchmarks repeat a block of app code thousands of times and measure its time and allocations, so they detect changes of a few percent, which benchmarks of whole screens can't. They run inside app process and call its classes directly, against `nonMinifiedRelease` build type, because R8 renames and removes classes. App process loads classes of the test APK before classes of the app, so libraries that the app also uses are excluded from the test APK, see `APP_LIBRARIES` in `tests/buildSrc`. When a dependency of tests brings another such library, the app crashes at start with `NoSuchFieldError` or `NoSuchMethodError`, and microbenchmarks have no results: the crash is in logcat and in the report of `scripts/check-test-tools.sh`.

```kotlin
class TextBenchmarks(sample: TextSample) : TextSampleBenchmark(sample) {
  @Test
  fun layout_Text() = measure {
    sample.toText()
  }

  @Test
  fun layout_BoringOrStaticLayout() = measure {
    sample.toBoringOrStaticLayout()
  }

  @Test
  fun draw_Text() {
    val text = onMainThread { sample.toText() }
    measureDrawing(TEXT_WIDTH, text.height) {
      text.draw(it, 0, 0)
    }
  }
}
```

* `measure` repeats the block on the main thread, where app lays out and draws UI. Results are `timeNs`, time of one run, and `allocationCount`, objects allocated by it.
* `MicroBenchmarkRunner` keeps `BenchmarkActivity` open during all tests instead of activity of the benchmark library, which app process doesn't have: app stays in foreground, screen stays on, and sustained performance mode, where device supports it, limits clocks to ones that device keeps without overheating, so results don't depend on the screen. Hot device, e.g. while charging, may still be throttled: the benchmark library doesn't wait for it to cool down, which would fail tests on the main thread by their time limit, and `compareBenchmarkResults` shows the highest thermal status of each run instead. `nonMinifiedRelease` builds of the app include it from `tests/microbenchmark/app`.
* `measureDrawing` measures recording of canvas commands, as views do on the main thread, without rendering by GPU. Before measuring, it waits until images requested by drawing are loaded, e.g. emoji, and saves the drawing as output of the test.
* `output(result)` saves result of measured code as output of the test, e.g. `output(parse(input))`. Outputs are saved next to results, as `<Class>.<test>.output.txt` or `.png` for drawings with `_` instead of characters other than letters, digits, `.` and `-`, e.g. `TextBenchmarks.draw_Text_text_short_.output.png`, and `compareBenchmarkResults` compares them between runs, so that an optimization that changes results is found by the same run. `TextOutputs` saves drawings and sizes of `Text`, e.g. widths of lines, for more samples, widths, limits of lines and options of `Text.Builder` that app uses, in about a minute.
* Classes that run together are joined by a suite, e.g. `TextSuite` runs `TextBenchmarks` and `TextOutputs`: a list of classes separated by commas in `class` argument runs only the first class.
* Implementations of the same operation are compared by tests named `operation_Implementation` in one class. Tests on several inputs are parameterized: `TextSampleBenchmark` runs each test on every text of `TEXT_SAMPLES`, which includes texts in one line and several, with entities of each kind, and long text ellipsized at the end of the second line, as in chats list, and conversions `toText` and `toBoringOrStaticLayout` build them the way app does: like `Text`, Android layout replaces emoji with images, then it's `BoringLayout` for text that fits into one line and `StaticLayout` for other texts, as in `TextView`. All tests of one input run one after another, so implementations are compared on the same state of device.
* The same input repeated thousands of times hits caches, e.g. widths of words measured by Android, so results are for texts already seen, as when chat is scrolled back. App code runs without R8 optimizations, so it's a bit slower than in release builds, unlike classes of Android.

```shell
./gradlew :tests:microbenchmark:connectedLatestNonMinifiedReleaseAndroidTest -Pabi=arm64 -Pandroid.testInstrumentationRunnerArguments.class=tgx.test.TextBenchmarks
./gradlew :tests:microbenchmark:describeBenchmarkResults
```

`describeBenchmarkResults` prints medians of the last run as tables, one for each operation and metric, with implementations side by side and parameters in rows. Each result is compared with the best of other implementations: `19.5 µs (1.83× slower)` is 1.83 times slower than the fastest of the others, allocations are compared by difference, e.g. `12 (-4)`. Counts per operation are rounded to whole numbers in comparisons too: averaging over repetitions adds small fractions to them, which would look like changes. 🟢 marks the best implementation and 🔴 the worst when the difference is outside 95% confidence interval, `≈` marks a difference within it. Lines under each table count inputs where each implementation is the best and list ones where it's the worst, e.g. `Text: 🟢 5 of 8, 🔴 3: text=long, text=rtl, text=short`. With `-Pcontrol=<Implementation>` each result is compared with the control instead, and outputs of other implementations are compared with outputs of the control with the same parameters, see [variants](#optimizing-code). Changes of app code are measured as described in [Optimizing code](#optimizing-code). Microbenchmarks run only on `connected` device. Inside app process the benchmark library can't show its screen that isolates measurements from other apps, so device stays on a static screen, e.g. home screen without live wallpaper.

## Optimizing code

Any part of the app is optimized the same way: the metric that the change targets is measured before and after the change on the same device, and the change is kept when the metric is clearly better and nothing else is worse. What measures it depends on the part:

| Optimized | Measured by | Result |
|---|---|---|
| Code that runs many times, e.g. parsing, formatting, layout and drawing of text or of a view | [Microbenchmark](#microbenchmarks) of the code, see the steps below | `timeNs` and `allocationCount` in `compareBenchmarkResults` |
| Allocations and layout passes on frames of animations and scrolling | [Integration check](#integration-tests) with `assertAnimatesSmoothly` or `assertScrollsSmoothly` | The check fails before and passes after, `countAnimationFrames` and `countScrollFrames` give exact numbers |
| Startup, opening of screens, scrolling of real lists, memory | [Benchmark](#benchmarks) of the flow | The target metric in `compareBenchmarkResults`, e.g. `timeToInitialDisplayMs`, `frameDurationCpuMsP90` or `bindSumMs` |

* A slow flow is found by its benchmark and [traces](#analyzing-traces), which show the code that takes time. This code is then optimized with a microbenchmark, which detects changes of a few percent, and the benchmark of the flow shows the effect when it's large enough to be seen there.
* Microbenchmarks run without an account, and `BenchmarkActivity` isn't `BaseActivity`. Code that needs them is measured by benchmarks of screens, or its parts that work with plain data are measured separately, e.g. `TextSample` builds `TdApi.FormattedText` for `Text` without TDLib.
* Numbers of allocations and layout passes don't depend on speed of device, so integration checks compare them on emulator too, where benchmarks aren't representative.

Code that runs often is optimized in short cycles: change the code, run the same [microbenchmarks](#microbenchmarks) and compare results with the previous run. Only the measurement file and the optimized code are written, Gradle tasks compare and sum up results. The steps are the same for people and AI agents, `-q` leaves only output of tasks.

1. **Write measurements** in `tests/microbenchmark/checks`, which is ignored by git. Each test measures one operation of one implementation and is named `operation_Implementation`. Inputs cover common cases and cases where the code is slow, several inputs are parameterized like `TextSampleBenchmark`: controls on several inputs also show how much times differ between runs. `output(result)` saves the result, so that its changes are found. Implementations that don't change in this optimization are controls: they show how much faster or slower device became between runs. When the target code has no alternative, the control is its original version copied into the measurement file, or Android code doing similar work when the original is too large to copy, e.g. `BoringOrStaticLayout` for `Text`:

```kotlin
class StringsBenchmarks : MicroBenchmark() {
  private val text = "First line\nSecond line\n\nAfter an empty line"

  @Test
  fun newLinesToSpaces_Strings() {
    output(Strings.translateNewLinesToSpaces(text))
    measure {
      Strings.translateNewLinesToSpaces(text)
    }
  }

  @Test
  fun newLinesToSpaces_Original() = measure {
    translateNewLinesToSpaces(text)
  }
}

// Strings.translateNewLinesToSpaces before the optimization
private fun translateNewLinesToSpaces(text: String): String = …
```

2. **Measure before the change** twice: save results of the first run and compare the second run with them, as in step 4. The comparison without a change must find no clear changes, otherwise results before the change are not representative, e.g. device was hot, and they are measured again:

```shell
./gradlew -q :tests:microbenchmark:connectedLatestNonMinifiedReleaseAndroidTest :tests:microbenchmark:saveBenchmarkResults :tests:microbenchmark:describeBenchmarkResults -Pabi=arm64 -Pandroid.testInstrumentationRunnerArguments.class=tgx.test.StringsBenchmarks
```

`describeBenchmarkResults` shows where the target is slower than other implementations. `describeBenchmarkProfiles` shows what takes time in each test: methods by own time, without methods they call, and numbers of their calls, from method traces that the benchmark library records after measuring each microbenchmark with `androidx.benchmark.profiling.mode=MethodTracing`. Tracing is off by default: on some versions of Android it changes speed of tests that run after it, on Android 17 randomly up to 1.8 times, see [the issue](https://issuetracker.google.com/issues/316174880), so traces are recorded by a separate run of the target tests, which isn't compared. Tracing slows down Java code much more than native code, e.g. measuring of text by Android, so times only point at candidates, while numbers of calls are exact. `-Pbenchmark` selects tests by part of their names:

```shell
./gradlew -q :tests:microbenchmark:connectedLatestNonMinifiedReleaseAndroidTest :tests:microbenchmark:describeBenchmarkProfiles -Pabi=arm64 -Pandroid.testInstrumentationRunnerArguments.class=tgx.test.TextBenchmarks -Pandroid.testInstrumentationRunnerArguments.tests_regex=layout_Text -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.profiling.mode=MethodTracing '-Pbenchmark=layout_Text[text=short]'
```

3. **Change the code**, one idea at a time, so that its effect is measured. Several ideas are first compared in one run of microbenchmarks, which takes one build and one run instead of one for each idea and compares them on the same state of device. Benchmarks run the app in another process and compare one idea at a time. In one run of microbenchmarks:
   * Ideas are written next to the original code as variants, selected by a temporary public static field of the optimized class, e.g. `variant`, with one bit for each idea, so that sums of bits measure combinations of ideas. The field is never committed.
   * A check in `tests/microbenchmark/checks` measures each variant as an implementation and saves its output, e.g. `TextVariants` below. The second test of the original code shows how much results of the same code differ between tests of one run.
   * `describeBenchmarkResults -Pcontrol=<original>` compares each variant with the original code and lists outputs that differ from its outputs. Each test takes 50 measurements of 100 ms by default, `androidx.benchmark.iterations=20` makes the run about 2 times shorter, and the second test of the original shows whether that's precise enough.
   * A variant counts as faster only where it's 🟢 by more than the second test of the original code differs from the first one with the same parameters. The best variant is written without the field, and the field and other variants are removed, then the change is measured by step 4.

```kotlin
class TextVariants(sample: TextSample) : TextSampleBenchmark(sample) {
  @Test
  fun layout_Text() = layout(0)

  @Test
  fun layout_TextAgain() = layout(0)

  @Test
  fun layout_TextA() = layout(1)

  @Test
  fun layout_TextB() = layout(2)

  @Test
  fun layout_TextAB() = layout(3)

  private fun layout(variant: Int) {
    Text.variant = variant
    try {
      output(sizesOfTextCases())
      measure {
        sample.toText()
      }
    } finally {
      Text.variant = 0
    }
  }
}
```

```shell
./gradlew -q :tests:microbenchmark:connectedLatestNonMinifiedReleaseAndroidTest :tests:microbenchmark:describeBenchmarkResults -Pabi=arm64 -Pandroid.testInstrumentationRunnerArguments.class=tgx.test.TextVariants -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.iterations=20 -Pcontrol=Text
```

`sizesOfTextCases()` returns sizes of texts in all cases of `TextOutputs`, so that variants that lay out text differently are found by the same run.

4. **Measure after the change** and compare with the saved results:

```shell
./gradlew -q :tests:microbenchmark:connectedLatestNonMinifiedReleaseAndroidTest :tests:microbenchmark:compareBenchmarkResults -Pabi=arm64 -Pandroid.testInstrumentationRunnerArguments.class=tgx.test.StringsBenchmarks -Pimplementation=Strings -Pcontrol=Original
```

* Each run is saved with the build it ran, a checksum of the app, tests and arguments of the benchmark library, and runs of the same build, up to 5, are joined: repeating the command narrows intervals, 4 runs before and 4 after make them 2 times narrower than one and one. The first line shows the number of runs, e.g. `Runs: 2 before, 1 after`. The run of the build of saved results, e.g. without changes, is compared with its earlier runs.
* Runs are joined only when they ran in the same state of the device as the last run: the same screen, which sizes of texts depend on, e.g. on a foldable device, and the same thermal status, the highest during the run, e.g. `none` or `light`. Runs in another state are listed after `Left out runs in another state`. `Screen:` and `Thermal status:` show the state.
* Throttled device runs code slower, differently for each test. When saved results have no runs in the thermal status of the last run, the comparison fails with `No saved runs in the state of the last run`, and results are saved again in the current status. `-PacrossThermalStatus` with `-Pcontrol` compares times relative to the control with runs in another status instead, e.g. `Thermal status: light before, none after, times are relative to BoringOrStaticLayout, only to look at: decide by runs in one status`. Such comparisons are only to look at: of the same build, 36 metrics of `TextSuite`, 3 of 5 comparisons across statuses showed a false clear change, up to 9 %, and 1 of 4 in one status. Decisions, e.g. by the decision rule of a task, are made by comparisons in one status.
* `-Pimplementation` compares only the target implementation.
* `-Pcontrol` measures changes of times relative to the control: when device becomes 5% slower while it heats up, the control is 5% slower too, and the target is compared as if device didn't change. Times of the same code also differ between runs by a few percent, differently for each test, so intervals include the spread of changes of the control between its tests, shown in the first lines: one pair of runs clearly shows only changes several times larger than it.
* `-Pmetric=timeNs` measures the target metric more precisely, see [Comparing results](#comparing-results).
* `-PfailOnRegression` fails the task when a metric is clearly higher or an output changed, e.g. for `git bisect run`.

The output ends with a summary, changed outputs are listed before it with paths of both files, and drawings with the number and area of different pixels:

```text
Summary: 1 lower, 1 no clear change
Lower: newLinesToSpaces_Strings timeNs -12.4%
Outputs: 1 same
```

5. **Decide.**
   * The change is kept when the target metric is `lower`, nothing is `higher` and outputs are the same, also after the same command runs again and joins both runs: a clear change in one run may be a rare deviation of the run. `no clear change` means that the change doesn't help enough to be measured, so it's reverted, unless it simplifies the code.
   * A changed output is a bug, unless the change of result is intended and checked, e.g. by looking at both images.
   * When one operation becomes faster and another slower, the change is kept only when the faster one matters more, e.g. drawing runs on the main thread on every frame, while text is mostly laid out in background.
   * After a kept change `./gradlew :tests:microbenchmark:saveBenchmarkResults` makes its runs the base for the next cycle without running tests again. The pull request shows comparison of the final code with the commit it's based on, see [Comparing results](#comparing-results).

Faster checks:

* `-Pandroid.testInstrumentationRunnerArguments.tests_regex=TextBenchmarks#draw_` runs tests whose `class#method` matches the regular expression, e.g. drawing of all inputs, `'-Pandroid.testInstrumentationRunnerArguments.class=tgx.test.TextBenchmarks#draw_Text[text=short]'` runs one test.
* `-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.dryRunMode.enable=true` runs each measured block once: it checks that measurements work and saves outputs, which `compareBenchmarkResults` compares without measurements. `TextSuite` runs in 10 s and the command takes about 30 s with the app built, 2 min with a build of changed code. Dry runs don't compile the app ahead of time, which takes a minute, and outputs are packed into one file at the end of a run, because Gradle copies outputs during the run by a command of `adb` for each file, which took 10 minutes for 500 outputs.
* `Text` is optimized with `-Pandroid.testInstrumentationRunnerArguments.class=tgx.test.TextSuite -Pimplementation=Text -Pcontrol=BoringOrStaticLayout`, the suite also checks its drawing and sizes on more texts and options.

An earlier commit, e.g. the one a pull request is based on, is measured again by the current tests when tests changed since then: its saved runs measured other samples, and their drawings had no background. Both builds run in the same clone, so that their runs are saved together in `tests/microbenchmark/build/benchmark-results`:

```shell
# The earlier app with the current tests
git switch --detach <commit> && git submodule update --recursive
git checkout <branch> -- tests
# A dry run, 2 minutes with the build: the tests run on the earlier code and draw its outputs
./gradlew -q :tests:microbenchmark:connectedLatestNonMinifiedReleaseAndroidTest -Pabi=arm64 -Ptgx.checks=false -Pandroid.testInstrumentationRunnerArguments.class=tgx.test.TextSuite -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.dryRunMode.enable=true
./gradlew -q :tests:microbenchmark:connectedLatestNonMinifiedReleaseAndroidTest :tests:microbenchmark:saveBenchmarkResults -Pabi=arm64 -Ptgx.checks=false -Pandroid.testInstrumentationRunnerArguments.class=tgx.test.TextSuite
# Back to the branch, the runs stay
git checkout <branch> -- tests && git switch <branch> && git submodule update --recursive
./gradlew -q :tests:microbenchmark:connectedLatestNonMinifiedReleaseAndroidTest :tests:microbenchmark:compareBenchmarkResults -Pabi=arm64 -Pandroid.testInstrumentationRunnerArguments.class=tgx.test.TextSuite -Pimplementation=Text -Pcontrol=BoringOrStaticLayout
```

* `-Ptgx.checks=false` builds tests without `checks`, which use code of the change.
* Tests that use other code the earlier commit doesn't have, e.g. a benchmark of a new class or a helper that waits for its work, don't compile there. They are deleted or reduced before the runs of the earlier commit, and `git checkout <branch> -- tests` restores them: only metrics and outputs that both runs have are compared.
* The dry run checks in about 2 minutes with the build of the earlier app that the tests work, before measured runs of 5 minutes each. Outputs before come from the measured run, drawn by the same tests as after, with the same background.

## Running tests

| Kind | Command |
|---|---|
| Unit tests | `./gradlew :app:testLatestDebugUnitTest` |
| Integration tests | `./gradlew :app:emulatorLatestDebugAndroidTest -Pabi=arm64` |
| UI tests | `./gradlew :tests:ui:emulatorLatestNonMinifiedReleaseAndroidTest -Pabi=arm64` |
| Benchmarks | `./gradlew :tests:benchmark:emulatorLatestBenchmarkReleaseAndroidTest -Pabi=arm64` |
| Microbenchmarks | `./gradlew :tests:microbenchmark:connectedLatestNonMinifiedReleaseAndroidTest -Pabi=arm64` |
| Baseline profiles | `./gradlew :app:generateBaselineProfile -Pabi=arm64` |

Tests on device replace installed app and clear its data, so they must never run on a device with Telegram X used for real accounts. Tests requested together, e.g. integration and UI tests, run one after another. Device is selected by task name:

* `emulator`: Android 14 emulator created by Gradle. First run downloads system image. On machines without GPU add `-Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect`.
* `connected`: dedicated test device connected via `adb`. Faster than emulator and gives real benchmark results. Screen lock must be disabled, tests turn on the screen themselves. When several devices are connected, select one with `ANDROID_SERIAL=<serial from adb devices>`.

* Single test of unit tests: `--tests 'org.thunderdog.challegram.charts.SegmentTreeTest'`. Of tests on device: `-Pandroid.testInstrumentationRunnerArguments.class=tgx.test.ChatTest#openChat`.
* `-Pabi` limits native libraries to ABIs of devices, separated by commas: `arm64` for phones and emulators on ARM machines, `x64` for emulators on x86 machines, e.g. `-Pabi=arm64,x64` for both. Without it, all ABIs are built.
* UI tests have two build types. `NonMinifiedRelease` builds faster and is suitable for writing tests. `BenchmarkRelease` is minified with R8 the same way as release builds, use it to check the build before publishing.
* Benchmarks on emulator fail by default, because results are not representative. To check that they work, add `-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR`. Real measurements require `connected` device.
* To test other flavors, replace `Latest` in task name, e.g. with `Tens` (Android 7.0–9).
* Baseline profiles are generated for all variants that use them, on connected device and then on emulator, and saved in `app/src/<variant>/generated/baselineProfiles`.
* On failure Gradle prints path to report. Results of tests on device, including logcat, are saved in `build/outputs/androidTest-results` of the module.
* When a test in separate process fails, screenshot, UI hierarchy and app crashes at that moment are saved as `<Class>.<test>.failure.png`, `<Class>.<test>.failure.xml` and `<Class>.<test>.crash.txt`, screenshots taken by test as `<Class>.<test>.<name>.png`. Gradle copies them to `build/outputs` of the module: `find tests/ui/build/outputs -name 'ChatTest.*'`.
* After changes in `tests/core` or `tests/buildSrc`, `scripts/check-test-tools.sh` checks test tools: helpers of drawing, animations and scrolling, screenshots and recordings, check results before and after a change, benchmarks and their comparison, microbenchmarks, their outputs, comparison and method traces, `checkMinSdk`. Checks and benchmarks run on connected test device, `minsdk` step doesn't need it. Steps are selected by arguments, e.g. `scripts/check-test-tools.sh checks`. Its smoke checks are in `tests/<module>/tools`, which are compiled only with `-Ptgx.testTools`. The script runs Gradle and works with the device, `describeTestToolsCheck` task checks results and writes `build/test-tools-check/report.md`. It runs on macOS, Ubuntu and Windows in MSYS2 (UCRT64).

## Test accounts

* Tests and checks run on production servers of Telegram with real test accounts. Messages can be sent only to **Saved Messages** or to the users authorized in `snapshot.zip`, and groups and channels created by them can include only these users. Their ids are returned by `getSnapshotUserIds()`: when there is only one, messages are sent only to **Saved Messages**.
* Be careful with TDLib requests that load data over network: don't send them in loops or more often than a user could. Requests answered locally are not limited.

### Session snapshot

Authorized tests restore sessions from `tests/core/src/main/assets/snapshot.zip`. It is ignored by git and must never be committed: it gives access to the accounts. Use dedicated test accounts. Snapshot includes all accounts added to the app, so several accounts can be authorized to test interaction between them: messages, groups, channels, notifications.

Sessions from the snapshot must not be used by several apps at once, otherwise Telegram may terminate them as leaked. That's why build runs tests on device one at a time and only on one device, and fails when another Gradle build on the machine is already running tests with the same accounts. Builds with snapshots of other accounts can run tests at the same time.

To create the snapshot, open **Saved Messages** of the test account → attach menu → **Files** → **Session Snapshot** (available in builds with test hooks enabled or in developer mode). The snapshot is sent to the chat: download it and save as `tests/core/src/main/assets/snapshot.zip`. Then clear data of the app where the snapshot was created, so it stops using the same sessions. Don't log out instead: it terminates sessions in the snapshot too.
