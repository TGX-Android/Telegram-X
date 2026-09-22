@file:JvmName("SessionSnapshot")

package org.thunderdog.challegram.telegram

import org.thunderdog.challegram.Log
import org.thunderdog.challegram.tool.UI
import org.thunderdog.challegram.unsorted.Settings
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private data class SnapshotPath(
  val path: String,
  val isOptional: Boolean = false
)

fun getSnapshotsDir(): File =
  File(UI.getAppContext().filesDir, "snapshots")

@Throws(IOException::class)
fun createSnapshotWithAllAccounts(): File {
  val outputDir = getSnapshotsDir()
  if (!outputDir.exists()) {
    if (!outputDir.mkdir())
      throw IOException("Unable to create output directory: $outputDir")
  } else if (!outputDir.isDirectory) {
    throw IOException("Exists, but not a directory: $outputDir")
  }

  val outputFile = File(outputDir, "snapshot.zip")
  if (outputFile.exists() && !outputFile.delete()) {
    throw IOException("Exists, but unable to delete: $outputFile")
  }

  val inputFiles = mutableListOf<File>()

  val otherFiles = arrayOf(
    TdlibManager.getAccountConfigFile(),
    TdlibManager.getLanguageDatabaseDir(),
    Settings.instance().directory
  )

  for (otherFile in otherFiles) {
    if (!otherFile.exists()) {
      throw IOException("Other file not found: $otherFile")
    }
    inputFiles += otherFile
  }

  val tdlibFiles = arrayOf(
    SnapshotPath("td.binlog"),
    SnapshotPath("db.sqlite"),
    SnapshotPath("db.sqlite-wal", isOptional = true),
    SnapshotPath("db.sqlite-shm", isOptional = true)
  )
  val accountsNum = TdlibManager.instance().activeAccountsNum
  for (accountId in 0 until accountsNum) {
    val tdlibPath = TdlibManager.getTdlibDirectory(accountId, false);
    val tdlibDir = File(tdlibPath)
    if (!tdlibDir.exists() || !tdlibDir.isDirectory)
      throw IOException("TDLib folder doesn't exist or is not a directory: $tdlibPath")
    for (tdlibPath in tdlibFiles) {
      val tdlibFile = File(tdlibDir, tdlibPath.path)
      if (tdlibFile.exists()) {
        inputFiles += tdlibFile
      } else if (!tdlibPath.isOptional) {
        throw IOException("Required file does not exist: ${tdlibFile.path}")
      }
    }
  }

  val baseDir = UI.getAppContext().filesDir
  ZipOutputStream(BufferedOutputStream(outputFile.outputStream())).use { zos ->
    for (source in inputFiles) {
      if (source.exists()) {
        source.walkTopDown().forEach { child ->
          val name = child.relativeTo(baseDir).invariantSeparatorsPath
          Log.i("Add snapshot entry: %s", name)
          if (child.isDirectory) {
            zos.putNextEntry(ZipEntry("$name/"))
            zos.closeEntry()
          } else {
            zos.putNextEntry(ZipEntry(name))
            child.inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
          }
        }
      } else {
        throw IOException("File disappeared: ${source.path}")
      }
    }
  }

  return outputFile
}

@Suppress("Deprecation")
private fun getPublicDirectory(): File =
  UI.getAppContext().externalMediaDirs.first()

fun restoreSnapshot(): Boolean {
  val publicDir = getPublicDirectory()
  val snapshotsDir = File(publicDir, "tgx-session")
  val targetFile = File(snapshotsDir, "snapshot.zip")
  if (!targetFile.exists()) {
    return false
  }

  val targetDirectory = UI.getAppContext().filesDir

  ZipInputStream(BufferedInputStream(targetFile.inputStream())).use { zis ->
    var entry = zis.nextEntry
    while (entry != null) {
      val out = File(targetDirectory, entry.name)
      require(out.canonicalPath.startsWith(targetDirectory.canonicalPath + File.separator)) {
        "Invalid entry: ${entry.name}"
      }
      if (entry.isDirectory) {
        out.mkdirs()
      } else {
        out.parentFile?.mkdirs()
        out.outputStream().use { zis.copyTo(it) }
      }
      zis.closeEntry()
      entry = zis.nextEntry
    }
  }

  return true
}