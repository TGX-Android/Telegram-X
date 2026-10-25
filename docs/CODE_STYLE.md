# Code style

Rules for code of Telegram X, for people and AI agents alike. Formatting settings are in [`.editorconfig`](/.editorconfig), which Android Studio applies. New code follows these rules even where older code around it doesn't.

## Formatting

**Indentation is 2 spaces, lines are at most 100 characters.** Only string literals and links that can't be split are longer.

**A declaration that doesn't fit into one line has one parameter per line**, and the closing parenthesis on its own line:

```kotlin
// Don't
private class TestToolsRun(val name: String, val step: String, val code: Int, val seconds: String, val command: String)

// Do
private class TestToolsRun(
  val name: String,
  val step: String,
  val code: Int,
  val seconds: String,
  val command: String
)
```

**An expression body after `=` starts in the same line when the declaration fits into one line**, even when the body takes several lines. `=` ends the line, and the body is indented by 2 spaces, after parameters on separate lines, when the first line would be longer than 100 characters, or when the body is a call with arguments on separate lines and without a trailing lambda:

```kotlin
// Don't
fun collect(
  outputFilePrefix: String,
  block: MacrobenchmarkScope.() -> Unit
) = rule.collect(
  outputFilePrefix = outputFilePrefix,
  packageName = getApplicationId()
) {
  pressHome()
  block()
}

// Do
fun collect(
  outputFilePrefix: String,
  block: MacrobenchmarkScope.() -> Unit
) =
  rule.collect(
    outputFilePrefix = outputFilePrefix,
    packageName = getApplicationId()
  ) {
    pressHome()
    block()
  }

// Do
fun launchDefault() = collect(
  "launch_authorized",
  includeInStartupProfile = true
) {
  startActivityAndWait()
}

// Do, when the first line would be too long
private fun describeImplementationOfOperation(implementation: String, operation: String) =
  when (implementation) {
    // ...
  }

// Do, a call without a trailing lambda
fun drawsWithoutAllocations() =
  assertDrawsWithoutAllocations(
    view,
    width = 200,
    height = 100
  )
```

**A call that doesn't fit into one line has one argument per line**, and a trailing lambda follows the closing parenthesis.

```kotlin
// Don't
runOnFrames(SKIPPED_FRAMES + frames, beforeFrame = { frameAllocations = Debug.getThreadAllocCount() }) { frame ->

// Do
runOnFrames(
  SKIPPED_FRAMES + frames,
  beforeFrame = {
    frameAllocations = Debug.getThreadAllocCount()
  }
) { frame ->
```

**A Kotlin lambda has its body on separate lines**, unless the body is one expression without spaces outside string literals, the lambda is the only one passed to the call, and the line stays short: `files.filter { it.isFile }`, `Array(100) { "Chat $it" }`. A call after the closing brace of a lambda goes to the next line, like other calls of a chain:

```kotlin
// Don't
val savedBefore = savedRuns.of(baseline).filter { it.id != current.id }
val worst = rows.filter { it.second[index].startsWith(WORST) }.map { it.first }

// Do
val savedBefore = savedRuns.of(baseline).filter {
  it.id != current.id
}
val worst = rows
  .filter { it.second[index].startsWith(WORST) }
  .map { it.first }
```

**A chain of calls that doesn't fit into one line has one call per line**, and a long condition breaks after `&&` or `||`. A named value often makes a long expression shorter and clearer:

```kotlin
// Don't
assertWithMessage("Allocations caught in wrong scrolling").that(counted.allocations).isGreaterThan(0)

// Do
assertWithMessage("Allocations caught in wrong scrolling")
  .that(counted.allocations)
  .isGreaterThan(0)
```

**Bodies of `if`, `else`, `for` and `while` always have braces and line breaks**, even when they are one statement. Short Kotlin functions and `if` expressions that choose a value stay in one line: `val mark = if (isTarget) "*" else " "`.

```java
// Don't
if (text == null) return;

// Do
if (text == null) {
  return;
}
```

**Java declarations of methods have a space before parameters**, calls don't: `public static int dp (float size)`, `Screen.dp(16f)`. Kotlin has no space in both.

**Kotlin has no trailing commas.**

## Names and comments

**Names are descriptive and not abbreviated**: `destinationDir`, not `dstDir`.

**Comments say only what code can't**: a workaround of a bug of Android or a library, the reason of an unusual decision, an algorithm that the name of the function doesn't explain, a contract that callers rely on. They never restate code.

```kotlin
// Don't
// Posts the callback
choreographer.postFrameCallbackDelayed(this, -BEFORE_FRAME_DELAY_MS)

// Do
// Callbacks of a frame run in order of time when they are due,
// so callback due earlier than posted runs first
choreographer.postFrameCallbackDelayed(this, -BEFORE_FRAME_DELAY_MS)
```

## Warnings

**New code adds no warnings.** A warning that can't be fixed is suppressed on the smallest declaration that causes it, with a comment when the reason isn't obvious:

```kotlin
// Don't: warning 'getIsolated()' is marked unstable with @Incubating
private fun Project.findAppKeystore(): Keystore? {
  val root = isolated.rootProject.projectDirectory

// Do
@Suppress("UnstableApiUsage")
private fun Project.findAppKeystore(): Keystore? {
  val root = isolated.rootProject.projectDirectory
```

## Code

* Changes are minimal and reuse existing code: helpers of the module, utilities of `me.vkryl`, components of the app. Repeated code is extracted into a short function.
* Code of other projects is never copied: repositories are added as git submodules, libraries as Gradle dependencies.
* Kotlin code reads Java getters as properties: `it.type.constructor`, not `it.type.getConstructor()`.
* Kotlin is allowed in new modules outside of `org.thunderdog` (see `me.vkryl` package), tests and build code, as long as it is interoperable with Java code or private.
* New files in `app` start with the license header, copied from a neighbouring file.
* Build code in `buildSrc` and `tests/buildSrc` fails with `fatal(message)` of `tgx.gradle`.
* Drawing, animations and scrolling don't allocate objects or request layout on frames, see [tests](/tests/README.md#drawing-and-animations).

## Resources

* Icons are vector drawables only. Their viewport (`viewportWidth`, `viewportHeight`) is 24x24, display size is set by `width` and `height` and is the suffix of the file name, e.g. `baseline_access_time_24.xml`.
