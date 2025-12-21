plugins {
  id(libs.plugins.android.library.get().pluginId)
  alias(libs.plugins.kotlin.android)
  id("tgx-module")
}

dependencies {
  api(project(":extension:bridge"))
}

android {
  namespace = "tgx.extension"
}