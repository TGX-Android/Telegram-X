package tgx.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupBenchmarks {

  @get:Rule
  val rule = MacrobenchmarkRule()

  @Test
  fun startupCompilationNone() =
    benchmark(CompilationMode.None(), false)

  @Test
  fun startupDefault() =
    benchmark(CompilationMode.DEFAULT, false)

  @Test
  fun startupCompilationBaselineProfiles() =
    benchmark(CompilationMode.Partial(BaselineProfileMode.Require), false)

  @Test
  fun startupAuthorizedCompilationNone() =
    benchmark(CompilationMode.None(), true)

  @Test
  fun startupAuthorizedDefault() =
    benchmark(CompilationMode.DEFAULT, true)

  @Test
  fun startupAuthorizedCompilationBaselineProfiles() =
    benchmark(CompilationMode.Partial(BaselineProfileMode.Require), true)

  private fun benchmark(compilationMode: CompilationMode, authorized: Boolean) {
    rule.measureRepeated(
      packageName = getApplicationId(),
      metrics = listOf(StartupTimingMetric()),
      compilationMode = compilationMode,
      startupMode = StartupMode.COLD,
      iterations = 15,
      setupBlock = {
        if (authorized) {
          copySnapshotToTargetDevice(device, instrumentation)
        } else {
          deleteSnapshotFromTargetDevice(device, instrumentation)
        }
        pressHome()
      },
      measureBlock = {
        startActivityAndWait()
        device.waitForNavigation()
      }
    )
  }
}