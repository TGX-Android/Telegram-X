package tgx.baselineprofile

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*

const val NAMESPACE_APPLICATION_ID = "org.thunderdog.challegram"

fun getStringArgument(
  key: String
): String =
  InstrumentationRegistry.getArguments().getString(key) ?:
  error("$key not passed")

fun getApplicationId(): String =
  getStringArgument("app.id")

fun idSelector(id: String): BySelector =
  By.res(NAMESPACE_APPLICATION_ID, id)

fun UiDevice.findElementOrNull(
  id: String,
  timeout: Long = 10_000
): UiObject2? =
  try {
    findElement(id, timeout)
  } catch (e: Exception) {
    println(e)
    null
  }

fun UiDevice.failIfPresent(
  id: String,
  timeout: Long = 10_000
) {
  var elementFound: Boolean
  try {
    findElement(id, timeout)
    elementFound = true
  } catch (e: Exception) {
    elementFound = false
  }
  if (elementFound) {
    error("$id is present while it should not")
  }
}

fun UiDevice.findElement(
  id: String,
  timeout: Long = 10_000
): UiObject2 =
  this.wait(Until.findObject(idSelector(id)), timeout)

fun UiDevice.findViewPager(
  timeout: Long = 10_000
): UiObject2 =
  this.wait(Until.findObject(By.clazz("androidx.viewpager.widget.ViewPager")), timeout)

fun UiDevice.waitForElement(
  id: String,
  timeout: Long = 10_000,
  errorMessage: (() -> String)? = null
) {
  if (!this.wait(Until.hasObject(idSelector(id)), timeout)) {
    val message = if (errorMessage != null) {
      errorMessage()
    } else {
      "$id did not appear."
    }
    error(message)
  }
}

fun UiDevice.waitForNavigation(
  timeout: Long = 10_000,
  errorMessage: (() -> String)? = null
) =
  this.waitForElement("nav_root", timeout, errorMessage)

fun UiDevice.waitForStartupMarker(
  timeout: Long = 30_000,
  errorMessage: (() -> String)? = null
) =
  this.waitForElement("startup_marker", timeout, errorMessage)