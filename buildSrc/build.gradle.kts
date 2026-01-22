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
    register("tgx-config") {
      id = "tgx-config"
      implementationClass = "tgx.gradle.plugin.ConfigurationPlugin"
    }
    register("tgx-module") {
      id = "tgx-module"
      implementationClass = "tgx.gradle.plugin.ModulePlugin"
    }
  }
}

dependencies {
  // https://github.com/gradle/gradle/issues/15383#issuecomment-779893192
  implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))

  compileOnly(gradleApi())
  implementation(libs.android.gradle.plugin)
  implementation(libs.okhttp.latest)
  implementation(libs.kotlinx.serialization.json)
}

apply(from = "${rootDir.parentFile}/properties.gradle.kts")
if (extra["huawei"] == true) {
  dependencies {
    implementation(libs.huawei.agconnect)
  }
}