package tgx.test

import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.shell.Shell
import androidx.test.uiautomator.*
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask

const val NAMESPACE_APPLICATION_ID = "org.thunderdog.challegram"

fun getStringArgument(key: String): String =
  InstrumentationRegistry.getArguments().getString(key) ?: error("$key not passed")

fun getApplicationId(): String = getStringArgument("app.id")

fun getSnapshotUserIds(): List<Long> =
  InstrumentationRegistry.getArguments().getString("snapshot.users")
    ?.split(',')
    ?.map { it.toLong() }
    .orEmpty()

// Resources of the app belong to its applicationId, which may differ from namespace
fun idSelector(id: String): BySelector = By.res(getApplicationId(), id)

fun UiDevice.findElementOrNull(id: String, timeout: Long = 10_000): UiObject2? =
  this.wait(Until.findObject(idSelector(id)), timeout)

fun UiDevice.failIfPresent(id: String, timeout: Long = 10_000) {
  check(findElementOrNull(id, timeout) == null) {
    "$id is present while it should not"
  }
}

fun UiDevice.findElement(id: String, timeout: Long = 10_000): UiObject2 =
  findElementOrNull(id, timeout) ?: error("$id not found.")

/** Flings the same way every time, so frame metrics of benchmarks are comparable */
fun UiDevice.scrollList(id: String, flings: Int = 3) {
  val list = findElement(id)
  repeat(flings) {
    list.fling(Direction.DOWN)
    waitForIdle()
  }
  repeat(flings) {
    list.fling(Direction.UP)
    waitForIdle()
  }
}

fun UiDevice.findViewPager(timeout: Long = 10_000): UiObject2 =
  this.wait(Until.findObject(By.clazz("androidx.viewpager.widget.ViewPager")), timeout)

fun UiDevice.waitForElement(
  id: String,
  timeout: Long = 10_000,
  errorMessage: (() -> String)? = null
) {
  if (!this.wait(Until.hasObject(idSelector(id)), timeout)) {
    error(errorMessage?.invoke() ?: "$id did not appear, current screen: ${currentScreen()}")
  }
}

fun UiDevice.waitForNavigation(timeout: Long = 10_000, errorMessage: (() -> String)? = null) =
  this.waitForElement("nav_root", timeout, errorMessage)

/** [screen] is the name of id returned by `ViewController.getId()`, e.g. `controller_settings` */
fun UiDevice.waitForScreen(screen: String, timeout: Long = 10_000) {
  if (!this.wait(Until.hasObject(idSelector("screen_marker").desc(screen)), timeout)) {
    error("$screen did not open, current screen: ${currentScreen()}")
  }
}

private fun UiDevice.currentScreen(): String? =
  this.findObject(idSelector("screen_marker"))?.contentDescription

fun UiDevice.waitForStartupMarker(timeout: Long = 30_000, errorMessage: (() -> String)? = null) =
  this.waitForElement("startup_marker", timeout, errorMessage)

fun runShellCommand(command: String): String = Shell.command(command).stdOut

/** Exceptions, including failed assertions, are thrown in the calling thread */
fun <T> onMainThread(block: () -> T): T {
  if (Looper.myLooper() == Looper.getMainLooper()) {
    return block()
  }
  val task = FutureTask(Callable { block() })
  InstrumentationRegistry.getInstrumentation().runOnMainSync(task)
  try {
    return task.get()
  } catch (e: ExecutionException) {
    throw e.cause ?: e
  }
}

fun waitUntil(errorMessage: String, timeout: Long = 5_000, condition: () -> Boolean) {
  val deadline = SystemClock.uptimeMillis() + timeout
  while (!condition()) {
    check(SystemClock.uptimeMillis() < deadline) {
      errorMessage
    }
    SystemClock.sleep(10)
  }
}

fun UiDevice.unlockScreen() {
  this.wakeUp()
  // Works only when screen lock has no PIN, pattern or password
  runShellCommand("wm dismiss-keyguard")
}

fun Intent.toMainActivity(): Intent =
  setClassName(getApplicationId(), "$NAMESPACE_APPLICATION_ID.MainActivity")

private fun startMainActivity(intent: Intent) =
  Shell.application.amStartActivityIntent(intent.toMainActivity())

fun launchApplication() =
  startMainActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))

fun openUrl(url: String) = startMainActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
