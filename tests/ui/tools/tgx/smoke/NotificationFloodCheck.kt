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
    runShellCommand(
      "am broadcast -n ${getApplicationId()}/tgx.test.NotificationFloodReceiver --ei count 5 --ei perMinute 600 --ei chats 1"
    )
    val deadline = System.currentTimeMillis() + 60_000
    while ("done" !in runShellCommand("logcat -d -s NotificationFlood")) {
      check(System.currentTimeMillis() < deadline) { "Flood didn't end" }
      Thread.sleep(1_000)
    }
    // A notification of the chat with its messages, and a summary of the account
    val messageCounts = runShellCommand("dumpsys notification --noredact")
      .lines()
      .mapNotNull { Regex("""android\.messages=Bundle\[\] \((\d+)\)""").find(it)?.groupValues?.get(1)?.toInt() }
    assertWithMessage("Messages of notifications of the app").that(messageCounts).contains(5)
  }
}
