package tgx.test

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.Direction
import org.junit.Rule
import org.junit.Test

private const val CHAT_URL = "https://t.me/tgx_android"

// Match ChatId.fromSupergroupId
private const val ZERO_CHANNEL_ID = -1000000000000L
private const val CHAT_ID = ZERO_CHANNEL_ID - 1136101327

private const val STABLE_ITERATIONS = 5
private const val MAX_ITERATIONS = 20

abstract class BaselineProfileGenerator(authorized: Boolean) : AppTest(authorized) {
  @get:Rule
  val rule = BaselineProfileRule()

  fun collect(
    outputFilePrefix: String,
    includeInStartupProfile: Boolean,
    stableIterations: Int = STABLE_ITERATIONS,
    maxIterations: Int = MAX_ITERATIONS,
    block: MacrobenchmarkScope.() -> Unit
  ) =
    rule.collect(
      outputFilePrefix = outputFilePrefix,
      packageName = getApplicationId(),
      includeInStartupProfile = includeInStartupProfile,
      stableIterations = stableIterations,
      maxIterations = maxIterations
    ) {
      pressHome()
      block()
    }
}

private fun MacrobenchmarkScope.chatUrlIntent() =
  Intent(Intent.ACTION_VIEW, Uri.parse(CHAT_URL)).setPackage(packageName)

@LargeTest
class AuthorizedBaselineProfileGenerator : BaselineProfileGenerator(authorized = true) {
  @Test
  fun launchDefault() = collect("launch_authorized", includeInStartupProfile = true) {
    startActivityAndWait()
    device.waitForNavigation()
    device.waitForStartupMarker()
    device.waitForElement("chats_list")
  }

  @Test
  fun launchViaUrl() = collect("launch_url", includeInStartupProfile = true) {
    startActivityAndWait(chatUrlIntent())
    device.waitForNavigation()
    device.waitForElement("msg_list")
    device.waitForIdle()
    SystemClock.sleep(1_000)
  }

  @Test
  fun launchFromNotification() = collect("launch_notification", includeInStartupProfile = true) {
    val accountId = 0
    val action = "${packageName}.OPEN_CHAT.${accountId}"
    val intent = Intent(action).toMainActivity().apply {
      setPackage(packageName)
      putExtra("account_id", accountId)
      putExtra("chat_id", CHAT_ID)
    }
    startActivityAndWait(intent)
    device.waitForNavigation()
    device.waitForElement("msg_list")
    device.waitForIdle()
    SystemClock.sleep(1_000)
  }

  @Test
  fun openChatUrl() = collect("open_url", includeInStartupProfile = false) {
    startActivityAndWait(chatUrlIntent())
    device.waitForNavigation()
    device.waitForElement("msg_list")
    SystemClock.sleep(1_000)
    pressBack()
    SystemClock.sleep(1_000)
    device.waitForStartupMarker()
  }

  @Test
  fun chatsListScroll() = collect(
    "open_chats",
    includeInStartupProfile = false,
    maxIterations = 15
  ) {
    startActivityAndWait()
    device.waitForNavigation()
    device.waitForStartupMarker()

    val chatsList = device.findElement("chats_list")
    chatsList.setGestureMargin(device.displayWidth / 5)
    chatsList.fling(Direction.DOWN)
    SystemClock.sleep(1_000)
    chatsList.fling(Direction.UP)
    SystemClock.sleep(1_000)
    device.waitForIdle()

    val viewPager = device.findViewPager()
    viewPager.setGestureMargin(device.displayWidth / 5)
    viewPager.fling(Direction.RIGHT)
    SystemClock.sleep(1_000)
    viewPager.fling(Direction.LEFT)
    SystemClock.sleep(1_000)
    device.waitForIdle()
  }
}

@LargeTest
class UnauthorizedBaselineProfileGenerator : BaselineProfileGenerator(authorized = false) {
  @Test
  fun launchDefault() = collect("launch_unauthorized", includeInStartupProfile = true) {
    startActivityAndWait()
    device.waitForNavigation()
  }

  // Iterations of BaselineProfileRule by default
  @Test
  fun loginFlow() = collect(
    "open_login",
    includeInStartupProfile = false,
    stableIterations = 3,
    maxIterations = 15
  ) {
    startActivityAndWait()
    device.waitForNavigation()

    val startMessaging = device.findElement("btn_startMessaging")
    startMessaging.click()

    device.waitForStartupMarker()
  }
}
