package tgx.test

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test

// Where updateSnapshot of tests/buildSrc pulls the new snapshot from
private const val PULLED_SNAPSHOT = "/data/local/tmp/tgx-snapshot-update.zip"

// Where SnapshotUpdateReceiver of tests/ui/app makes it, in the media folder of the app
private const val NEW_SNAPSHOT = "tgx-session/new-snapshot.zip"

/**
 * The phone's part of `updateSnapshot`, see "Session snapshot" in tests/README.md: starts the app
 * with the current snapshot, lets `SnapshotUpdateReceiver` make a new one once every account is
 * connected and up to date, and moves it where adb pulls it. Runs only when asked by
 * snapshotUpdate=true, which `-PsnapshotUpdate` sets
 */
class SnapshotUpdate : AuthorizedTest() {
  @Test
  fun makesSnapshot() {
    val isAsked = InstrumentationRegistry.getArguments().getString("snapshotUpdate") == "true"
    assumeTrue("Only when asked by snapshotUpdate=true", isAsked)
    runShellCommand("rm -f $PULLED_SNAPSHOT")
    launchApplication()
    device.waitForNavigation(30_000)
    val since = (runShellCommand("date +%s").trim().toLong() + 1).toString() + ".000"
    Thread.sleep(1_100)
    runShellCommand("am broadcast -n ${getApplicationId()}/tgx.test.SnapshotUpdateReceiver")
    try {
      moveNewSnapshot(since)
    } finally {
      // A copy that wasn't moved, e.g. when the move failed, doesn't stay
      runShellCommand("rm -f /sdcard/Android/media/${getApplicationId()}/$NEW_SNAPSHOT")
    }
  }

  private fun moveNewSnapshot(since: String) {
    var result = ""
    for (attempt in 1..400) {
      result = runShellCommand("logcat -d -T $since -s SnapshotUpdate -v raw").lines()
        .firstOrNull { it.startsWith("ready ") || it.startsWith("failed ") }
        .orEmpty()
      if (result.isNotEmpty()) {
        break
      }
      Thread.sleep(500)
    }
    check(result.startsWith("ready ")) { "No new snapshot: ${result.ifEmpty { "no answer" }}" }
    val made = result.removePrefix("ready ")
    runShellCommand("mv $made $PULLED_SNAPSHOT")
    check(runShellCommand("ls $PULLED_SNAPSHOT").trim() == PULLED_SNAPSHOT) {
      "The new snapshot wasn't moved from $made"
    }
  }
}
