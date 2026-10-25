@file:OptIn(ExperimentalMetricApi::class)

package tgx.test

import androidx.annotation.RequiresApi
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.MemoryCountersMetric
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.Metric
import androidx.benchmark.macro.PowerCategory
import androidx.benchmark.macro.PowerCategoryDisplayLevel
import androidx.benchmark.macro.PowerMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
abstract class AppBenchmark {
  companion object {
    val FRAME_METRICS: List<Metric> = listOf(
      FrameTimingMetric(),
      TraceSectionMetric("layout", TraceSectionMetric.Mode.Count),
      TraceSectionMetric("% GC", TraceSectionMetric.Mode.Count, label = "gc")
    )

    /**
     * Lists must not be laid out while scrolling:
     * it means that items request layout or code changes scroll position
     */
    val SCROLL_METRICS: List<Metric> = FRAME_METRICS + listOf(
      // Versions of RecyclerView name sections differently,
      // e.g. "RV OnBindView" or "RV onBindViewHolder type=0x0"
      TraceSectionMetric("RV %BindView%", TraceSectionMetric.Mode.Sum, label = "bind"),
      TraceSectionMetric("RV %CreateView%", TraceSectionMetric.Mode.Count, label = "createView"),
      TraceSectionMetric("RV OnLayout", TraceSectionMetric.Mode.Count, label = "listLayout"),
      TraceSectionMetric(
        "RV FullInvalidate",
        TraceSectionMetric.Mode.Count,
        label = "listFullUpdate"
      ),
      TraceSectionMetric(
        "RV PartialInvalidate",
        TraceSectionMetric.Mode.Count,
        label = "listUpdate"
      )
    )

    val MEMORY_METRICS: List<Metric> = listOf(
      MemoryUsageMetric(MemoryUsageMetric.Mode.Last),
      MemoryCountersMetric()
    )

    /** Requires device with power rails, e.g. Pixel 6 and newer, measures only listed categories */
    @RequiresApi(29)
    fun energyMetrics(): List<Metric> =
      listOf(
        PowerMetric(
          PowerMetric.Energy(
            PowerCategory.entries.associateWith { PowerCategoryDisplayLevel.TOTAL }
          )
        )
      )
  }

  @get:Rule
  val rule = MacrobenchmarkRule()

  @get:Rule
  val report = ReportRule(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()))

  fun measure(
    authorized: Boolean,
    metrics: List<Metric>,
    compilationMode: CompilationMode = CompilationMode.DEFAULT,
    startupMode: StartupMode? = null,
    iterations: Int = 10,
    setupBlock: MacrobenchmarkScope.() -> Unit = {},
    measureBlock: MacrobenchmarkScope.() -> Unit
  ) =
    rule.measureRepeated(
      packageName = getApplicationId(),
      metrics = metrics,
      compilationMode = compilationMode,
      startupMode = startupMode,
      iterations = iterations,
      setupBlock = {
        device.unlockScreen()
        prepareApplicationData(instrumentation, authorized)
        pressHome()
        setupBlock()
      },
      measureBlock = measureBlock
    )
}
