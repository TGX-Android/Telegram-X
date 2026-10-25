package tgx.smoke

import android.os.Build
import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.UiDevice
import org.junit.Assume.assumeTrue
import org.junit.Test
import tgx.test.*

class SmokeBenchmark : AppBenchmark() {
  @Test
  fun introSwipes() = measure(
    authorized = false,
    metrics = FRAME_METRICS + MEMORY_METRICS,
    iterations = 5,
    setupBlock = {
      openIntro()
    }
  ) {
    device.swipeIntro()
  }

  @Test
  fun chatsListScrolls() {
    assumeTrue("Requires snapshot.zip", getSnapshotUserIds().isNotEmpty())
    measure(
      authorized = true,
      metrics = SCROLL_METRICS,
      iterations = 5,
      setupBlock = {
        startActivityAndWait()
        // Sessions from snapshot.zip may open longer on the first start after installation
        device.waitForElement("chats_list", timeout = 30_000)
      }
    ) {
      device.scrollList("chats_list")
    }
  }

  @Test
  fun energy() {
    assumeTrue("Requires Android 10+", Build.VERSION.SDK_INT >= 29)
    assumeTrue(
      "Requires device with power rails",
      runShellCommand("dumpsys powerstats").contains("ChannelId")
    )
    measure(
      authorized = false,
      metrics = energyMetrics(),
      iterations = 3,
      setupBlock = {
        openIntro()
      }
    ) {
      device.swipeIntro()
    }
  }
}

private fun MacrobenchmarkScope.openIntro() {
  startActivityAndWait()
  device.waitForElement("btn_startMessaging")
}

private fun UiDevice.swipeIntro() {
  val y = displayHeight / 2
  val left = displayWidth / 5
  val right = displayWidth - left
  repeat(2) {
    swipe(right, y, left, y, 20)
    SystemClock.sleep(500)
  }
  repeat(2) {
    swipe(left, y, right, y, 20)
    SystemClock.sleep(500)
  }
}
