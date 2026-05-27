plugins {
  id(libs.plugins.android.library.get().pluginId)
  id("tgx-module")
}

dependencies {
  api(project(":extension:bridge"))
}

android {
  namespace = "tgx.extension"
}