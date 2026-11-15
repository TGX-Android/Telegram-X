@file:Suppress("AvoidApplyPluginMethod")

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  `kotlin-dsl`
}

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(25)
  }
}

val hasTests = file("../tests/.git").exists()
val tests = file("../tests/buildSrc")

kotlin {
  if (hasTests) {
    sourceSets.named("main") {
      kotlin.srcDir(tests.resolve("kotlin"))
    }
    sourceSets.named("test") {
      kotlin.srcDir(tests.resolve("checks"))
    }
  }
  compilerOptions {
    allWarningsAsErrors = true
    jvmTarget = JvmTarget.JVM_25
  }
  jvmToolchain {
    languageVersion = JavaLanguageVersion.of(25)
  }
}

gradlePlugin {
  plugins {
    register("tgx-config") {
      id = "tgx-config"
      implementationClass = "tgx.gradle.plugin.AppConfigurationPlugin"
    }
    register("tgx-module") {
      id = "tgx-module"
      implementationClass = "tgx.gradle.plugin.ModulePlugin"
    }
    if (hasTests) {
      register("tgx-tests-root") {
        id = "tgx-tests-root"
        implementationClass = "tgx.test.RootTestsPlugin"
      }
      register("tgx-tests-app") {
        id = "tgx-tests-app"
        implementationClass = "tgx.test.AppTestsPlugin"
      }
      register("tgx-tests-module") {
        id = "tgx-tests-module"
        implementationClass = "tgx.test.ModuleTestsPlugin"
      }
    }
  }
}

dependencies {
  // https://github.com/gradle/gradle/issues/15383#issuecomment-779893192
  implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))

  compileOnly(gradleApi())
  implementation(libs.vkryl.annotation)
  implementation(libs.android.gradle.plugin)
  implementation(libs.kotlin.gradle.plugin)
  implementation(libs.okhttp.latest)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.jgit)
  implementation(libs.jgit.lfs)
  if (hasTests) {
    testImplementation(libs.bundles.unit.test)
  }
}

apply(from = "${rootDir.parentFile}/properties.gradle.kts")
if (extra["huawei"] == true) {
  dependencies {
    implementation(libs.huawei.agconnect)
  }
}