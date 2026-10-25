package tgx.smoke

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import tgx.test.*

// Script runs checks with "before", like before the change, and then with "after"
private val isAfterChange: Boolean
  get() = InstrumentationRegistry.getArguments().getString("tgx.smoke.state") != "before"

class SmokeUiCheck : UnauthorizedTest() {
  @Test
  fun introSwipes() {
    openIntro()
    screenshot("intro")
    recordScreen("swipes", animationScale = 5f) {
      assertWithMessage("Animator duration scale while recording")
        .that(animatorDurationScale())
        .isEqualTo("5.0")
      device.swipeIntro(pauseMs = 2_000)
    }
    assertWithMessage("Animator duration scale after recording")
      .that(animatorDurationScale())
      .isNotEqualTo("5.0")
  }

  @Test
  fun fixedBug() {
    assertWithMessage("Bug is fixed").that(isAfterChange).isTrue()
  }

  @Test
  fun newScreen() {
    openIntro()
    if (isAfterChange) {
      screenshot("new")
    }
  }

  @Test
  @DisableAnimations
  fun disablesAnimations() {
    assertWithMessage("Animator duration scale").that(animatorDurationScale()).isEqualTo("0.0")
  }

  private fun openIntro() {
    launchApplication()
    device.waitForNavigation()
    device.waitForElement("btn_startMessaging")
  }
}

private fun animatorDurationScale(): String =
  runShellCommand("settings get global animator_duration_scale").trim()

private fun UiDevice.swipeIntro(pauseMs: Long) {
  val y = displayHeight / 2
  val left = displayWidth / 5
  val right = displayWidth - left
  repeat(2) {
    swipe(right, y, left, y, 20)
    SystemClock.sleep(pauseMs)
  }
  repeat(2) {
    swipe(left, y, right, y, 20)
    SystemClock.sleep(pauseMs)
  }
}
