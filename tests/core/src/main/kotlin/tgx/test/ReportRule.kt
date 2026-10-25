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
