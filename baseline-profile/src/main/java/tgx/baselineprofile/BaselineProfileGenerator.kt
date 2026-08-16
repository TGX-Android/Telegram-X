package tgx.baselineprofile

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val CHAT_URL = "https://t.me/tgx_android"

// Match ChatId.fromSupergroupId
private const val ZERO_CHANNEL_ID = -1000000000000L
private const val CHAT_ID = ZERO_CHANNEL_ID - 1136101327

private const val STABLE_ITERATIONS = 5
private const val MAX_ITERATIONS = 20

abstract class BaselineProfileGenerator {
  @get:Rule
  val rule = BaselineProfileRule()

  val instrumentation =
    InstrumentationRegistry.getInstrumentation()!!
  val device =
    UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
}

@RunWith(AndroidJUnit4::class)
@LargeTest
class AuthorizedBaselineProfileGenerator : BaselineProfileGenerator() {
  @Before
  fun authorize() =
    copySnapshotToTargetDevice(device, instrumentation)

  @After
  fun deauthorize() =
    deleteSnapshotFromTargetDevice(device, instrumentation)

  @Test
  fun launchDefault() = rule.collect(
    outputFilePrefix = "launch_authorized",
    packageName = getApplicationId(),
    includeInStartupProfile = true,
    stableIterations = STABLE_ITERATIONS,
    maxIterations = MAX_ITERATIONS
  ) {
    pressHome()
    startActivityAndWait()
    device.waitForNavigation()
    device.waitForStartupMarker()
    device.waitForElement("chats_list")
  }

  @Test
  fun launchViaUrl() = rule.collect(
    outputFilePrefix = "launch_url",
    packageName = getApplicationId(),
    includeInStartupProfile = true,
    stableIterations = STABLE_ITERATIONS,
    maxIterations = MAX_ITERATIONS
  ) {
    pressHome()
    startActivityAndWait(
      Intent(Intent.ACTION_VIEW, Uri.parse(CHAT_URL)).apply {
        setPackage(packageName)
      }
    )
    device.waitForNavigation()
    device.waitForElement("msg_list")
    device.waitForIdle()
    SystemClock.sleep(1_000)
  }

  @Test
  fun launchFromNotification() = rule.collect(
    outputFilePrefix = "launch_notification",
    packageName = getApplicationId(),
    includeInStartupProfile = true,
    stableIterations = STABLE_ITERATIONS,
    maxIterations = MAX_ITERATIONS
  ) {
    pressHome()
    val accountId = 0
    val action = "${packageName}.OPEN_CHAT.${accountId}"
    val intent = Intent(action).apply {
      setClassName(packageName, "${packageName}.MainActivity")
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

  // Baseline

  @Test
  fun openChatUrl() = rule.collect(
    outputFilePrefix = "open_url",
    packageName = getApplicationId(),
    includeInStartupProfile = false,
    stableIterations = STABLE_ITERATIONS,
    maxIterations = MAX_ITERATIONS
  ) {
    pressHome()
    startActivityAndWait(
      Intent(Intent.ACTION_VIEW, Uri.parse(CHAT_URL)).apply {
        setPackage(packageName)
      }
    )
    device.waitForNavigation()
    device.waitForElement("msg_list")
    SystemClock.sleep(1_000)
    pressBack()
    SystemClock.sleep(1_000)
    device.waitForStartupMarker()
  }

  @Test
  fun chatsListScroll() = rule.collect(
    outputFilePrefix = "open_chats",
    packageName = getApplicationId(),
    includeInStartupProfile = false,
    stableIterations = 5,
    maxIterations = 15
  ) {
    pressHome()
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

@RunWith(AndroidJUnit4::class)
@LargeTest
class UnauthorizedBaselineProfileGenerator : BaselineProfileGenerator() {

  @Before
  fun deauthorize() =
    deleteSnapshotFromTargetDevice(device, instrumentation)

  @Test
  fun launchDefault() = rule.collect(
    outputFilePrefix = "launch_unauthorized",
    packageName = getApplicationId(),
    includeInStartupProfile = true,
    stableIterations = STABLE_ITERATIONS,
    maxIterations = MAX_ITERATIONS
  ) {
    pressHome()
    startActivityAndWait()
    device.waitForNavigation()
  }

  @Test
  fun loginFlow() = rule.collect(
    outputFilePrefix = "open_login",
    packageName = getApplicationId(),
    includeInStartupProfile = false
  ) {
    pressHome()
    startActivityAndWait()
    device.waitForNavigation()

    val startMessaging = device.findElement("btn_startMessaging")
    startMessaging.click()

    device.waitForStartupMarker()
  }
}
