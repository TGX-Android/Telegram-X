package tgx.test

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import tgx.gradle.fatal
import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.zip.ZipInputStream

private const val SNAPSHOT_REUSE_ERROR =
  "Tests use accounts from snapshot.zip, which must not be used by several apps at once, " +
  "otherwise Telegram may terminate their sessions as leaked."

/** Builds keep locks of accounts until they finish */
abstract class SnapshotLockService : BuildService<SnapshotLockService.Parameters>, AutoCloseable {
  interface Parameters : BuildServiceParameters {
    val snapshot: RegularFileProperty
    val locksDirectory: DirectoryProperty
  }

  private val channels = mutableListOf<FileChannel>()
  private var isLocked = false

  @Synchronized
  fun lock() {
    if (isLocked) {
      return
    }
    val snapshot = parameters.snapshot.get().asFile
    val userIds = if (snapshot.exists()) {
      snapshot.inputStream().use(::readSnapshotUserIds)
    } else {
      emptyList()
    }
    val directory = parameters.locksDirectory.get().asFile
    directory.mkdirs()
    for (userId in userIds) {
      val channel = FileChannel.open(
        File(directory, "$userId.lock").toPath(),
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE
      )
      if (channel.tryLock() == null) {
        channel.close()
        close()
        fatal("Another Gradle build is running tests with account $userId. $SNAPSHOT_REUSE_ERROR")
      }
      channels += channel
    }
    isLocked = true
  }

  override fun close() {
    // Closing channels releases their locks
    channels.forEach {
      it.close()
    }
    channels.clear()
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
    fatal("tdlib_accounts.bin is missing in snapshot.zip")
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

internal fun requireSingleDevice(adb: File) {
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
}
