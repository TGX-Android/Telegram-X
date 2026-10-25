import tgx.test.configureAppTests
import tgx.test.configureBenchmarkComparison

plugins {
  id("java-toolchain-convention")
  id(libs.plugins.android.test.get().pluginId)
  id("tgx-module")
}

// R8 would rename and remove app classes that benchmarks call
configureAppTests("nonMinifiedRelease", inAppProcess = true)
configureBenchmarkComparison()

dependencies {
  implementation(project(":tests:core"))
  implementation(libs.androidx.benchmark.junit4)
  // App classes use classes of its dependencies, e.g. TdApi.FormattedText
  compileOnly(project(":tdlib"))
  compileOnly(project(":vkryl:core"))
  compileOnly(project(":vkryl:android"))
}

android {
  namespace = "tgx.test.microbenchmark"
  defaultConfig {
    testInstrumentationRunner = "tgx.test.MicroBenchmarkRunner"
    // Benchmark library checks for its IsolationActivity, which app process doesn't have,
    // MicroBenchmarkRunner opens BenchmarkActivity instead
    testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] =
      "ACTIVITY-MISSING,UNSUSTAINED-ACTIVITY-MISSING"
    // Benchmark library waits 90 seconds while device is throttled,
    // which fails tests on the main thread by their time limit,
    // compareBenchmarkResults shows thermal status of runs instead
    testInstrumentationRunnerArguments["androidx.benchmark.thermalThrottle.sleepDurationSeconds"] =
      "0"
    // Method tracing after a test changes speed of later tests on some versions of Android,
    // see https://issuetracker.google.com/issues/316174880, so traces are recorded by separate runs
    testInstrumentationRunnerArguments["androidx.benchmark.profiling.mode"] = "None"
  }
}
