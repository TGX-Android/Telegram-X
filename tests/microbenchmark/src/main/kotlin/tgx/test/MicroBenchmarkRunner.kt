package tgx.test

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.benchmark.Outputs
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnitRunner
import org.thunderdog.challegram.tool.UI
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Keeps [BenchmarkActivity] open during all tests, like AndroidBenchmarkRunner of AndroidX */
class MicroBenchmarkRunner : AndroidJUnitRunner() {
  private val startTime = System.currentTimeMillis()

  // Throttled device runs code slower
  @Volatile
  private var thermalStatus: Int? = null

  override fun onStart() {
    outputsDirectory.deleteRecursively()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val powerManager = targetContext.getSystemService(PowerManager::class.java)
      powerManager.addThermalStatusListener(targetContext.mainExecutor) {
        thermalStatus = maxOf(thermalStatus ?: it, it)
      }
    }
    super.onStart()
  }

  // Runner finishes activities before and after each test and waits here until they stop
  override fun waitForActivitiesToComplete() {
    if (!BenchmarkActivity.isInForeground) {
      val intent = Intent(targetContext, BenchmarkActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
      targetContext.startActivity(intent)
      waitUntil("BenchmarkActivity didn't open") {
        BenchmarkActivity.isInForeground
      }
    }
  }

  override fun finish(resultCode: Int, results: Bundle?) {
    saveRunInfo()
    packOutputs()
    super.finish(resultCode, results)
  }

  /**
   * `compareBenchmarkResults` joins results of runs of the same build of the app and tests
   * with the same arguments of benchmark library and shows screen that sizes of texts depend on,
   * and the highest thermal status of device during each run. Dry runs measure nothing, so they
   * aren't saved to compare with
   */
  @SuppressLint("RestrictedApi")
  private fun saveRunInfo() {
    val digest = MessageDigest.getInstance("SHA-256")
    val apks = listOf(targetContext.applicationInfo.sourceDir, context.applicationInfo.sourceDir)
    for (apk in apks) {
      ZipFile(apk).use { zip ->
        for (entry in zip.entries()) {
          digest.update("${entry.name} ${entry.crc}\n".toByteArray())
        }
      }
    }
    val arguments = InstrumentationRegistry.getArguments()
    val benchmarkKeys = arguments.keySet()
      .filter { it.startsWith("androidx.benchmark.") }
      .sorted()
    for (key in benchmarkKeys) {
      digest.update("$key=${arguments.getString(key)}\n".toByteArray())
    }
    val build = digest.digest().take(8).joinToString("") { "%02x".format(it) }
    val metrics = UI.getResources().displayMetrics
    val fontScale = UI.getResources().configuration.fontScale
    val screen = "${metrics.widthPixels}×${metrics.heightPixels}, " +
      "density ${metrics.density}, font scale $fontScale"
    val info = mutableListOf("id=$startTime", "build=$build", "screen=$screen")
    if (arguments.getString("androidx.benchmark.dryRunMode.enable") == "true") {
      info += "dryRun=true"
    }
    thermalStatus?.let {
      info += "thermal=${THERMAL_STATUSES.getOrNull(it) ?: it}"
    }
    File(Outputs.outputDirectory, "run.txt").writeText(info.joinToString("\n", postfix = "\n"))
  }

  @SuppressLint("RestrictedApi")
  private fun packOutputs() {
    // Gradle copies files of the output directory during the run, every few seconds, and checks
    // each file by a separate command of adb, which takes minutes for hundreds of outputs, so they
    // are copied as one file
    val files = outputsDirectory.listFiles().orEmpty().sortedBy { it.name }
    if (files.isEmpty()) {
      return
    }
    val packed = File(Outputs.outputDirectory, OUTPUTS_FILE)
    ZipOutputStream(packed.outputStream().buffered()).use { zip ->
      for (file in files) {
        zip.putNextEntry(ZipEntry(file.name))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
      }
    }
    outputsDirectory.deleteRecursively()
  }
}

// Outputs of tests, which `keepBenchmarkResults` unpacks next to results
private const val OUTPUTS_FILE = "outputs.zip"

/** Outputs of the current run, e.g. drawings of texts, until the runner packs them */
internal val outputsDirectory: File
  get() = File(
    InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
    "benchmark-outputs"
  )

// Names of PowerManager.THERMAL_STATUS_* by their values
private val THERMAL_STATUSES =
  listOf("none", "light", "moderate", "severe", "critical", "emergency", "shutdown")
