plugins {
  id("com.android.library")
  id("module-plugin")
}

dependencies {
  implementation("com.huawei.hms:push:${LibraryVersions.HUAWEI_SERVICES}")
  implementation(project(":tdlib"))
  api(project(":extension:bridge"))
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

android {
  namespace = "tgx.extension"
}