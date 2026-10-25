import tgx.test.configureAppTests

plugins {
  id("java-toolchain-convention")
  id(libs.plugins.android.test.get().pluginId)
  id("tgx-module")
}

configureAppTests("nonMinifiedRelease", "benchmarkRelease")

dependencies {
  implementation(project(":tests:core"))
}

android {
  namespace = "tgx.test.ui"
}
