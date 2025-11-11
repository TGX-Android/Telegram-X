// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
  repositories {
    mavenCentral()
    google()
  }
  dependencies {
    // Must be the same AGP version as in buildSrc/build.gradle.kts
    classpath("com.android.tools.build:gradle:8.13.1")
  }
}