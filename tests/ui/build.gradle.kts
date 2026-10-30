import tgx.test.configureAppTests
import tgx.test.configureSnapshotUpdate

plugins {
  id("java-toolchain-convention")
  id(libs.plugins.android.test.get().pluginId)
  id("tgx-module")
}

configureAppTests("nonMinifiedRelease", "benchmarkRelease")
configureSnapshotUpdate()

dependencies {
  implementation(project(":tests:core"))
}

android {
  namespace = "tgx.test.ui"
}
