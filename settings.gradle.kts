@file:Suppress("UnstableApiUsage", "AvoidApplyPluginMethod")

apply(from = "properties.gradle.kts")

pluginManagement {
  repositories {
    google()
    mavenCentral()
    maven(url = "https://jitpack.io")
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    maven(url = "https://jitpack.io")
    if (extra["huawei"] == true) {
      maven(url = "https://developer.huawei.com/repo/")
    }
  }
}

rootProject.name = "tgx"
includeBuild("vkryl/annotation")
include(
  ":tdlib",
  ":tgcalls",

  ":vkryl:td",
  ":vkryl:android",
  ":vkryl:leveldb",
  ":vkryl:core",

  ":extension:bridge",
  ":extension:${extra["extension"]}",

  ":app",

  ":tests:baseline-profile",
  ":tests:core",
  ":tests:ui",
  ":tests:benchmark",
  ":tests:microbenchmark"
)
