package tgx.test

import Sdk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register
import org.gradle.work.DisableCachingByDefault
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

private val RESULT_DIRECTORIES = listOf(
  "app/build/test-results",
  "app/build/outputs/androidTest-results",
  "tests/ui/build/outputs/androidTest-results",
  "tests/benchmark/build/outputs/androidTest-results",
  "tests/microbenchmark/build/outputs/androidTest-results"
)

internal const val MEDIA_DIRECTORY = "tests/ui/build/outputs"

internal const val MEDIA_COPIES_DIRECTORY = "tests/ui/checks/media"

private val CHECK_DIRECTORIES = listOf(
  "tests/unit/checks",
  "tests/integration/checks",
  "tests/ui/checks",
  "tests/benchmark/checks",
  "tests/microbenchmark/checks"
)

fun Project.configureCheckResults() {
  val savedResults = layout.buildDirectory.dir("check-results")
  tasks.register<Delete>("clearCheckResults") {
    delete(savedResults)
    delete(files(RESULT_DIRECTORIES).asFileTree.matching {
      include("**/TEST-*.xml")
    })
    delete(files(MEDIA_DIRECTORY).asFileTree.matching {
      MEDIA_EXTENSIONS.forEach {
        include("**/*.$it")
      }
    })
    delete(MEDIA_COPIES_DIRECTORY)
  }
  tasks.register<SaveCheckResultsTask>("saveCheckResults") {
    rootDirectory.set(layout.projectDirectory)
    results.from(RESULT_DIRECTORIES)
    this.savedResults.set(savedResults)
  }
  tasks.register<DescribeCheckResultsTask>("describeCheckResults") {
    rootDirectory.set(layout.projectDirectory)
    results.from(RESULT_DIRECTORIES)
    this.savedResults.set(savedResults)
    checks.from(CHECK_DIRECTORIES)
    if (hasTestTools) {
      checks.from(TEST_TOOLS_DIRECTORIES)
    }
    report.set(layout.buildDirectory.file("check-results.md"))
  }
}

@DisableCachingByDefault(because = "Moves files")
abstract class SaveCheckResultsTask : DefaultTask() {
  @get:Internal
  abstract val rootDirectory: DirectoryProperty

  @get:Internal
  abstract val results: ConfigurableFileCollection

  @get:Internal
  abstract val savedResults: DirectoryProperty

  @TaskAction
  fun save() {
    val root = rootDirectory.get().asFile
    val saved = savedResults.get().asFile
    saved.deleteRecursively()
    for (file in findResultFiles(results) + findMediaFiles(root.resolve(MEDIA_DIRECTORY))) {
      file.copyTo(saved.resolve(file.relativeTo(root)))
      file.delete()
    }
  }
}

@DisableCachingByDefault(because = "Results of test runs are read every time")
abstract class DescribeCheckResultsTask : DefaultTask() {
  @get:Internal
  abstract val rootDirectory: DirectoryProperty

  @get:Internal
  abstract val results: ConfigurableFileCollection

  @get:Internal
  abstract val savedResults: DirectoryProperty

  @get:Internal
  abstract val checks: ConfigurableFileCollection

  @get:Internal
  abstract val report: RegularFileProperty

  @TaskAction
  fun describe() {
    val before = readResults(listOf(savedResults.get().asFile))
    val after = readResults(results)
    val checkClasses = findClasses(checks)
    val rows = mutableListOf<String>()
    var passedTests = 0
    for (run in (before.keys + after.keys).sortedWith(compareBy(Run::name, Run::target))) {
      val resultBefore = before[run]
      val resultAfter = after[run]
      val isFailing = (resultBefore ?: PASSES) != PASSES || (resultAfter ?: PASSES) != PASSES
      if (run.className in checkClasses || isFailing) {
        rows += "| `${run.name}` | ${run.target} | ${resultBefore ?: "—"} | ${resultAfter ?: "—"} |"
      } else if (resultAfter != null) {
        passedTests++
      }
    }
    val text = buildString {
      if (rows.isEmpty()) {
        appendLine("No checks were run.")
      } else {
        appendLine("| Check | Flavor, device | Before | After |")
        appendLine("|---|---|---|---|")
        rows.forEach(::appendLine)
      }
      if (passedTests > 0) {
        appendLine()
        appendLine("Other tests pass: $passedTests.")
      }
      describeMedia(rootDirectory.get().asFile, savedResults.get().asFile)?.let {
        appendLine()
        append(it)
      }
    }
    val reportFile = report.get().asFile
    reportFile.parentFile.mkdirs()
    reportFile.writeText(text)
    logger.lifecycle(text)
  }
}

private const val PASSES = "passes"

private data class Run(val className: String, val method: String, val target: String) {
  val name: String
    get() = "${className.substringAfterLast('.')}#$method"
}

private fun readResults(directories: Iterable<File>): Map<Run, String> {
  val results = mutableMapOf<Run, String>()
  for (file in findResultFiles(directories)) {
    // Unit tests run on JVM and save results of each variant in test<Flavor><BuildType>UnitTest,
    // tests on device save results in <buildType>/flavors/<flavor>/TEST-<device>.xml
    val isUnitTest = file.parentFile.parentFile.name == "test-results"
    val flavor = if (isUnitTest) {
      Sdk.VARIANTS.values
        .map { it.flavor }
        .firstOrNull {
          file.parentFile.name.startsWith("test" + it.replaceFirstChar(Char::uppercaseChar))
        }
    } else {
      file.parentFile.name.takeIf {
        file.parentFile.parentFile.name == "flavors"
      }
    } ?: "?"
    // Property "device" of results is serial number of device, which must not get into pull request
    val device = if (isUnitTest) "JVM" else file.nameWithoutExtension.removePrefix("TEST-")
    for (testCase in readTestCases(file)) {
      val run = Run(
        testCase.getAttribute("classname"),
        testCase.getAttribute("name"),
        "`$flavor`, $device"
      )
      results[run] = resultOf(testCase)
    }
  }
  return results
}

internal fun readTestCases(file: File): List<Element> = DocumentBuilderFactory.newInstance()
  .newDocumentBuilder()
  .parse(file)
  .getElementsByTagName("testcase")
  .elements()

internal fun resultOf(testCase: Element): String {
  val failure = testCase.getElementsByTagName("failure").elements().firstOrNull()
    ?: testCase.getElementsByTagName("error").elements().firstOrNull()
  val message = failure?.let(::messageOf)
  // Results of tests on device report failed assumptions as failures,
  // though Gradle counts them as skipped
  val isSkipped = testCase.getElementsByTagName("skipped").length > 0 ||
    message?.startsWith("AssumptionViolatedException") == true
  return when {
    isSkipped -> "skipped"
    message == null -> PASSES
    message.isEmpty() -> "fails without message"
    else -> "fails: `$message`"
  }
}

// Message without stack trace and package of exception,
// in one line that fits into code in a table cell
private fun messageOf(failure: Element): String {
  val message = failure.getAttribute("message")
    .ifEmpty { failure.textContent.substringBefore("\tat ") }
    .replace(Regex("""^([\w$]+\.)+(?=\w+(:|$))"""), "")
    .replace(Regex("""\s+"""), " ")
    .trim()
    .replace('`', '\'')
    .replace("|", "\\|")
  return if (message.length > MAX_MESSAGE_LENGTH) {
    message.take(MAX_MESSAGE_LENGTH - 1) + "…"
  } else {
    message
  }
}

private const val MAX_MESSAGE_LENGTH = 150

internal val MEDIA_EXTENSIONS = listOf("png", "mp4")

private fun findMediaFiles(directory: File): List<File> = directory.walk()
  .filter {
    it.isFile && it.extension in MEDIA_EXTENSIONS && !it.name.contains(".failure.")
  }
  .toList()

private fun describeMedia(root: File, savedResults: File): String? {
  fun findMedia(directory: File): Map<String, File> =
    findMediaFiles(directory).associateBy { it.relativeTo(directory).invariantSeparatorsPath }

  val before = findMedia(savedResults.resolve(MEDIA_DIRECTORY))
  val after = findMedia(root.resolve(MEDIA_DIRECTORY))
  if (before.isEmpty() && after.isEmpty()) {
    return null
  }
  root.resolve(MEDIA_COPIES_DIRECTORY).deleteRecursively()
  val branchUrl = checksBranchUrl(root)

  fun cell(key: String, file: File?, state: String): String {
    if (file == null) {
      return "—"
    }
    // Key starts with folder of connected or managed devices, then variant and device,
    // which are kept in names of copies
    val name = key.substringAfter('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
    val path = "$MEDIA_COPIES_DIRECTORY/$state/$name"
    file.copyTo(root.resolve(path))
    return when {
      branchUrl == null -> "`$path`"
      file.extension == "png" -> "![]($branchUrl/$path)"
      else -> "[recording]($branchUrl/$path)"
    }
  }

  return buildString {
    appendLine("| Screenshot or recording | Device | Before | After |")
    appendLine("|---|---|---|---|")
    for (key in (before.keys + after.keys).sorted()) {
      val file = after[key] ?: before.getValue(key)
      val cellBefore = cell(key, before[key], "before")
      val cellAfter = cell(key, after[key], "after")
      val title = file.nameWithoutExtension
      appendLine("| `$title` | ${file.parentFile.name} | $cellBefore | $cellAfter |")
    }
  }
}

private fun checksBranchUrl(root: File): String? {
  val builder = FileRepositoryBuilder().readEnvironment().findGitDir(root)
  val repository = runCatching(builder::build).getOrNull() ?: return null
  return repository.use {
    val origin = repository.config.getString("remote", "origin", "url") ?: return null
    val name = GITHUB_REPOSITORY.find(origin)?.groupValues?.get(1) ?: return null
    if (repository.fullBranch?.startsWith("refs/heads/") != true) {
      return null
    }
    "https://github.com/$name/raw/checks/${repository.branch}"
  }
}

private val GITHUB_REPOSITORY = Regex("""github\.com[:/](.+?)(\.git)?/?$""")

internal fun findResultFiles(directories: Iterable<File>): List<File> =
  directories.flatMap { directory ->
    directory.walk()
      .filter {
        it.isFile && it.name.startsWith("TEST-") && it.extension == "xml"
      }
      .toList()
  }

private val PACKAGE = Regex("""^package\s+([\w.]+)""", RegexOption.MULTILINE)
private val CLASS = Regex("""\bclass\s+(\w+)""")

private fun findClasses(directories: Iterable<File>): Set<String> =
  directories
    .flatMap { directory ->
      directory.walk()
        .filter {
          it.extension == "kt"
        }
        .toList()
    }
    .flatMap { file ->
      val text = file.readText()
      val packageName = PACKAGE.find(text)?.groupValues?.get(1)
      CLASS.findAll(text)
        .map {
          listOfNotNull(packageName, it.groupValues[1]).joinToString(".")
        }
        .toList()
    }
    .toSet()

private fun NodeList.elements(): List<Element> = (0 until length).map {
  item(it) as Element
}
