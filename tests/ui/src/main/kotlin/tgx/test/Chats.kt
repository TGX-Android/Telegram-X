package tgx.test

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Resources
import android.graphics.Rect
import android.os.SystemClock
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

// Built-in themes by their ids in the app, see ThemeId
enum class AppTheme(val id: Int) {
  BLUE(1),
  NIGHT_BLACK(2),
  NIGHT_BLUE(10),
  CLASSIC(11)
}

fun UiDevice.openDrawer() {
  val list = findElement("chats_list").visibleBounds
  swipe(
    list.left + list.width() / 4,
    list.centerY(),
    list.right - list.width() / 8,
    list.centerY(),
    20
  )
}

fun UiDevice.openSavedMessages() {
  // The only chat where tests send messages
  openDrawer()
  findElement("btn_savedMessages").click()
  waitForElement("msg_input")
  waitForIdle()
}

fun UiDevice.sendMessage(text: String) {
  // Markdown of the input applies, e.g. ``` around code
  findElement("msg_input").text = text
  findElement("msg_send").click()
  waitUntil("Message is not sent", 10_000) {
    findElement("msg_input").text.isNullOrEmpty()
  }
  waitForIdle()
}

fun UiDevice.lastMessages(count: Int): List<Rect> {
  // Bounds of the last messages of the open chat, from the top
  return findElement("msg_list").children
    .map(UiObject2::getVisibleBounds)
    .sortedBy { it.bottom }
    .takeLast(count)
}

fun UiDevice.visibleTopOfChat(): Int {
  // Bars over the list of the open chat, e.g. of a pinned message, cover the top of its list
  val listTop = findElement("msg_list").visibleBounds.top
  val bars = findElementOrNull("msg_topBar", 1_000)?.children.orEmpty()
  return (bars.map { it.visibleBounds.bottom } + listTop).max()
}

fun UiDevice.openMessageMenu(message: Rect) {
  // A tap at the time of an outgoing message, because its content, e.g. a code block, can handle
  // taps itself. Long press on a code block opens the menu of the block
  val density = Resources.getSystem().displayMetrics.density
  click(message.right - (40 * density).toInt(), message.bottom - (14 * density).toInt())
  waitForIdle()
}

fun UiDevice.setAppearance(theme: AppTheme? = null, bubbles: Boolean? = null) {
  // Without screens of settings, by AppearanceReceiver of builds with test hooks; null keeps the
  // current value
  val extras = listOfNotNull(
    theme?.let { "--ei theme ${it.id}" },
    bubbles?.let { "--ez bubbles $it" }
  )
  val receiver = "${getApplicationId()}/tgx.test.AppearanceReceiver"
  runShellCommand("am broadcast -n $receiver ${extras.joinToString(" ")}")
  waitForIdle()
  // Colors of a new theme are animated
  Thread.sleep(500)
}

fun AppTest.textCopiedBy(copy: () -> Unit): String? {
  // Text that copy puts into the clipboard, or null when nothing comes in 3 seconds
  val clipboard = onMainThread {
    instrumentation.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
  }
  // Apps in background can't read the clipboard, shell can
  val uiAutomation = instrumentation.uiAutomation
  uiAutomation.adoptShellPermissionIdentity("android.permission.READ_CLIPBOARD_IN_BACKGROUND")
  try {
    onMainThread {
      clipboard.setPrimaryClip(ClipData.newPlainText(null, ""))
    }
    copy()
    var copied: String? = null
    val deadline = SystemClock.uptimeMillis() + 3_000
    while (copied.isNullOrEmpty() && SystemClock.uptimeMillis() < deadline) {
      Thread.sleep(100)
      copied = onMainThread { clipboard.primaryClip?.getItemAt(0)?.text?.toString() }
    }
    // The system shows what was copied over the app for a few seconds
    device.wait(Until.gone(By.res("com.android.systemui", "clipboard_ui")), 10_000)
    return copied
  } finally {
    uiAutomation.dropShellPermissionIdentity()
  }
}
