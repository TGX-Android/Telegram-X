plugins {
  id("com.android.library")
  id("module-plugin")
}

dependencies {
  api(project(":extension:bridge"))
}

android {
  namespace = "tgx.extension"
}