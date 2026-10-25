package tgx.test

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import org.junit.Test

class StartupBenchmarks : AppBenchmark() {
  @Test
  fun startupCompilationNone() = benchmark(CompilationMode.None(), false)

  @Test
  fun startupDefault() = benchmark(CompilationMode.DEFAULT, false)

  @Test
  fun startupCompilationBaselineProfiles() =
    benchmark(CompilationMode.Partial(BaselineProfileMode.Require), false)

  @Test
  fun startupAuthorizedCompilationNone() = benchmark(CompilationMode.None(), true)

  @Test
  fun startupAuthorizedDefault() = benchmark(CompilationMode.DEFAULT, true)

  @Test
  fun startupAuthorizedCompilationBaselineProfiles() =
    benchmark(CompilationMode.Partial(BaselineProfileMode.Require), true)

  private fun benchmark(compilationMode: CompilationMode, authorized: Boolean) = measure(
    authorized = authorized,
    metrics = listOf(StartupTimingMetric()),
    compilationMode = compilationMode,
    startupMode = StartupMode.COLD,
    iterations = 15
  ) {
    startActivityAndWait()
    device.waitForNavigation()
  }
}
