plugins {
  id("com.android.library")
  id("module-plugin")
}

dependencies {
  implementation("com.huawei.hms:push:${LibraryVersions.HUAWEI_SERVICES}")
  implementation(project(":tdlib"))
  api(project(":extension:bridge"))
}

android {
  namespace = "tgx.extension"
}

apply(plugin = "com.huawei.agconnect")