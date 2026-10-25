import tgx.test.configureAppTests
import tgx.test.configureBenchmarkComparison

plugins {
  id("java-toolchain-convention")
  id(libs.plugins.android.test.get().pluginId)
  id("tgx-module")
}

configureAppTests("benchmarkRelease")
configureBenchmarkComparison()

dependencies {
  implementation(project(":tests:core"))
  implementation(libs.androidx.benchmark.macro.junit4)
}

android {
  namespace = "tgx.test.benchmark"
}
