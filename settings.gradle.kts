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
  }
}

rootProject.name = "tgx"
include(
  ":tdlib",

  ":vkryl:td",
  ":vkryl:android",
  ":vkryl:leveldb",
  ":vkryl:core",

  ":app"
)