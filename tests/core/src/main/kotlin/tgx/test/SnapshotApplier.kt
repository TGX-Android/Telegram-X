package tgx.test

import android.app.Instrumentation
import androidx.test.shell.Shell
import java.io.File
import java.io.IOException

private const val FOLDER_NAME = "tgx-session"
private const val FILE_NAME = "snapshot.zip"

@Suppress("Deprecation")
private fun getTargetDirectory(instrumentation: Instrumentation): File {
  val file = instrumentation.context.externalMediaDirs.first()
  return File(file.parentFile, getApplicationId())
}

private fun getTargetFile(instrumentation: Instrumentation): File =
  File(File(getTargetDirectory(instrumentation), FOLDER_NAME), FILE_NAME)

private fun copySnapshot(instrumentation: Instrumentation, file: File) =
  instrumentation.context.assets.open(FILE_NAME).use { input ->
    file.outputStream().use {
      input.copyTo(it)
    }
  }

private val PERMISSION = Regex("""android\.permission\.[A-Z_]+""")

fun copySnapshotToTargetDevice(instrumentation: Instrumentation) {
  val applicationId = getApplicationId()
  Shell.application.clearAppData(applicationId)

  val permissions = runShellCommand("dumpsys package $applicationId")
    .lineSequence()
    .filter {
      "granted=false" in it
    }
    .mapNotNull { PERMISSION.find(it)?.value }
    .distinct()
  permissions.forEach { permission ->
    runShellCommand("pm grant $applicationId $permission")
  }

  check(FILE_NAME in instrumentation.context.assets.list("").orEmpty()) {
    "assets/$FILE_NAME is missing"
  }
  val targetFile = getTargetFile(instrumentation)
  val snapshotsDir = targetFile.parentFile!!
  try {
    snapshotsDir.mkdirs()
    copySnapshot(instrumentation, targetFile)
  } catch (e: IOException) {
    // Media folder of the app may be unavailable to tests, while shell has access to it
    val temporaryFile = File(instrumentation.context.externalCacheDir, FILE_NAME)
    copySnapshot(instrumentation, temporaryFile)
    runShellCommand("mkdir -p ${snapshotsDir.absolutePath}")
    runShellCommand("rm -f ${targetFile.absolutePath}")
    runShellCommand("cp ${temporaryFile.absolutePath} ${targetFile.absolutePath}")
    check(temporaryFile.delete()) {
      "Unable to delete temporary file: $temporaryFile"
    }
  }
  check(targetFile.exists()) {
    "Target is still unavailable: ${targetFile.path}"
  }
}

fun deleteSnapshotFromTargetDevice(instrumentation: Instrumentation) {
  val targetFile = getTargetFile(instrumentation)
  if (targetFile.exists() && !targetFile.delete()) {
    runShellCommand("rm -f ${targetFile.absolutePath}")
    if (targetFile.exists()) {
      error("Unable to delete: $targetFile")
    }
  }

  Shell.application.clearAppData(getApplicationId())
}

fun prepareApplicationData(instrumentation: Instrumentation, authorized: Boolean) {
  if (authorized) {
    copySnapshotToTargetDevice(instrumentation)
  } else {
    deleteSnapshotFromTargetDevice(instrumentation)
  }
}
