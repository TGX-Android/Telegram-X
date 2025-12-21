import tgx.gradle.flavorImplementation

plugins {
  id(libs.plugins.android.library.get().pluginId)
  alias(libs.plugins.kotlin.android)
  id("tgx-module")
}

dependencies {
  flavorImplementation(
    libs.androidx.core.ktx.legacy,
    libs.androidx.core.ktx.latest
  )
  api(libs.kotlinx.coroutines.core)
  implementation(project(":tdlib"))
}

android {
  namespace = "tgx.bridge"
}