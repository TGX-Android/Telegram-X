plugins {
  `kotlin-dsl`
}

val kotlinVersion = "1.8.20"

gradlePlugin {
  plugins {
    register("module-plugin") {
      id = "module-plugin"
      implementationClass = "me.vkryl.plugin.ModulePlugin"
    }
    register("cmake-plugin") {
      id = "cmake-plugin"
      implementationClass = "me.vkryl.plugin.CMakePlugin"
    }
  }
}

repositories {
  google()
  mavenCentral()
}

configurations.all {
  resolutionStrategy.eachDependency {
    if (requested.group == "org.jetbrains.kotlin") {
      when (requested.name) {
        "kotlin-stdlib",
        "kotlin-stdlib-common",
        "kotlin-stdlib-jdk8",
        "kotlin-reflect",
        "kotlin-compiler-embeddable",
        "kotlin-scripting-compiler-embeddable",
        "kotlin-sam-with-receiver" -> {
          this.useVersion(kotlinVersion)
        }
        else -> if (requested.version != kotlinVersion) {
          throw RuntimeException("Incompatible package: ${requested.group}:${requested.name}:${requested.version}, ${target.group}:${target.name}:${target.version}")
        }
      }
    }
  }
}

dependencies {
  compileOnly(gradleApi())
  // 8.1.0-8.1.2 create APKs that do not launch on Android 4.x (armeabi-v7a)
  implementation("com.android.tools.build:gradle:8.0.2")
  implementation("com.google.gms:google-services:4.4.0")
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${kotlinVersion}")
  implementation("com.squareup.okhttp3:okhttp:4.10.0")
  implementation("com.squareup.okhttp3:logging-interceptor:4.10.0")
  implementation("com.beust:klaxon:5.6")
}