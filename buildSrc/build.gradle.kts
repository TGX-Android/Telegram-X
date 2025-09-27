import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  `kotlin-dsl`
}

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(21)
  }
}

kotlin {
  compilerOptions {
    allWarningsAsErrors = true
    jvmTarget = JvmTarget.JVM_21
  }
  jvmToolchain {
    languageVersion = JavaLanguageVersion.of(21)
  }
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
  implementation("com.android.tools.build:gradle:8.13.0")
  implementation("com.google.gms:google-services:4.4.3")
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.21")
  implementation("com.squareup.okhttp3:okhttp:4.12.0")
  implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
  implementation("com.beust:klaxon:5.6")
}