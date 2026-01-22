@file:Suppress("UnstableApiUsage")

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
include(
  ":tdlib",

  ":vkryl:td",
  ":vkryl:android",
  ":vkryl:leveldb",
  ":vkryl:core",

  ":extension:bridge",
  ":extension:${extra["extension"]}",

  ":app"
)