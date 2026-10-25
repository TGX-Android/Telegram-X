import tgx.test.TEST_DEVICE
import tgx.test.configureAppTests

plugins {
  id("java-toolchain-convention")
  id(libs.plugins.android.test.get().pluginId)
  id(libs.plugins.androidx.baselineprofile.get().pluginId)
  id("tgx-module")
}

configureAppTests {
  it.usesBaselineProfile
}

baselineProfile {
  managedDevices += TEST_DEVICE
  useConnectedDevices = true
}

dependencies {
  implementation(project(":tests:core"))
  implementation(libs.androidx.benchmark.macro.junit4)
}

android {
  namespace = "tgx.test.baselineprofile"
}
