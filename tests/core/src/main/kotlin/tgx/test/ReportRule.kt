package tgx.test

import android.app.UiAutomation
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.ResultsReporter
import androidx.test.uiautomator.UiDevice
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File

private const val RECORDING_PATH = "/data/local/tmp/tgx-test-recording.mp4"

// Perfetto reads configs only from its own folder
private const val HEAPPROFD_CONFIG_PATH = "/data/misc/perfetto-configs/tgx-test-heapprofd.pbtxt"
private const val HEAPPROFD_TRACE_PATH =
  "/data/misc/perfetto-traces/tgx-test-heapprofd.perfetto-trace"

// Samples of native allocations of the app every 4 KB, dumped every 30 s, so that long runs show
// growth
private val HEAPPROFD_CONFIG = """
  buffers { size_kb: 131072 }
  data_sources {
    config {
      name: "android.heapprofd"
      heapprofd_config {
        sampling_interval_bytes: 4096
        process_cmdline: "APP"
        shmem_size_bytes: 8388608
        block_client: true
        continuous_dump_config { dump_phase_ms: 30000 dump_interval_ms: 30000 }
      }
    }
  }
  duration_ms: 3600000
""".trimIndent()

/** Gradle copies saved files to `build/outputs` of the test module */
class ReportRule(private val device: UiDevice) : TestRule {
  private var reporter: ResultsReporter? = null
  private var filePrefix = ""
  private var hasFiles = false

  fun screenshot(name: String) {
    device.takeScreenshot(newFile("$name.png", name))
  }

  // UiAutomation runs commands asynchronously and gives binary output, unlike runShellCommand
  fun recordScreen(name: String, block: () -> Unit) {
    val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
    val recording = uiAutomation.executeShellCommand("screenrecord $RECORDING_PATH")
    try {
      waitUntil("screenrecord didn't start") {
        uiAutomation.runCommand("pidof screenrecord").isNotEmpty()
      }
      block()
    } finally {
      // screenrecord completes video when interrupted and closes output when it exits
      uiAutomation.runCommand("pkill -INT screenrecord")
      ParcelFileDescriptor.AutoCloseInputStream(recording).use {
        it.readBytes()
      }
      val video = uiAutomation.executeShellCommand("cat $RECORDING_PATH")
      ParcelFileDescriptor.AutoCloseInputStream(video).use { input ->
        newFile("$name.mp4", name).outputStream().use {
          input.copyTo(it)
        }
      }
      uiAutomation.runCommand("rm $RECORDING_PATH")
    }
  }

  fun recordNativeHeap(name: String, block: () -> Unit) {
    // heapprofd of Perfetto records native allocations of the app that aren't freed, by call sites,
    // builds of tests are profileable by shell. `describeHeapProfiles` reads the trace
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val uiAutomation = instrumentation.uiAutomation
    val config = File(instrumentation.context.externalCacheDir, "heapprofd.pbtxt")
    config.writeText(HEAPPROFD_CONFIG.replace("APP", getApplicationId()))
    uiAutomation.runCommand("cp ${config.path} $HEAPPROFD_CONFIG_PATH")
    val perfetto = "perfetto --txt -c $HEAPPROFD_CONFIG_PATH -o $HEAPPROFD_TRACE_PATH --background"
    val pid = String(uiAutomation.runCommand(perfetto)).trim()
    try {
      // heapprofd attaches to the process in about a second
      Thread.sleep(2_000)
      block()
    } finally {
      // Perfetto writes the trace when it's stopped
      uiAutomation.runCommand("kill -TERM $pid")
      waitUntil("Perfetto didn't stop", 60_000) {
        String(uiAutomation.runCommand("ls /proc/$pid/cmdline")).isBlank()
      }
      val trace = uiAutomation.executeShellCommand("cat $HEAPPROFD_TRACE_PATH")
      ParcelFileDescriptor.AutoCloseInputStream(trace).use { input ->
        newFile("$name.heapprofd.perfetto-trace", name).outputStream().use {
          input.copyTo(it)
        }
      }
      uiAutomation.runCommand("rm $HEAPPROFD_TRACE_PATH $HEAPPROFD_CONFIG_PATH")
    }
  }

  private fun UiAutomation.runCommand(command: String): ByteArray =
    ParcelFileDescriptor.AutoCloseInputStream(executeShellCommand(command)).use {
      it.readBytes()
    }

  private fun newFile(name: String, title: String): File {
    val reporter = reporter ?: error("Files can be saved only while test is running")
    hasFiles = true
    return reporter.addNewFile("$filePrefix.$name".toFileName(), title)
  }

  private fun saveFailure(): String {
    screenshot("failure")
    newFile("failure.xml", "UI hierarchy").outputStream().use {
      device.dumpWindowHierarchy(it)
    }
    val crashes = runShellCommand("logcat -d -b crash")
    if (crashes.isNotBlank()) {
      newFile("crash.txt", "Crashes").writeText(crashes)
    }
    return crashes
  }

  override fun apply(base: Statement, description: Description) = object : Statement() {
    override fun evaluate() {
      reporter = ResultsReporter(description.displayName)
      filePrefix = "${description.testClass.simpleName}.${description.methodName}"
      hasFiles = false
      runShellCommand("logcat -b crash -c")
      try {
        base.evaluate()
      } catch (e: Throwable) {
        val crashes = runCatching(::saveFailure).getOrDefault("")
        if (crashes.contains(getApplicationId())) {
          throw AssertionError("App crashed during the test:\n$crashes", e)
        }
        throw e
      } finally {
        if (hasFiles) {
          reporter?.reportToInstrumentation()
        }
        reporter = null
      }
    }
  }
}

// Gradle copies files to the host, so their names must be valid on Windows,
// e.g. names of parameterized tests may contain ':'
private fun String.toFileName() = replace(Regex("""[<>:"/\\|?*]"""), "_")
