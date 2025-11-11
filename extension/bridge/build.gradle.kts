plugins {
  id("com.android.library")
  id("module-plugin")
}

dependencies {
  implementation("androidx.core:core-ktx:${LibraryVersions.ANDROIDX_CORE}")
  api("org.jetbrains.kotlinx:kotlinx-coroutines-core:${LibraryVersions.COROUTINES}")
  implementation(project(":tdlib"))
}

android {
  namespace = "tgx.bridge"
}