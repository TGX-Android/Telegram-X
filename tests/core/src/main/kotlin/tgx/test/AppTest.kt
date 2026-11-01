package tgx.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith

/**
 * Before each test prepares app data, after each test clears it.
 * Tests launch the app themselves, e.g. with [launchApplication] or [openUrl].
 */
@RunWith(AndroidJUnit4::class)
abstract class AppTest(private val authorized: Boolean) {
  val instrumentation = InstrumentationRegistry.getInstrumentation()!!
  val device = UiDevice.getInstance(instrumentation)

  // Outer rules wrap inner ones, so app data is cleared only after failure is reported
  @get:Rule(order = 0)
  val applicationData = object : ExternalResource() {
    override fun before() {
      device.unlockScreen()
      prepareApplicationData(instrumentation, authorized)
    }

    override fun after() = deleteSnapshotFromTargetDevice(instrumentation)
  }

  @get:Rule(order = 1)
  val animations = AnimationsRule()

  @get:Rule(order = 2)
  val report = ReportRule(device)

  fun screenshot(name: String) = report.screenshot(name)

  fun recordNativeHeap(name: String, block: () -> Unit) =
    // Native allocations of the app while block runs, see "Profiles of native memory" in
    // tests/README.md
    report.recordNativeHeap(name, block)

  /** [animationScale] other than 1 slows animations down, e.g. 5 to see every frame of a glitch */
  fun recordScreen(name: String, animationScale: Float = 1f, block: () -> Unit) =
    report.recordScreen(name) {
      if (animationScale == 1f) {
        block()
      } else {
        withAnimationScale(animationScale, block)
      }
    }
}

/** Starts with the session from assets/snapshot.zip */
abstract class AuthorizedTest : AppTest(authorized = true)

abstract class UnauthorizedTest : AppTest(authorized = false)
