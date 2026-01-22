plugins {
  id(libs.plugins.android.library.get().pluginId)
  alias(libs.plugins.kotlin.android)
  id("tgx-module")
}

dependencies {
  implementation(libs.huawei.hms)
  implementation(project(":tdlib"))
  api(project(":extension:bridge"))
}

android {
  namespace = "tgx.extension"
}