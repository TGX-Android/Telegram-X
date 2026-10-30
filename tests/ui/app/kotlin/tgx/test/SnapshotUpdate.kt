package tgx.test

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.thunderdog.challegram.telegram.TdlibManager
import org.thunderdog.challegram.telegram.createSnapshotWithAllAccounts
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val TAG = "SnapshotUpdate"

/**
 * Makes a new snapshot of all accounts, as the attach menu does, but keeps it on the phone for
 * `SnapshotUpdate` of tests/ui instead of sending it:
 * `adb shell am broadcast -n org.example.tgx/tgx.test.SnapshotUpdateReceiver` waits until every
 * account is connected and up to date, makes the snapshot and moves it into the media folder of
 * the app, which the shell can read, then logs with tag `SnapshotUpdate` "ready <path>" or
 * "failed <reason>"
 */
class SnapshotUpdateReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    Thread {
      try {
        val accounts = TdlibManager.instance().filter { !it.isUnauthorized }
        val connected = CountDownLatch(accounts.size)
        accounts.forEach { it.tdlib().awaitConnection(connected::countDown) }
        check(connected.await(3, TimeUnit.MINUTES)) {
          "${connected.count} of ${accounts.size} accounts aren't connected after 3 minutes"
        }
        val snapshot = createSnapshotWithAllAccounts()
        @Suppress("Deprecation")
        val target = File(context.externalMediaDirs.first(), "tgx-session/new-snapshot.zip")
        try {
          target.parentFile!!.mkdirs()
          snapshot.copyTo(target, overwrite = true)
        } finally {
          snapshot.delete()
        }
        Log.i(TAG, "ready ${target.path}")
      } catch (e: Exception) {
        Log.e(TAG, "failed $e")
      }
    }.start()
  }
}
