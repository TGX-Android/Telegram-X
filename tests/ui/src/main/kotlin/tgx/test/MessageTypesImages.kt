package tgx.test

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Images of all types of messages, made up by MessageTypesReceiver of builds with test hooks: for
 * each sample in each of its directions the chat in the list with it as the last message, the
 * message in the chat and its notification when it has one, one image each, from the top; for each
 * notification by Firebase the notification before the message loads and after. In Blue and Night
 * Black, or in all themes with messageTypes.themes=all. Fails when the app crashes or logs an
 * exception. A tool rather than a test, so it runs only when asked:
 * `./gradlew :tests:ui:connectedLatestNonMinifiedReleaseAndroidTest -Pabi=arm64
 * -Pandroid.testInstrumentationRunnerArguments.class=tgx.test.MessageTypesImages
 * -Pandroid.testInstrumentationRunnerArguments.messageTypes=true`, then `saveMessageTypes` keeps
 * the images as the base and `compareMessageTypes` lists the types whose images changed since it,
 * see "Images of types of messages" in tests/README.md
 */
class MessageTypesImages : AuthorizedTest() {
  private val receiver get() = "${getApplicationId()}/tgx.test.MessageTypesReceiver"
  private val failed = mutableListOf<String>()

  @Test
  fun images() {
    val arguments = InstrumentationRegistry.getArguments()
    val isAsked = arguments.getString("messageTypes") == "true"
    assumeTrue("Images only when asked by messageTypes=true", isAsked)
    launchApplication()
    device.waitForNavigation()
    device.waitForElement("chats_list")
    Thread.sleep(2_000)
    val lines = broadcast("--es action types", 120_000) { it.startsWith("types ") }
    val themes = if (arguments.getString("messageTypes.themes") == "all") {
      AppTheme.entries
    } else {
      listOf(AppTheme.BLUE, AppTheme.NIGHT_BLACK)
    }
    // messageTypes.filter=MessageText+PushMessageContentText makes images only of the types whose
    // names contain one of these, e.g. the ones that a change touches
    val filter = arguments.getString("messageTypes.filter")?.split("+")
    val isShown = { name: String -> filter == null || filter.any { it in name } }
    for (theme in themes) {
      device.setAppearance(theme)
      val prefix = theme.name.lowercase()
      for (line in lines.filter { it.startsWith("sample ") }) {
        val (_, index, directions, kinds, name) = line.split(" ", limit = 5)
        if (!isShown(name)) {
          continue
        }
        for (direction in directions.split(",")) {
          val parts = sampleImages(index, direction == "out", kinds.split(",").first())
          if (parts.isNotEmpty()) {
            val number = "%03d".format(index.toInt())
            image("$prefix-$number-${fileNameOf(name)}-$direction", stacked(parts))
          }
        }
      }
      // Notifications by Firebase before the message loads, and after it replaces them
      for (line in lines.filter { it.startsWith("push ") }) {
        val (_, index, _, name) = line.split(" ", limit = 4)
        if (!isShown(name)) {
          continue
        }
        val parts = whileChecked(name) {
          val pushed = broadcast("--es action push --ei index $index", 20_000) {
            it.startsWith("pushed $index ")
          }
          val paths = pushed.last { it.startsWith("pushed ") }.split(" ").drop(2)
          paths.filter { it != "none" }.mapNotNull { readImage(it) }
        }
        if (parts.isNotEmpty()) {
          image("%s-push-%02d-%s".format(prefix, index.toInt(), fileNameOf(name)), stacked(parts))
        }
      }
    }
    assertTrue("Crashed or logged exceptions: ${failed.joinToString("; ")}", failed.isEmpty())
  }

  private fun sampleImages(index: String, outgoing: Boolean, kind: String): List<Bitmap> =
    whileChecked("$index ${if (outgoing) "out" else "in"}") {
      val added = broadcast(
        "--es action type --ei index $index --ez outgoing $outgoing --es kind $kind", 10_000
      ) { it.startsWith("type ") }
      val chatId = added.last { it.startsWith("type ") }.split(" ")[2]
      Thread.sleep(1_500)
      val list = instrumentation.uiAutomation.takeScreenshot()
      val row = topRow()
      val chats = "${getApplicationId()}/tgx.test.FakeChatReceiver"
      runShellCommand("am broadcast -n $chats --es action open --el chat $chatId")
      device.waitForElement("msg_list")
      // A chat opens where it was scrolled to, above the last message when it's long
      device.findElementOrNull("btn_scroll", 1_000)?.click()
      // Stickers and animated emoji play once and stop, longer than 2 s for some
      Thread.sleep(4_000)
      val chat = instrumentation.uiAutomation.takeScreenshot()
      val message = device.lastMessages(1).firstOrNull()
      device.pressBack()
      device.waitForElement("chats_list")
      Thread.sleep(500)
      val notified = broadcast("--es action notify", 15_000) { it.startsWith("notified ") }
      val notification = notified.last { it.startsWith("notified ") }.removePrefix("notified ")
        .takeIf { it != "none" }
      listOfNotNull(
        row?.let { cut(list, it) }, message?.let { cut(chat, it) },
        notification?.let { readImage(it) }
      )
    }

  private fun whileChecked(name: String, block: () -> List<Bitmap>): List<Bitmap> {
    // A crash of the app or an exception that it logs while block runs fails the sample, and the
    // app starts again with the samples for the next one, so that one run finds all such types
    val since = logcatTime()
    val pid = runShellCommand("pidof ${getApplicationId()}").trim()
    val result = try {
      block()
    } catch (e: Exception) {
      failed += "$name: ${e.message}"
      listOf()
    }
    val exceptions = runShellCommand("logcat -d -T $since --pid=$pid -v brief *:W").lines()
      .filter { "Exception" in it }
    if (exceptions.isNotEmpty()) {
      failed += "$name: ${exceptions.first()}"
    }
    // The system may start the process again at once for a service of the app, so a crash is a new
    // process; the app opens then on its screen of the crash, left by "Launch App"
    if (runShellCommand("pidof ${getApplicationId()}").trim() != pid) {
      failed += "$name: the app crashed"
      launchApplication()
      device.waitForNavigation()
      device.findElementOrNull("btn_launchApp", 5_000)?.click()
      device.waitForElement("chats_list", 30_000)
      Thread.sleep(2_000)
      broadcast("--es action types", 120_000) { it.startsWith("types ") }
    }
    return result
  }

  private fun logcatTime(): String {
    val since = (runShellCommand("date +%s").trim().toLong() + 1).toString() + ".000"
    Thread.sleep(1_100)
    return since
  }

  private fun broadcast(extras: String, timeout: Long, until: (String) -> Boolean): List<String> {
    // Lines of the log of MessageTypes since the broadcast, up to the one that ends the action
    val since = logcatTime()
    runShellCommand("am broadcast -n $receiver $extras")
    var lines = listOf<String>()
    waitUntil("No answer to $extras", timeout) {
      lines = runShellCommand("logcat -d -T $since -s MessageTypes -v raw").lines()
      lines.any(until)
    }
    return lines
  }

  private fun fileNameOf(name: String): String = name.replace(Regex("\\W+"), "-")

  private fun readImage(path: String): Bitmap? =
    // The shell reads external files of the app, the test can't
    ParcelFileDescriptor.AutoCloseInputStream(
      instrumentation.uiAutomation.executeShellCommand("cat $path")
    ).use { BitmapFactory.decodeStream(it) }

  private fun topRow(): Rect? {
    // The first chat of the list. Archived chats, when they are shown in the list, come above it
    // with a gap after them, while rows of chats follow each other without one
    val rows = device.findElement("chats_list").children
      .filter { it.resourceName?.endsWith(":id/chat") == true }
      .map { it.visibleBounds }
      .sortedBy { it.top }
    val isArchive = rows.size > 1 && rows[1].top - rows[0].bottom > 2
    return rows.getOrNull(if (isArchive) 1 else 0)
  }

  private fun cut(screen: Bitmap, bounds: Rect): Bitmap =
    Bitmap.createBitmap(screen, bounds.left, bounds.top, bounds.width(), bounds.height())

  private fun stacked(parts: List<Bitmap>): Bitmap {
    val result = Bitmap.createBitmap(
      parts.maxOf { it.width }, parts.sumOf { it.height }, Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(result)
    var y = 0f
    for (part in parts) {
      // A narrower part, e.g. a notification, in the middle
      canvas.drawBitmap(part, (result.width - part.width) / 2f, y, null)
      y += part.height
    }
    return result
  }
}
