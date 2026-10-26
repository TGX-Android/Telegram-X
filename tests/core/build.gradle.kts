plugins {
  id("java-toolchain-convention")
  id(libs.plugins.android.library.get().pluginId)
  id("tgx-module")
}

dependencies {
  api(libs.androidx.test.runner)
  api(libs.androidx.junit)
  api(libs.androidx.uiautomator)
  api(libs.androidx.uiautomator.shell)
  api(libs.google.truth)
  // Integration tests use RecyclerView of the app, which version depends on flavor
  compileOnly(libs.androidx.recyclerview.legacy)
}

android {
  namespace = "tgx.test"
}
