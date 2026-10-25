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
import java.util.zip.ZipFile

/** Keeps [BenchmarkActivity] open during all tests, like AndroidBenchmarkRunner of AndroidX */
class MicroBenchmarkRunner : AndroidJUnitRunner() {
  private val startTime = System.currentTimeMillis()

  // Throttled device runs code slower
  @Volatile
  private var thermalStatus: Int? = null

  override fun onStart() {
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
    super.finish(resultCode, results)
  }

  /**
   * `compareBenchmarkResults` joins results of runs of the same build of the app and tests
   * with the same arguments of benchmark library and shows screen that sizes of texts depend on,
   * and the highest thermal status of device during each run
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
    thermalStatus?.let {
      info += "thermal=${THERMAL_STATUSES.getOrNull(it) ?: it}"
    }
    File(Outputs.outputDirectory, "run.txt").writeText(info.joinToString("\n", postfix = "\n"))
  }
}

// Names of PowerManager.THERMAL_STATUS_* by their values
private val THERMAL_STATUSES =
  listOf("none", "light", "moderate", "severe", "critical", "emergency", "shutdown")
