package tgx.smoke

import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import tgx.test.*

// The app shows fake notifications of NotificationFlood like the ones of new messages
class NotificationFloodCheck : AuthorizedTest() {
  @Test
  fun showsNotificationsOfMessages() {
    launchApplication()
    // Experimental builds show an alert at every launch
    if (device.wait(Until.hasObject(By.text("Caution: experimental build")), 10_000)) {
      device.findObject(By.text("OK")).click()
    }
    device.waitForNavigation()
    device.waitForElement("chats_list")
    device.pressHome()
    runShellCommand("logcat -c")
    // Messages 2 seconds apart, since the app shows forwarded messages of the same date as one
    val appId = getApplicationId()
    runShellCommand(
      "am broadcast -n $appId/tgx.test.NotificationFloodReceiver " +
        "--ei count 5 --ei perMinute 30 --ei chats 1"
    )
    val deadline = System.currentTimeMillis() + 60_000
    while ("done" !in runShellCommand("logcat -d -s NotificationFlood")) {
      check(System.currentTimeMillis() < deadline) { "Flood didn't end" }
      Thread.sleep(1_000)
    }
    // A notification of the chat with its messages, and a summary of the account. The dump is of
    // the app only and redacted, without notifications of other apps and texts
    check(appId.isNotEmpty())
    val messageCounts = runShellCommand("dumpsys notification p $appId")
      .split("NotificationRecord(")
      .filter { " pkg=$appId " in it.substringBefore('\n') }
      .mapNotNull { Regex("""android\.messages=Bundle\[\] \((\d+)\)""").find(it)?.groupValues?.get(1)?.toInt() }
    assertWithMessage("Messages of notifications of the app").that(messageCounts).contains(5)
  }
}
