package tgx.test

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager

/**
 * Microbenchmarks keep it open instead of IsolationActivity of benchmark library,
 * which app process doesn't have: app stays in foreground, screen stays on,
 * and clocks are limited to ones that device sustains without overheating
 */
class BenchmarkActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    window.setSustainedPerformanceMode(true)
  }

  override fun onResume() {
    super.onResume()
    isInForeground = true
  }

  override fun onPause() {
    super.onPause()
    isInForeground = false
  }

  // Test runner finishes activities around each test, this one stays open during all tests
  override fun finish() {}

  companion object {
    @Volatile
    var isInForeground = false
      private set
  }
}
