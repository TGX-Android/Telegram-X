plugins {
  `kotlin-dsl`
}

gradlePlugin {
  plugins {
    register("module-plugin") {
      id = "module-plugin"
      implementationClass = "tgx.gradle.plugin.ModulePlugin"
    }
    register("cmake-plugin") {
      id = "cmake-plugin"
      implementationClass = "tgx.gradle.plugin.CMakePlugin"
    }
  }
}

repositories {
  google()
  mavenCentral()
}

dependencies {
  compileOnly(gradleApi())
  implementation("com.android.tools.build:gradle:8.10.1")
  implementation("com.google.gms:google-services:4.4.2")
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.20")
  implementation("com.squareup.okhttp3:okhttp:4.12.0")
  implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
  implementation("com.beust:klaxon:5.6")
}