package tgx.test

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register
import org.gradle.work.DisableCachingByDefault
import tgx.gradle.fatal
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

@Suppress("UnstableApiUsage")
fun Project.configureBenchmarkComparison() {
  // Benchmarks on emulator are not representative, so only results of connected device are compared
  val results = layout.buildDirectory.dir("outputs/connected_android_test_additional_output")
  val savedResults = layout.buildDirectory.dir("benchmark-results")
  // Results are read after tests requested in the same command
  val connectedTests = tasks.named {
    it.startsWith("connected") && it.endsWith("AndroidTest")
  }
  tasks.register<SaveBenchmarksTask>("saveBenchmarkResults") {
    mustRunAfter(connectedTests)
    this.results.set(results)
    this.savedResults.set(savedResults)
  }
  tasks.register<CompareBenchmarksTask>("compareBenchmarkResults") {
    mustRunAfter(connectedTests)
    rootDirectory.set(isolated.rootProject.projectDirectory)
    before.set(savedResults)
    after.set(results)
    targetMetric.set(providers.gradleProperty("metric"))
    control.set(providers.gradleProperty("control"))
    implementation.set(providers.gradleProperty("implementation"))
    failOnRegression.set(
      providers.gradleProperty("failOnRegression")
        .map {
          it != "false"
        }
        .orElse(false)
    )
  }
  tasks.register<DescribeBenchmarksTask>("describeBenchmarkResults") {
    mustRunAfter(connectedTests)
    rootDirectory.set(isolated.rootProject.projectDirectory)
    this.results.set(results)
    control.set(providers.gradleProperty("control"))
  }
  tasks.register<DescribeProfilesTask>("describeBenchmarkProfiles") {
    mustRunAfter(connectedTests)
    this.results.set(results)
    benchmark.set(providers.gradleProperty("benchmark"))
  }
}

/** Saves results of the last run to compare with, together with earlier runs of the same build */
@DisableCachingByDefault(because = "Copies files")
abstract class SaveBenchmarksTask : DefaultTask() {
  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val results: DirectoryProperty

  @get:Internal
  abstract val savedResults: DirectoryProperty

  @TaskAction
  fun save() {
    val runs = SavedRuns(savedResults.get().asFile)
    val run = runs.add(results.get().asFile)
    runs.saveBaseline(run.build)
    runs.keep(run.build)
    logger.quiet("Saved runs of the same build: ${runs.of(run.build).size}")
  }
}

/**
 * Change of each metric is the difference of medians,
 * with 95% confidence interval of the shift between runs: when the interval doesn't include zero,
 * the change is clear, otherwise it's smaller than measurements can tell.
 * Some of many metrics change clearly by chance,
 * so intervals are widened for the number of metrics,
 * for [targetMetric] separately from others, so that it's measured more precisely.
 * Sampled metrics, e.g. frame durations, are compared by their percentiles in each iteration.
 * Times of [control] implementation, which code doesn't change, show how much faster or slower
 * device became between runs, e.g. when it heats up,
 * so changes of times of other implementations are measured relative to their median change.
 * Times of the same code also change between runs differently for each test,
 * e.g. because of garbage collections,
 * so intervals are widened by the spread of changes of control.
 * Outputs saved by tests, e.g. drawings, are compared too.
 * Results of the last run are saved with earlier runs of the same build,
 * which are compared together, so repeated runs narrow intervals.
 */
@DisableCachingByDefault(because = "Comparison is printed")
abstract class CompareBenchmarksTask : DefaultTask() {
  @get:Internal
  abstract val rootDirectory: DirectoryProperty

  /** Saved runs, see [SavedRuns] */
  @get:Internal
  abstract val before: DirectoryProperty

  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val after: DirectoryProperty

  @get:Input
  @get:Optional
  abstract val targetMetric: Property<String>

  @get:Input
  @get:Optional
  abstract val control: Property<String>

  /** Only results of this implementation are compared */
  @get:Input
  @get:Optional
  abstract val implementation: Property<String>

  /** Task fails when a metric is higher or an output changed */
  @get:Input
  abstract val failOnRegression: Property<Boolean>

  @TaskAction
  fun compare() {
    val root = rootDirectory.get().asFile
    val target = targetMetric.orNull
    val control = control.orNull
    val implementation = implementation.orNull
    fun isCompared(name: String): Boolean = if (implementation != null) {
      implementationOf(name) == implementation
    } else {
      implementationOf(name) != control
    }
    fun isComparedOutput(output: File): Boolean = isCompared(testNameOf(output).substringAfter('.'))
    val savedRuns = SavedRuns(before.get().asFile)
    val baseline = savedRuns.baseline
      ?: fatal("No saved results, saveBenchmarkResults saves results of the last run")
    val current = savedRuns.add(after.get().asFile)
    savedRuns.keep(baseline, current.build)
    // A run of the saved build, e.g. without changes, is compared with its earlier runs
    val savedBefore = savedRuns.of(baseline).filter {
      it.id != current.id
    }
    val savedAfter = if (current.build == baseline) listOf(current) else savedRuns.of(current.build)
    if (savedBefore.isEmpty()) {
      fatal("Only results of the last run are saved, run tests again to compare with them")
    }
    val before = loadRuns(savedBefore)
    val after = loadRuns(savedAfter)
    val outputsBefore = loadOutputs(savedBefore.last().directory).filterValues(::isComparedOutput)
    val outputsAfter = loadOutputs(current.directory).filterValues(::isComparedOutput)
    val compared = after.values
      .filter {
        isCompared(it.name) && it.key in before
      }
      .sortedBy { it.key }
    val comparedOutputs = outputsAfter.keys.intersect(outputsBefore.keys).sorted()
    if (compared.isEmpty() && comparedOutputs.isEmpty()) {
      val of = if (implementation != null) " of $implementation" else ""
      fatal("No results$of to compare, both runs must have them")
    }
    logger.quiet(
      "Runs: ${savedBefore.size} before, ${savedAfter.size} after, " +
        "runs of the same build are joined"
    )
    val runsInOrder = (savedBefore + savedAfter).sortedBy { it.id }
    describeRuns("Screen", "results and outputs", runsInOrder.mapNotNull { it.screen })
    describeRuns("Thermal status", "results", runsInOrder.mapNotNull { it.thermal })

    val metrics = compared.flatMap {
      it.metrics.keys.intersect(before.getValue(it.key).metrics.keys)
    }
    val targetCount = metrics.count {
      it == target
    }
    val otherCount = metrics.size - targetCount
    // Dry runs save only outputs
    if (target != null && compared.isNotEmpty() && targetCount == 0) {
      fatal("No results of metric $target")
    }
    val targetZ = criticalValue(CONFIDENCE_ERROR / maxOf(1, targetCount))
    val otherZ = criticalValue(CONFIDENCE_ERROR / maxOf(1, otherCount))
    // Changes of logarithms of times of control between runs, for each metric
    val controls = before.values.filter {
      it.implementation == control && it.key in after
    }
    val controlChanges = controls
      .flatMap { saved ->
        saved.metrics
          .filterKeys { it.endsWith("Ns") }
          .mapNotNull { (metric, runs) ->
            after.getValue(saved.key).metrics[metric]?.let {
              metric to ln(median(it) / median(runs))
            }
          }
      }
      .groupBy(Pair<String, Double>::first, Pair<String, Double>::second)
    val drifts = controlChanges.mapValues { median(it.value) }
    // Standard deviation of changes estimated by their median absolute deviation,
    // which ignores rare outliers
    val noises = controlChanges.mapValues { (metric, changes) ->
      val deviations = changes.map {
        abs(it - drifts.getValue(metric))
      }
      1.4826 * median(deviations)
    }

    if (compared.isNotEmpty()) {
      val metricsTogether = if (target != null) {
        "$targetCount * $target together and for $otherCount other metrics together"
      } else {
        "all $otherCount metrics together"
      }
      logger.quiet("Changes with 95% confidence intervals for $metricsTogether")
    }
    if (control != null && compared.isNotEmpty()) {
      if (controlChanges.isEmpty()) {
        fatal(
          "No times of $control in both runs, changes of other times are measured relative to them"
        )
      }
      for ((metric, changes) in controlChanges) {
        val drift = formatRatio(drifts.getValue(metric))
        val noise = "±%.1f%%".format(Locale.ROOT, noises.getValue(metric) * 100)
        logger.quiet(
          "$metric is relative to $control: " +
            "it changed by $drift in median of ${changes.size} tests, " +
            "intervals include spread $noise of changes between tests"
        )
      }
    }
    val changes = mutableMapOf<String, MutableList<String>>()
    val classNames = compared.map { it.className }
    val classPrefix = classNames.distinct().size > 1
    for (result in compared) {
      logger.quiet(result.key)
      val saved = before.getValue(result.key)
      val name = if (classPrefix) "${result.className}.${result.name}" else result.name
      for (metric in (saved.metrics.keys + result.metrics.keys).sorted()) {
        val runsBefore = saved.metrics[metric].orEmpty()
        val runsAfter = result.metrics[metric].orEmpty()
        val mark = if (metric == target) "*" else " "
        if (runsBefore.isEmpty() || runsAfter.isEmpty()) {
          val only = if (runsAfter.isEmpty()) "before" else "after"
          logger.quiet("%s %-24s only %s".format(Locale.ROOT, mark, metric, only))
          continue
        }
        val z = if (metric == target) targetZ else otherZ
        val medianBefore = median(runsBefore)
        val medianAfter = median(runsAfter)
        val drift = drifts[metric]
        val shift: Change
        val estimate: Double
        val format: (Double) -> String
        if (drift != null) {
          shift = relativeChange(
            runsBefore,
            runsAfter,
            drift,
            noises.getValue(metric),
            z,
            controlChanges.getValue(metric).size - 1
          )
          estimate = shift.median
          format = ::formatRatio
        } else {
          shift = change(runsBefore, runsAfter, z)
          estimate = medianAfter - medianBefore
          format = {
            formatChange(it, medianBefore)
          }
        }
        changes.getOrPut(shift.verdict) { mutableListOf() } += "$name $metric ${format(estimate)}"
        logger.quiet(
          "%s %-24s %12s -> %-12s %-30s %s".format(
            Locale.ROOT,
            mark,
            metric,
            formatValue(metric, medianBefore),
            formatValue(metric, medianAfter),
            shift.format(estimate, format),
            shift.verdict
          )
        )
      }
    }

    val changedOutputs = mutableListOf<String>()
    for (path in comparedOutputs) {
      val output = outputsAfter.getValue(path)
      val savedOutput = outputsBefore.getValue(path)
      val difference = differenceOf(savedOutput, output) ?: continue
      if (changedOutputs.isEmpty()) {
        logger.quiet("")
        logger.quiet("Changed outputs")
      }
      changedOutputs += testNameOf(output)
      logger.quiet("${testNameOf(output)}: $difference")
      logger.quiet("  before: ${savedOutput.relativeTo(root).invariantSeparatorsPath}")
      logger.quiet("  after: ${output.relativeTo(root).invariantSeparatorsPath}")
    }

    logger.quiet("")
    if (compared.isNotEmpty()) {
      val counts = VERDICTS.mapNotNull { verdict ->
        changes[verdict]?.let { "${it.size} $verdict" }
      }
      logger.quiet("Summary: ${counts.joinToString()}")
      for (verdict in listOf("higher", "lower")) {
        changes[verdict]?.let {
          logger.quiet("${verdict.replaceFirstChar(Char::uppercaseChar)}: ${it.joinToString()}")
        }
      }
    }
    if (comparedOutputs.isNotEmpty()) {
      val same = comparedOutputs.size - changedOutputs.size
      if (changedOutputs.isEmpty()) {
        logger.quiet("Outputs: $same same")
      } else {
        logger.quiet(
          "Outputs: $same same, ${changedOutputs.size} changed: ${changedOutputs.joinToString()}"
        )
      }
    }
    val newResults = after.values.count {
      isCompared(it.name) && it.key !in before
    } + (outputsAfter.keys - outputsBefore.keys).size
    if (newResults > 0) {
      logger.quiet("Without saved results to compare with: $newResults")
    }
    val notRepeated = before.values.count {
      isCompared(it.name) && it.key !in after
    } + (outputsBefore.keys - outputsAfter.keys).size
    if (notRepeated > 0) {
      logger.quiet("Saved results that didn't run again: $notRepeated")
    }
    val regressions = changes["higher"].orEmpty().size
    if (failOnRegression.get() && (regressions > 0 || changedOutputs.isNotEmpty())) {
      fatal("$regressions metrics are higher, ${changedOutputs.size} outputs changed")
    }
  }

  // States of device that change results, e.g. screen or thermal status
  private fun describeRuns(name: String, changed: String, values: List<String>) {
    val distinct = values.distinct()
    if (distinct.size > 1) {
      logger.quiet(
        "$name changed between runs, $changed differ because of it too: " +
          distinct.joinToString(" -> ")
      )
    } else {
      distinct.firstOrNull()?.let {
        logger.quiet("$name: $it")
      }
    }
  }
}

/**
 * Tests named `operation_Implementation`, e.g. `layout_Text` and `layout_BoringOrStaticLayout`,
 * are shown side by side, each compared with the best of other implementations,
 * or with [control], e.g. with the original code when variants of it are measured in one run.
 */
@DisableCachingByDefault(because = "Results are printed")
abstract class DescribeBenchmarksTask : DefaultTask() {
  @get:Internal
  abstract val rootDirectory: DirectoryProperty

  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val results: DirectoryProperty

  /** Implementation that others are compared with, together with their outputs */
  @get:Input
  @get:Optional
  abstract val control: Property<String>

  @TaskAction
  fun describe() {
    val z = criticalValue(CONFIDENCE_ERROR)
    val control = control.orNull
    val directory = results.get().asFile
    val groups = loadResults(directory)
      .ifEmpty { fatal("No benchmark results in $directory") }
      .groupBy { "${it.className}.${it.operation} on ${it.device}" }
    val comparedWith = if (control != null) {
      "$control: $BEST better, $WORST worse"
    } else {
      "the best of other implementations: $BEST the best, $WORST the worst"
    }
    logger.quiet(
      "Each result is compared with $comparedWith, ≈ difference within 95% confidence interval"
    )
    for ((group, groupResults) in groups.toSortedMap()) {
      val implementations = groupResults
        .map {
          it.implementation.ifEmpty { "Result" }
        }
        .distinct()
        .sorted()
      val controlIndex = implementations.indexOf(control)
      logger.quiet("")
      logger.quiet("#### $group")
      val metrics = groupResults
        .flatMap { it.metrics.keys }
        .distinct()
        .sortedWith(
          compareBy<String> { !it.endsWith("Ns") }
            .thenBy { it }
        )
      for (metric in metrics) {
        logger.quiet("")
        logger.quiet("**$metric**")
        logger.quiet("")
        logger.quiet("| Parameters | ${implementations.joinToString(" | ")} |")
        logger.quiet("|---|${"---|".repeat(implementations.size)}")
        val rowResults = groupResults
          .groupBy {
            it.parameters.ifEmpty { "—" }
          }
          .toSortedMap()
        val rows = rowResults.map { (parameters, row) ->
          val runs = implementations.map { implementation ->
            val result = row.find {
              it.implementation.ifEmpty { "Result" } == implementation
            }
            result?.metrics?.get(metric).orEmpty()
          }
          parameters to runs.mapIndexed { index, cell ->
            val others = when (controlIndex) {
              -1 -> runs.filterIndexed { other, it ->
                other != index && it.isNotEmpty()
              }
              index -> emptyList()
              else -> listOf(runs[controlIndex]).filter { it.isNotEmpty() }
            }
            if (cell.isEmpty()) "—" else formatResult(metric, cell, others, z)
          }
        }
        for ((parameters, cells) in rows) {
          logger.quiet("| $parameters | ${cells.joinToString(" | ")} |")
        }
        if (implementations.size > 1) {
          logger.quiet("")
          implementations.forEachIndexed { index, implementation ->
            if (index == controlIndex) {
              return@forEachIndexed
            }
            val best = rows.count { it.second[index].startsWith(BEST) }
            val worst = rows
              .filter { it.second[index].startsWith(WORST) }
              .map { it.first }
            val worstList = if (worst.isEmpty()) "" else ": ${worst.joinToString()}"
            logger.quiet(
              "$implementation: $BEST $best of ${rows.size}, $WORST ${worst.size}$worstList"
            )
          }
        }
      }
    }
    if (control != null) {
      describeOutputs(directory, control)
    }
  }

  // Outputs of other implementations are compared with outputs of control with the same parameters
  private fun describeOutputs(directory: File, control: String) {
    val root = rootDirectory.get().asFile
    val compared = loadOutputs(directory).toSortedMap().values.mapNotNull { output ->
      // E.g. output of layout_TextA_text_short_ is compared with output of layout_Text_text_short_
      val (className, test) = testNameOf(output).split('.', limit = 2)
      val implementation = implementationOf(test)
      val operation = test.substringBefore('_')
      val parameters = test.removePrefix("${operation}_$implementation")
      val controlName = "$className.${operation}_$control$parameters.output.${output.extension}"
      val controlOutput = output.resolveSibling(controlName)
      (output to controlOutput).takeIf {
        implementation.isNotEmpty() && implementation != control && controlOutput.isFile
      }
    }
    if (compared.isEmpty()) {
      return
    }
    logger.quiet("")
    val different = mutableListOf<String>()
    for ((output, controlOutput) in compared) {
      val difference = differenceOf(controlOutput, output) ?: continue
      different += testNameOf(output)
      logger.quiet("${testNameOf(output)}: $difference")
      logger.quiet("  $control: ${controlOutput.relativeTo(root).invariantSeparatorsPath}")
      logger.quiet("  output: ${output.relativeTo(root).invariantSeparatorsPath}")
    }
    val same = "Outputs: ${compared.size - different.size} same as $control"
    if (different.isEmpty()) {
      logger.quiet(same)
    } else {
      logger.quiet("$same, ${different.size} different: ${different.joinToString()}")
    }
  }
}

// Probability that the true change is outside of its confidence interval
private const val CONFIDENCE_ERROR = 0.05

private val PERCENTILES = intArrayOf(50, 90, 99)

private val VERDICTS = listOf("higher", "lower", "no clear change", "too few runs")

private const val BEST = "🟢"
private const val WORST = "🔴"

// Benchmark library prefixes names with suppressed errors, e.g. ACTIVITY-MISSING_layout_Text
private val SUPPRESSED_ERRORS = Regex("""^([A-Z][A-Z0-9-]*_)+""")

// Outputs saved by tests, e.g. TextBenchmarks.draw_Text_text_short_.output.png
private val OUTPUT_FILE = Regex("""(.+)\.output\.\w+""")

/** [name] includes parameters in brackets, e.g. `layout_Text[text=bold]` */
internal class BenchmarkResult(
  val className: String,
  val name: String,
  val device: String,
  val metrics: Map<String, List<Double>>,
  val methodTrace: File?
) {
  val key: String
    get() = "$className.$name on $device"

  private val method: String
    get() = name.substringBefore('[')

  val operation: String
    get() = method.substringBefore('_')

  val implementation: String
    get() = implementationOf(name)

  val parameters: String
    get() = name.substringAfter('[', "").removeSuffix("]")

  fun join(other: BenchmarkResult): BenchmarkResult {
    val joined = (metrics.keys + other.metrics.keys).associateWith {
      metrics[it].orEmpty() + other.metrics[it].orEmpty()
    }
    return BenchmarkResult(className, name, device, joined, other.methodTrace ?: methodTrace)
  }
}

// Saved by MicroBenchmarkRunner, results of benchmarks in separate process don't have it
private const val RUN_INFO = "run.txt"

// More runs narrow intervals, but the earliest ones may be measured in other conditions
private const val RUNS_OF_BUILD = 5

/** Results of one run, without method traces */
private class SavedRun(val directory: File) {
  private val info = File(directory, RUN_INFO).readLines().associate {
    it.substringBefore('=') to it.substringAfter('=')
  }

  val id: Long = directory.name.toLong()

  /** Checksum of the app, tests and arguments of benchmark library */
  val build: String = info.getValue("build")

  val screen: String? = info["screen"]

  /** The highest thermal status of device during the run, e.g. none or light */
  val thermal: String? = info["thermal"]
}

/** Runs of the build to compare with and of the last compared build */
private class SavedRuns(private val directory: File) {
  private val baselineFile = File(directory, "baseline.txt")
  private val runsDirectory = File(directory, "runs")

  /** Build of results to compare with */
  val baseline: String?
    get() = if (baselineFile.exists()) baselineFile.readText() else null

  fun saveBaseline(build: String) = baselineFile.writeText(build)

  fun of(build: String): List<SavedRun> = all().filter {
    it.build == build
  }

  fun add(results: File): SavedRun {
    val info = results.walk()
      .find {
        it.name == RUN_INFO
      }
      ?.readText()
    val id = info
      ?.lineSequence()
      ?.find { it.startsWith("id=") }
      ?.substringAfter('=')
      ?: System.currentTimeMillis().toString()
    val run = File(runsDirectory, id)
    run.deleteRecursively()
    results.walk()
      .filter {
        it.name.endsWith("benchmarkData.json") || OUTPUT_FILE.matches(it.name)
      }
      .forEach {
        it.copyTo(File(run, it.relativeTo(results).path))
      }
    run.mkdirs()
    File(run, RUN_INFO).writeText(info ?: "build=$id\n")
    return SavedRun(run)
  }

  /** Removes runs of other builds and the earliest runs, as well as files of older versions */
  fun keep(vararg builds: String) {
    directory.listFiles().orEmpty()
      .filter {
        it != baselineFile && it != runsDirectory
      }
      .forEach {
        it.deleteRecursively()
      }
    for ((build, runs) in all().groupBy { it.build }) {
      val removed = if (build in builds) runs.dropLast(RUNS_OF_BUILD) else runs
      removed.forEach {
        it.directory.deleteRecursively()
      }
    }
  }

  private fun all(): List<SavedRun> =
    runsDirectory.listFiles().orEmpty().map(::SavedRun).sortedBy { it.id }
}

/** Results of the same tests in several runs are joined */
private fun loadRuns(runs: List<SavedRun>): Map<String, BenchmarkResult> =
  runs.flatMap { loadResults(it.directory) }
    .groupBy { it.key }
    .mapValues { (_, results) ->
      results.reduce(BenchmarkResult::join)
    }

// Names of outputs have '_' instead of brackets, e.g. draw_Text_text_short_
private fun implementationOf(name: String): String =
  name.substringBefore('[').substringAfter('_', "").substringBefore('_')

internal fun loadResults(dir: File): List<BenchmarkResult> {
  val files = dir.walk()
    .filter { it.name.endsWith("benchmarkData.json") }
    .sorted()
    .toList()
  return files.flatMap { file ->
    val data = Json.parseToJsonElement(file.readText()).jsonObject
    val build = data.getValue("context").jsonObject.getValue("build").jsonObject
    val device = build.getValue("model").jsonPrimitive.content
    val benchmarks = data.getValue("benchmarks").jsonArray.map { it.jsonObject }
    benchmarks.map { benchmark ->
      val metrics = mutableMapOf<String, List<Double>>()
      (benchmark["metrics"] as? JsonObject).orEmpty().forEach { (metric, result) ->
        val runs = result.jsonObject.getValue("runs").jsonArray.map { it.jsonPrimitive.double }
        // Counts per operation are whole,
        // fractions of microbenchmark results are noise of averaging over repetitions
        metrics[metric] = if (metric.endsWith("Count")) runs.map { round(it) } else runs
      }
      (benchmark["sampledMetrics"] as? JsonObject).orEmpty().forEach { (metric, result) ->
        val runs = result.jsonObject.getValue("runs").jsonArray
          .map { run ->
            run.jsonArray.map { it.jsonPrimitive.double }
          }
          .filter { it.isNotEmpty() }
        for (percentile in PERCENTILES) {
          metrics["${metric}P$percentile"] = runs.map {
            percentileOf(it, percentile)
          }
        }
      }
      val profilerOutputs = (benchmark["profilerOutputs"] as? JsonArray).orEmpty()
        .map { it.jsonObject }
      val methodTrace = profilerOutputs.find {
        it["type"]?.jsonPrimitive?.content == "MethodTrace"
      }
      BenchmarkResult(
        benchmark.getValue("className").jsonPrimitive.content.substringAfterLast('.'),
        benchmark.getValue("name").jsonPrimitive.content.replace(SUPPRESSED_ERRORS, ""),
        device,
        metrics,
        methodTrace?.let { file.resolveSibling(it.getValue("filename").jsonPrimitive.content) }
      )
    }
  }
}

/** Outputs by paths relative to [dir], which include device */
private fun loadOutputs(dir: File): Map<String, File> = dir.walk()
  .filter {
    it.isFile && OUTPUT_FILE.matches(it.name)
  }
  .associateBy { it.relativeTo(dir).invariantSeparatorsPath }

/** E.g. `TextBenchmarks.draw_Text_text_short_` */
private fun testNameOf(output: File): String = OUTPUT_FILE.matchEntire(output.name)!!.groupValues[1]

/** Null when outputs are the same */
private fun differenceOf(before: File, after: File): String? {
  if (before.readBytes().contentEquals(after.readBytes())) {
    return null
  }
  if (after.extension != "png") {
    return "changed"
  }
  val imageBefore = ImageIO.read(before)
  val imageAfter = ImageIO.read(after)
  if (imageBefore.width != imageAfter.width || imageBefore.height != imageAfter.height) {
    val sizeBefore = "${imageBefore.width}×${imageBefore.height}"
    return "size $sizeBefore -> ${imageAfter.width}×${imageAfter.height}"
  }
  val pixels = (0 until imageAfter.height).flatMap { y ->
    (0 until imageAfter.width)
      .filter { x ->
        imageBefore.getRGB(x, y) != imageAfter.getRGB(x, y)
      }
      .map { x ->
        x to y
      }
  }
  if (pixels.isEmpty()) {
    // Encoding of the same image can change, e.g. after update of Android
    return null
  }
  val left = pixels.minOf { it.first }
  val top = pixels.minOf { it.second }
  val width = pixels.maxOf { it.first } - left + 1
  val height = pixels.maxOf { it.second } - top + 1
  return "${pixels.size} pixels differ in $width×$height at $left,$top"
}

private fun percentileOf(values: List<Double>, percentile: Int): Double =
  values.sorted()[maxOf(0, ceil(percentile / 100.0 * values.size).toInt() - 1)]

private fun median(values: List<Double>): Double {
  val sorted = values.sorted()
  val middle = sorted.size / 2
  return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
}

/** Bounds of confidence interval are infinite when runs are too few */
private class Change(val median: Double, val low: Double, val high: Double) {
  val verdict: String
    get() = when {
      low.isInfinite() -> "too few runs"
      low > 0 -> "higher"
      high < 0 -> "lower"
      else -> "no clear change"
    }

  fun format(estimate: Double, format: (Double) -> String): String = if (low.isInfinite()) {
    format(estimate)
  } else {
    "${format(estimate)} (${format(low)}..${format(high)})"
  }
}

private fun formatChange(value: Double, base: Double): String = if (base != 0.0) {
  "%+.1f%%".format(Locale.ROOT, value / abs(base) * 100)
} else {
  "%+.2f".format(Locale.ROOT, value)
}

/**
 * Hodges-Lehmann estimate of change from [before] to [after],
 * median of differences between all their pairs,
 * with confidence interval of Mann-Whitney U test for critical value [z] of normal distribution
 */
private fun change(before: List<Double>, after: List<Double>, z: Double): Change {
  val differences = after
    .flatMap { value ->
      before.map {
        value - it
      }
    }
    .sorted()
  if (differences.first() == differences.last()) {
    // E.g. counts that don't vary between runs
    return Change(differences.first(), differences.first(), differences.first())
  }
  val count = differences.size
  // Number of the lowest and the highest differences outside of the interval
  val outside = floor(count / 2.0 - z * sqrt(count * (before.size + after.size + 1) / 12.0)).toInt()
  if (outside < 1) {
    return Change(median(differences), Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)
  }
  return Change(median(differences), differences[outside - 1], differences[count - outside])
}

/**
 * Change of logarithms of times from [before] to [after] minus [drift] of control,
 * with interval widened by standard deviation [noise] of changes between runs,
 * estimated with [degrees] of freedom
 */
private fun relativeChange(
  before: List<Double>,
  after: List<Double>,
  drift: Double,
  noise: Double,
  z: Double,
  degrees: Int
): Change {
  val change = change(before.map(::ln), after.map(::ln), z)
  val median = change.median - drift
  if (change.low.isInfinite()) {
    return Change(median, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)
  }
  // Both errors add up like independent normal errors,
  // noise estimated from few tests is uncertain itself
  val spread = if (degrees > 0) studentCriticalValue(z, degrees) * noise else 0.0
  return Change(
    median,
    median - hypot(change.median - change.low, spread),
    median + hypot(change.high - change.median, spread)
  )
}

/**
 * Critical value of Student's t-distribution with [degrees] of freedom
 * by Cornish-Fisher expansion of critical value [z] of normal distribution
 */
private fun studentCriticalValue(z: Double, degrees: Int): Double {
  val v = degrees.toDouble()
  return z + (z.pow(3) + z) / (4 * v) +
    (5 * z.pow(5) + 16 * z.pow(3) + 3 * z) / (96 * v * v) +
    (3 * z.pow(7) + 19 * z.pow(5) + 17 * z.pow(3) - 15 * z) / (384 * v * v * v)
}

private fun formatRatio(logarithm: Double): String =
  "%+.1f%%".format(Locale.ROOT, (exp(logarithm) - 1) * 100)

/**
 * Formats median of [runs] and its comparison with the best of [others],
 * e.g. `🟢 4.34 µs (1.16× faster)` or `🔴 54 (+42)`
 */
private fun formatResult(
  metric: String,
  runs: List<Double>,
  others: List<List<Double>>,
  z: Double
): String {
  val value = formatValue(metric, median(runs))
  if (others.isEmpty()) {
    return value
  }
  val isTime = metric.endsWith("Ns")
  // Times are compared by ratio, which is a change of logarithms,
  // counts by difference, because they are often zero
  fun compareWith(other: List<Double>): Change =
    if (isTime) change(other.map(::ln), runs.map(::ln), z) else change(other, runs, z)
  fun isDifferent(other: List<Double>): Boolean =
    isTime || formatValue(metric, median(other)) != value
  val best = others.minBy { median(it) }
  val worst = others.maxBy { median(it) }
  val toBest = compareWith(best)
  val mark = when {
    toBest.high < 0 && isDifferent(best) -> "$BEST "
    compareWith(worst).low > 0 && isDifferent(worst) -> "$WORST "
    else -> ""
  }
  val unclear = if (toBest.low > 0 || toBest.high < 0) "" else "≈"
  val comparison = when {
    !isDifferent(best) -> "same"
    !isTime -> "$unclear%+.0f".format(Locale.ROOT, toBest.median)
    toBest.median > 0 -> "$unclear%.2f× slower".format(Locale.ROOT, exp(toBest.median))
    else -> "$unclear%.2f× faster".format(Locale.ROOT, exp(-toBest.median))
  }
  return "$mark$value ($comparison)"
}

private fun formatValue(metric: String, value: Double): String = when {
  metric.endsWith("Count") -> if (abs(value - round(value)) < 0.05) {
    "%.0f".format(Locale.ROOT, value)
  } else {
    "%.1f".format(Locale.ROOT, value)
  }
  !metric.endsWith("Ns") -> "%.2f".format(Locale.ROOT, value)
  value < 1_000 -> "%.0f ns".format(Locale.ROOT, value)
  value < 1_000_000 -> "${(value / 1_000).toSignificant()} µs"
  else -> "${(value / 1_000_000).toSignificant()} ms"
}

private fun Double.toSignificant(): String =
  "%.${if (this < 10) 2 else if (this < 100) 1 else 0}f".format(Locale.ROOT, this)

/** Two-sided critical value of standard normal distribution for probability [error] */
private fun criticalValue(error: Double): Double {
  var low = 0.0
  var high = 10.0
  repeat(60) {
    val middle = (low + high) / 2
    if (erfc(middle / sqrt(2.0)) > error) {
      low = middle
    } else {
      high = middle
    }
  }
  return (low + high) / 2
}

// Abramowitz and Stegun 7.1.26 for x >= 0, error is less than 1.5e-7
private fun erfc(x: Double): Double {
  val t = 1 / (1 + 0.3275911 * x)
  val polynomial = t * (0.254829592 + t * (-0.284496736 +
    t * (1.421413741 + t * (-1.453152027 + t * 1.061405429))))
  return polynomial * exp(-x * x)
}
