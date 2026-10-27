package tgx.test

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register
import org.gradle.work.DisableCachingByDefault
import java.io.DataInputStream
import java.io.File

internal val TEST_TOOLS_DIRECTORIES = listOf(
  "tests/integration/tools",
  "tests/ui/tools",
  "tests/benchmark/tools"
)

private const val OUTPUT_DIRECTORY = "build/test-tools-check"

private const val CHECK_RESULTS_REPORT = "build/check-results.md"

// Before the change fixedBug fails
private val EXPECTED_FAILURES = setOf("before-ui")

internal val Project.hasTestTools: Boolean
  get() = providers.gradleProperty("tgx.testTools")
    .map {
      it != "false"
    }
    .getOrElse(false)

fun Project.configureTestToolsCheck() {
  tasks.register<DescribeTestToolsCheckTask>("describeTestToolsCheck") {
    rootDirectory.set(layout.projectDirectory)
    serial.set(providers.environmentVariable("ANDROID_SERIAL"))
  }
}

/**
 * Writes report of scripts/check-test-tools.sh. The script saves to build/test-tools-check
 * logs of runs, runs.tsv, and copies of test results of each run,
 * because the next runs replace them.
 */
@DisableCachingByDefault(because = "Results of runs are read every time")
abstract class DescribeTestToolsCheckTask : DefaultTask() {
  @get:Internal
  abstract val rootDirectory: DirectoryProperty

  @get:Internal
  abstract val serial: Property<String>

  @TaskAction
  fun describe() {
    val root = rootDirectory.get().asFile
    val report = TestToolsReport(root, serial.orNull).build()
    root.resolve("$OUTPUT_DIRECTORY/report.md").writeText(report)
    logger.lifecycle(report.trimEnd().substringAfterLast('\n'))
  }
}

private class TestToolsRun(
  val name: String,
  val step: String,
  val code: Int,
  val seconds: String,
  val command: String
)

private class TestToolsReport(private val root: File, private val serial: String?) {
  private val out = root.resolve(OUTPUT_DIRECTORY)
  private val runs = out.resolve("runs.tsv").readLinesOrEmpty()
    .filter { it.isNotEmpty() }
    .map {
      val (name, step, code, seconds, command) = it.split('\t', limit = 5)
      TestToolsRun(name, step, code.toInt(), seconds, command)
    }
  private val text = StringBuilder()
  private var issues = 0

  fun build(): String {
    text.appendLine(out.resolve("header.md").readTextOrEmpty().trimEnd())
    for (step in runs.map(TestToolsRun::step).distinct()) {
      when (step) {
        "checks" -> checks()
        "benchmark" -> benchmark()
        "minsdk" -> minSdk()
      }
    }
    // The script saves name of the interrupted run, nothing when it's interrupted between runs
    val interrupted = out.resolve("interrupted")
    val interruption = if (interrupted.isFile) {
      val run = interrupted.readText().trim()
      if (run.isEmpty()) "Interrupted" else "Interrupted during $run"
    } else {
      null
    }
    if (interruption != null) {
      heading("**$interruption**")
    }
    heading("## Runs")
    text.appendLine("| Run | Command | Exit code | Time |")
    text.appendLine("|---|---|---|---|")
    for (run in runs) {
      text.appendLine("| `${run.name}` | `${run.command}` | ${run.code} | ${run.seconds} s |")
    }
    text.appendLine()
    text.appendLine(
      when {
        interruption != null -> "$interruption, expectations not met: $issues"
        issues == 0 -> "All expectations are met"
        else -> "Expectations not met: $issues"
      }
    )
    return anonymize(text.toString())
  }

  private fun checks() {
    heading("## Checks before and after a change")
    problems("checks")
    val table = root.resolve(CHECK_RESULTS_REPORT).readLinesOrEmpty()
    fun rows(content: String) = table.filter {
      content in it
    }
    fun rowsEndWith(content: String, end: String): Boolean {
      val found = rows(content)
      return found.isNotEmpty() && found.all { it.endsWith(end) }
    }
    fun mediaListed(name: String, isNew: Boolean = false): Boolean {
      val row = rows("`$name`").singleOrNull() ?: return false
      return "/after/" in row && if (isNew) "| — |" in row else "/before/" in row
    }
    val scalesBefore = out.resolve("scales-before-ui.txt").readTextOrEmpty()
    val scalesAfter = out.resolve("scales-after-ui.txt").readTextOrEmpty()
    val recordings = root.resolve(MEDIA_COPIES_DIRECTORY).walk()
      .filter {
        it.isFile && it.extension == "mp4"
      }
      .toList()
    heading("### Expectations")
    expect(
      "Helpers of drawing, animations and scrolling pass correct code and catch wrong code",
      rowsEndWith("SmokeIntegrationCheck#", "| passes |")
    )
    expect("All 6 checks of helpers ran", rows("SmokeIntegrationCheck#").size == 6)
    val fixedBugFailedBefore = rows("SmokeUiCheck#fixedBug").any {
      "| fails: " in it
    }
    expect(
      "fixedBug fails before the change and passes after it",
      fixedBugFailedBefore && rowsEndWith("SmokeUiCheck#fixedBug", "| passes |")
    )
    expect(
      "introSwipes: recordScreen sets animationScale 5 and restores previous one",
      rowsEndWith("SmokeUiCheck#introSwipes", "| passes | passes |")
    )
    expect(
      "disablesAnimations: @DisableAnimations turns animations off",
      rowsEndWith("SmokeUiCheck#disablesAnimations", "| passes | passes |")
    )
    if (root.resolve(SNAPSHOT_PATH).exists()) {
      expect(
        "NotificationFlood: the app shows its fake notifications like the ones of new messages",
        rowsEndWith("NotificationFloodCheck#", "| passes | passes |")
      )
    }
    expect(
      "Screenshot intro is listed once, before and after",
      mediaListed("SmokeUiCheck.introSwipes.intro")
    )
    expect(
      "Recording swipes is listed once, before and after",
      mediaListed("SmokeUiCheck.introSwipes.swipes")
    )
    expect(
      "New screen: — before, screenshot after",
      mediaListed("SmokeUiCheck.newScreen.new", isNew = true)
    )
    expect(
      "Failure screenshots are not listed",
      table.none {
        ".failure" in it
      }
    )
    expect("Other tests are counted", table.any { it.startsWith("Other tests pass: ") })
    expect(
      "UI tests from the repository ran",
      "introOpens: passes" in testResults(results("after-ui"))
    )
    expect(
      "Flavors are listed, device serial number is not",
      table.none {
        "`?`" in it || (!serial.isNullOrEmpty() && serial in it)
      }
    )
    expect(
      "Animation scales are restored after UI tests",
      scalesBefore.isNotBlank() && scalesBefore == scalesAfter
    )
    expect(
      "Copies of recordings are MP4 videos",
      recordings.isNotEmpty() && recordings.all { isMp4(it) }
    )

    heading("### Results after the change")
    code(
      listOf("after-app", "after-ui").flatMap { run ->
        val directory = results(run)
        val files = findResultFiles(listOf(directory)).map {
          it.relativeTo(directory).invariantSeparatorsPath
        }
        listOf("$run:") + files + testResults(directory)
      }
    )
    heading("### Numbers of helpers on device")
    code(out.resolve("smoke-numbers.txt").readLinesOrEmpty())
    heading("### $CHECK_RESULTS_REPORT")
    table.forEach(text::appendLine)
    heading("### Screenshots and recordings")
    val media = listOf(MEDIA_DIRECTORY, "build/check-results")
      .flatMap { directory ->
        root.resolve(directory).walk()
          .filter {
            it.isFile && it.extension in MEDIA_EXTENSIONS
          }
          .map { it.relativeTo(root).invariantSeparatorsPath }
          .toList()
      }
      .sorted()
    val copies = root.resolve(MEDIA_COPIES_DIRECTORY).walk()
      .filter { it.isFile }
      .sortedBy { it.path }
      .map {
        "${it.relativeTo(root).invariantSeparatorsPath}: ${it.length()} bytes, ${mediaType(it)}"
      }
      .toList()
    code(media + copies)
  }

  private fun benchmark() {
    heading("## Benchmarks")
    problems("benchmark")
    val before = testResults(results("benchmark-before"))
    val after = testResults(results("benchmark-after"))
    val micro = testResults(results("micro"))
    val microAgain = testResults(results("micro-compare"))
    val microRepeat = testResults(results("micro-repeat"))
    val microProfile = testResults(results("micro-profile"))
    if (micro.isEmpty()) {
      heading("#### End of the log of `micro`")
      val logEnd = log("micro")
        .filterNot { TASK_WITHOUT_WORK.matches(it) }
        .takeLast(40)
      code(logEnd)
      heading("#### Instrumentation in logcat of `micro`")
      val files = results("micro").walk()
        .filter { it.isFile }
        .sortedBy { it.path }
        .toList()
      val contents = files.flatMap { file ->
        val path = file.relativeTo(results("micro")).invariantSeparatorsPath
        listOf("$path:") + if (file.extension == "pb") emptyList() else file.readLines()
      }
      code(contents + out.resolve("micro-logcat.txt").readLinesOrEmpty())
    }
    val comparison = taskOutput(log("benchmark-compare"), "compareBenchmarkResults")
    val description = taskOutput(log("micro"), "describeBenchmarkResults")
    val controlDescription = taskOutput(log("micro-control"), "describeBenchmarkResults")
    val microComparison = taskOutput(log("micro-compare"), "compareBenchmarkResults")
    val repeatedComparison = taskOutput(log("micro-repeat"), "compareBenchmarkResults")
    val profile = taskOutput(log("micro-profile"), "describeBenchmarkProfiles")
    heading("### Expectations")
    expect(
      "introSwipes: FRAME_METRICS and MEMORY_METRICS are measured before the change",
      "introSwipes: passes" in before
    )
    expect(
      "introSwipes: FRAME_METRICS and MEMORY_METRICS are measured after the change",
      "introSwipes: passes" in after
    )
    if (root.resolve(SNAPSHOT_PATH).exists()) {
      expect(
        "chatsListScrolls: SCROLL_METRICS and scrollList work",
        "chatsListScrolls: passes" in after
      )
    } else {
      expect(
        "chatsListScrolls is skipped without snapshot.zip",
        "chatsListScrolls: skipped" in after
      )
    }
    expect(
      "energy: energyMetrics are measured, or skipped on device without power rails",
      "energy: passes" in after || "energy: skipped" in after
    )
    expect("compareBenchmarkResults compares results", exitCode("benchmark-compare") == 0)
    expect(
      "compareBenchmarkResults finds no clear changes without a change",
      exitCode("benchmark-compare") == 0 &&
        comparison.none {
          it.endsWith(" higher") || it.endsWith(" lower")
        }
    )
    expect(
      "Microbenchmarks run inside app process: TextBenchmarks pass",
      "layout_Text[text=short]: passes" in micro &&
        (micro + microAgain + microRepeat + microProfile).all { it.endsWith(": passes") }
    )
    expect(
      "TextOutputs draw texts of more samples",
      micro.any { it.startsWith("draw_Text[text=quote,width=300,maxLines=2,options=default]: ") }
    )
    expect(
      "TextOutputs draw texts with options of Text.Builder that app uses",
      micro.any { it.startsWith("draw_Text[text=links,width=200,maxLines=-1,options=suffix]: ") }
    )
    expect(
      "TextOutputs save sizes of lines next to drawings",
      root.resolve("tests/microbenchmark/build/benchmark-results/runs").walk().any {
        it.name.startsWith("TextOutputs.") && it.name.endsWith(".output.txt")
      }
    )
    expect(
      "describeBenchmarkResults shows BoringOrStaticLayout and Text side by side",
      description.any {
        "| Parameters | BoringOrStaticLayout | Text |" in it
      }
    )
    val timeTables = description.count {
      it == "**timeNs**"
    }
    val allocationTables = description.count {
      it == "**allocationCount**"
    }
    expect(
      "describeBenchmarkResults shows time and allocations of layout and drawing separately",
      description.count { it.startsWith("#### TextBenchmarks.") } == 2 &&
        timeTables == 2 &&
        allocationTables == 2
    )
    expect(
      "describeBenchmarkResults sums up where each implementation is the best and the worst",
      description.count { it.startsWith("Text: 🟢 ") } == 4
    )
    val comparedWithControl = controlDescription.any {
      "compared with BoringOrStaticLayout:" in it
    }
    expect(
      "describeBenchmarkResults compares each implementation with -Pcontrol",
      comparedWithControl &&
        controlDescription.count { it.startsWith("Text: 🟢 ") } == 4 &&
        controlDescription.none { it.startsWith("BoringOrStaticLayout: ") }
    )
    val comparedDrawings = controlDescription
      .firstNotNullOfOrNull { OUTPUTS_OF_CONTROL.matchEntire(it) }
      ?.groupValues
      ?.drop(1)
      ?.sumOf {
        it.toIntOrNull() ?: 0
      }
    expect(
      "describeBenchmarkResults compares drawings of Text with drawings of -Pcontrol",
      comparedDrawings == micro.count { it.startsWith("draw_BoringOrStaticLayout[") }
    )
    expect(
      "Tests and compareBenchmarkResults run in one command",
      exitCode("micro-compare") == 0 && microComparison.isNotEmpty()
    )
    expect(
      "compareBenchmarkResults measures times of Text relative to BoringOrStaticLayout",
      microComparison.any { it.startsWith("timeNs is relative to BoringOrStaticLayout") } &&
        microComparison.none {
          "_BoringOrStaticLayout[" in it
        }
    )
    expect(
      "Drawings of Text are the same in both runs",
      microComparison.any { SAME_OUTPUTS.matches(it) }
    )
    expect(
      "compareBenchmarkResults finds no clear changes of microbenchmarks without a change",
      microComparison.none {
        it.endsWith(" higher") || it.endsWith(" lower")
      }
    )
    expect(
      "compareBenchmarkResults shows screen of runs",
      microComparison.any { it.startsWith("Screen: ") }
    )
    expect(
      "compareBenchmarkResults shows thermal status of runs",
      microComparison.any { it.startsWith("Thermal status") }
    )
    expect(
      "compareBenchmarkResults joins repeated runs of the same build",
      microComparison.any { it.startsWith("Runs: 1 before, 1 after") } &&
        repeatedComparison.any { it.startsWith("Runs: 2 before, 1 after") }
    )
    expect(
      "describeBenchmarkProfiles shows methods of Text in method trace",
      profile.any { it.startsWith("#### TextBenchmarks.layout_Text[text=long]") } &&
        profile.any {
          "| org.thunderdog.challegram.util.text.Text." in it
        }
    )

    heading("### Results")
    code(listOf("Before:") + before + "After:" + after)
    heading("### compareBenchmarkResults")
    code(comparison)
    heading("### Microbenchmarks")
    val (passed, notPassed) = (micro + microAgain + microRepeat + microProfile).partition {
      it.endsWith(": passes")
    }
    code(notPassed + "Tests pass: ${passed.size}")
    description.forEach(text::appendLine)
    heading("### describeBenchmarkResults -Pcontrol=BoringOrStaticLayout")
    val controlSummary = controlDescription.filter {
      it.startsWith("Each result") || it.startsWith("Outputs: ")
    }
    code(controlSummary)
    heading("### compareBenchmarkResults of microbenchmarks")
    code(microComparison)
    heading("### compareBenchmarkResults of repeated microbenchmarks")
    code(repeatedComparison)
    heading("### describeBenchmarkProfiles")
    profile.forEach(text::appendLine)
  }

  private fun minSdk() {
    heading("## checkMinSdk")
    problems("minsdk")
    val modules = root.listFiles().orEmpty().filter {
      it.isDirectory && !it.name.startsWith(".")
    }
    val reports = modules
      .flatMap { module ->
        listOf(module) + module.listFiles().orEmpty().filter { it.isDirectory }
      }
      .flatMap { module ->
        module.resolve("build/reports/minSdk").listFiles().orEmpty().filter {
          it.extension == "txt"
        }
      }
      .sortedBy { it.relativeTo(root).invariantSeparatorsPath }
    heading("### Expectations")
    expect("checkMinSdk checked variants", reports.isNotEmpty())
    expect(
      "checkMinSdk checked legacy flavor of the app",
      root.resolve("app/build/reports/minSdk/legacyDebug.txt").exists()
    )
    expect(
      "checkMinSdk passes, flavors for Android 5.0+ only list classes added after minSdk",
      exitCode("minsdk") == 0
    )
    expect(
      "No declarations and imports in legacy flavor use classes added after minSdk",
      exitCode("minsdk-legacy") == 0
    )
    // Lists of variants for Android 5.0+ don't fail checks and are only counted
    val failedReports = (log("minsdk") + log("minsdk-legacy")).mapNotNull { line ->
      FAILED_MIN_SDK_CHECK.matchEntire(line)?.destructured?.let { (module, variant) ->
        val directory = module.removePrefix(":").replace(':', '/')
        val name = variant.replaceFirstChar(Char::lowercaseChar)
        root.resolve("$directory/build/reports/minSdk/$name.txt")
      }
    }
    code(
      reports.flatMap { report ->
        val violations = report.readLines().filter { it.isNotEmpty() }
        val path = report.relativeTo(root).invariantSeparatorsPath
        val listed = if (report in failedReports) violations.take(15) else emptyList()
        listOf("$path: ${violations.size}") + listed
      }
    )
  }

  private fun problems(step: String) {
    val stepRuns = runs.filter {
      it.step == step
    }
    for (run in stepRuns) {
      val disconnects = out.resolve("${run.name}.disconnects.txt")
      if (disconnects.exists()) {
        heading("#### Connection to the device was lost during `${run.name}`")
        text.appendLine(
          "Tests that ran at that moment fail regardless of code, " +
            "e.g. without message or with DeadObjectException. " +
            "Check USB cable and port, then run again."
        )
        code(disconnects.readLines())
      }
      if (run.code != 0 && run.name !in EXPECTED_FAILURES) {
        val log = log(run.name)
        val whatWentWrong = log.indices
          .filter { log[it].startsWith("* What went wrong") }
          .flatMap {
            log.subList(it, minOf(log.size, it + 13))
          }
        val errors = log.filter {
          ERROR.containsMatchIn(it) && !STACK_FRAME.containsMatchIn(it)
        }
        heading("#### Errors of `${run.name}`")
        code(errors.take(40) + whatWentWrong.take(60))
      }
      val crashes = crashes(results(run.name))
      if (crashes.isNotEmpty()) {
        heading("#### Crashes in logcat of `${run.name}`")
        code(crashes)
      }
    }
  }

  private fun expect(description: String, isMet: Boolean) {
    if (isMet) {
      text.appendLine("- ✓ $description")
    } else {
      text.appendLine("- ✗ $description")
      issues++
    }
  }

  private fun heading(title: String) {
    separate()
    text.appendLine(title)
    text.appendLine()
  }

  private fun code(lines: List<String>) {
    separate()
    text.appendLine("```text")
    lines
      .ifEmpty { listOf("No output") }
      .forEach(text::appendLine)
    text.appendLine("```")
  }

  private fun separate() {
    if (!text.endsWith("\n\n")) {
      text.appendLine()
    }
  }

  private fun log(run: String): List<String> = out.resolve("$run.log").readLinesOrEmpty()

  private fun exitCode(run: String): Int? {
    val lastRun = runs.lastOrNull {
      it.name == run
    }
    return lastRun?.code
  }

  private fun results(run: String): File = out.resolve("$run-results")

  private fun anonymize(text: String): String {
    val home = File(System.getProperty("user.home"))
    val result = text.replace(home.path, "~").replace(home.invariantSeparatorsPath, "~")
    return if (serial.isNullOrEmpty()) result else result.replace(serial, "<serial>")
  }
}

private val ERROR = Regex(""" FAILED$|^e: |error:|Error:|Exception""")
private val STACK_FRAME = Regex("""^\s+at """)
private val CRASH = Regex("FATAL EXCEPTION|Process crashed|ANR in|Instrumentation run failed")
private val SAME_OUTPUTS = Regex("""^Outputs: \d+ same$""")
private val OUTPUTS_OF_CONTROL = Regex("""^Outputs: (\d+) same as \w+(?:, (\d+) different: .*)?$""")
private val FAILED_MIN_SDK_CHECK = Regex("""^> Task (:[\w:-]+):check(\w+)MinSdk FAILED$""")
private val TASK_WITHOUT_WORK = Regex("""^> Task .* (UP-TO-DATE|SKIPPED|NO-SOURCE|FROM-CACHE)$""")
private val END_OF_TASK_OUTPUT = listOf("> Task ", "BUILD ", "[Incubating]", "Deprecated Gradle")

private fun testResults(directory: File): List<String> =
  findResultFiles(listOf(directory)).flatMap { file ->
    readTestCases(file).map { "${it.getAttribute("name")}: ${resultOf(it)}" }
  }

private fun crashes(directory: File): List<String> =
  directory.walk()
    .filter {
      it.isFile && it.name.startsWith("logcat")
    }
    .flatMap { file ->
      val lines = file.readLines()
      lines.indices
        .filter { CRASH.containsMatchIn(lines[it]) }
        .flatMap {
          it until minOf(lines.size, it + 16)
        }
        .distinct()
        .map { lines[it] }
    }
    .take(80)
    .toList()

private fun taskOutput(log: List<String>, task: String): List<String> {
  val start = log.indexOfFirst {
    it.startsWith("> Task ") && task in it
  }
  if (start < 0) {
    return emptyList()
  }
  return log.drop(start + 1)
    .takeWhile { line ->
      END_OF_TASK_OUTPUT.none { line.startsWith(it) }
    }
    .dropLastWhile { it.isBlank() }
}

private fun isMp4(file: File): Boolean {
  if (file.length() <= 8) {
    return false
  }
  val header = file.inputStream().use { it.readNBytes(8) }
  return header.copyOfRange(4, 8).decodeToString() == "ftyp"
}

private fun mediaType(file: File): String = when {
  isMp4(file) -> "MP4"
  file.extension == "png" && file.length() > 24 -> DataInputStream(file.inputStream()).use {
    it.skipNBytes(16)
    "PNG ${it.readInt()}x${it.readInt()}"
  }
  else -> "unknown"
}

private fun File.readLinesOrEmpty(): List<String> = if (isFile) readLines() else emptyList()

private fun File.readTextOrEmpty(): String = if (isFile) readText() else ""
