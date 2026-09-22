package tgx.baselineprofile

import android.app.Instrumentation
import androidx.test.uiautomator.UiDevice
import okio.IOException
import java.io.File

private const val FOLDER_NAME = "tgx-session"
private const val FILE_NAME = "snapshot.zip"

@Suppress("Deprecation")
private fun getTargetDirectory(instrumentation: Instrumentation): File {
  val file = instrumentation.context.externalMediaDirs.first()
  return File(file.parentFile, getApplicationId())
}

fun copySnapshotToTargetDevice(
  device: UiDevice,
  instrumentation: Instrumentation
) {
  val applicationId = getApplicationId()

  device.executeShellCommand("pm clear $applicationId")

  val perms = device.executeShellCommand("dumpsys package $applicationId")
    .lineSequence()
    .filter { it.contains("android.permission.") && it.contains("granted=false") }
    .mapNotNull { Regex("(android\\.permission\\.[A-Z_]+)").find(it)?.value }
    .distinct()

  perms.forEach { permission ->
    device.executeShellCommand("pm grant $applicationId $permission")
  }

  val publicDir = getTargetDirectory(instrumentation)
  val snapshotsDir = File(publicDir, FOLDER_NAME)
  val targetFile = File(snapshotsDir, FILE_NAME)
  println("Target: ${targetFile.path}")

  try {
    instrumentation.context.assets.open(FILE_NAME).close()
  } catch (e: IOException) {
    error("assets/$FILE_NAME is missing")
  }

  var success = false
  try {
    if (snapshotsDir.exists() || snapshotsDir.mkdirs()) {
      if (targetFile.exists() && !targetFile.delete()) {
        error("Unable to delete: $targetFile")
      }
      instrumentation.context.assets.open(FILE_NAME).use { input ->
        targetFile.outputStream().use { input.copyTo(it) }
      }
      success = true
    }
  } catch (e: Exception) {
    println(e)
  }

  if (!success) {
    println("Unable to access snapshots dir: $snapshotsDir")
    val tempFile = File(instrumentation.context.externalCacheDir, FILE_NAME)
    instrumentation.context.assets.open(FILE_NAME).use { input ->
      tempFile.outputStream().use { input.copyTo(it) }
    }
    device.executeShellCommand("mkdir -p ${targetFile.parentFile!!.absolutePath}")
    device.executeShellCommand("rm -f ${targetFile.absolutePath}")
    device.executeShellCommand("cp ${tempFile.absolutePath} ${targetFile.absolutePath}")
    if (!tempFile.delete()) {
      error("Unable to delete temp file: $tempFile")
    }
  }

  if (!targetFile.exists()) {
    error("Target is still unavailable: ${targetFile.path}")
  }
}

fun deleteSnapshotFromTargetDevice(
  device: UiDevice,
  instrumentation: Instrumentation
) {
  val publicDir = getTargetDirectory(instrumentation)
  val snapshotsDir = File(publicDir, FOLDER_NAME)
  val targetFile = File(snapshotsDir, FILE_NAME)

  if (targetFile.exists() && !targetFile.delete()) {
    device.executeShellCommand("rm -f ${targetFile.absolutePath}")
    if (targetFile.exists()) {
      error("Unable to delete: $targetFile")
    }
  }

  device.executeShellCommand("pm clear ${getApplicationId()}")
}