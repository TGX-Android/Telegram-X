package tgx.test

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import tgx.gradle.fatal
import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.zip.ZipInputStream

private const val SNAPSHOT_REUSE_ERROR =
  "Tests use sessions of the snapshot of -PsnapshotFile, which must not be used by several apps " +
  "at once, otherwise Telegram may terminate them as leaked."

/**
 * Builds keep locks of their phone and of the sessions of their snapshot until they finish.
 * A build waits while another one runs tests on the same phone or with the same snapshot:
 * snapshots of one account in other sessions run at the same time
 */
abstract class SnapshotLockService : BuildService<SnapshotLockService.Parameters>, AutoCloseable {
  interface Parameters : BuildServiceParameters {
    val snapshot: RegularFileProperty
    val locksDirectory: DirectoryProperty
  }

  private val channels = mutableListOf<FileChannel>()
  private var isLocked = false

  @Synchronized
  fun lock(serial: String?) {
    // serial is the phone of connected tests, null for a managed device
    if (isLocked) {
      return
    }
    val directory = parameters.locksDirectory.get().asFile
    directory.mkdirs()
    // The phone first, so that a build waiting for a snapshot keeps its phone, not another one
    if (serial != null) {
      hold(File(directory, "phone-$serial.lock"), "phone $serial")
    }
    parameters.snapshot.orNull?.asFile?.let { snapshot ->
      // A session is never copied, so the path of its snapshot tells it
      val digest =
        MessageDigest.getInstance("SHA-256").digest(snapshot.canonicalPath.toByteArray())
      val name = digest.take(8).joinToString("") { "%02x".format(it) }
      hold(File(directory, "session-$name.lock"), "sessions of ${snapshot.path}")
    }
    isLocked = true
  }

  private fun hold(file: File, what: String) {
    val channel =
      FileChannel.open(file.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
    if (channel.tryLock() == null) {
      Logging.getLogger(SnapshotLockService::class.java)
        .lifecycle("Waiting for another Gradle build that runs tests with $what")
      channel.lock()
    }
    channels += channel
  }

  override fun close() {
    // Closing channels releases their locks
    channels.forEach {
      it.close()
    }
    channels.clear()
  }
}

internal fun releaseBuildLock() {
  // Gives back the lock of builds of a script that runs builds one at a time, see TGX_BUILD_LOCK in
  // tests/README.md, unless another script took it again
  val directory = System.getenv("TGX_BUILD_LOCK")?.let(::File) ?: return
  val pid = System.getenv("TGX_BUILD_LOCK_PID") ?: return
  val owner = File(directory, "pid").takeIf { it.isFile }?.readText()?.trim()
  if (owner == pid) {
    directory.deleteRecursively()
  }
}

// See TdlibAccount.FLAG_UNAUTHORIZED
private const val ACCOUNT_FLAG_UNAUTHORIZED = 1

/**
 * Reads ids of authorized users from tdlib_accounts.bin,
 * see TdlibManager.writeAccountConfigFully
 */
internal fun readSnapshotUserIds(snapshot: InputStream): List<Long> {
  val zip = ZipInputStream(snapshot)
  val entries = generateSequence { zip.nextEntry }
  // Stops at the entry, so its data can be read right after
  val hasAccounts = entries.any {
    it.name == "tdlib_accounts.bin"
  }
  if (!hasAccounts) {
    fatal("tdlib_accounts.bin is missing in the snapshot")
  }
  val input = DataInputStream(zip)
  val accountCount = input.readInt()
  input.readInt() // preferredAccountId
  return (0 until accountCount).mapNotNull {
    val flags = input.readInt()
    val userId = input.readLong()
    input.readLong() // modificationTime
    input.readInt() // order
    userId.takeIf {
      (flags and ACCOUNT_FLAG_UNAUTHORIZED) == 0 && it != 0L
    }
  }
}

internal fun requireSingleDevice(adb: File): String? {
  // Returns the serial of the device that connected tests run on, null when none is connected
  val selectedDevices = System.getenv("ANDROID_SERIAL")
    ?.split(',')
    ?.filter { it.isNotBlank() }
    .orEmpty()
  val devices = selectedDevices.ifEmpty {
    ProcessBuilder(adb.path, "devices").start().inputStream.bufferedReader().useLines { lines ->
      lines
        .filter { it.endsWith("\tdevice") }
        .map { it.substringBefore('\t') }
        .toList()
    }
  }
  if (devices.size > 1) {
    fatal(
      "${devices.size} devices are selected: ${devices.joinToString()}. " +
        "$SNAPSHOT_REUSE_ERROR Select one device with ANDROID_SERIAL."
    )
  }
  return devices.singleOrNull()
}
