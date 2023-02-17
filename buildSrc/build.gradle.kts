plugins {
  `kotlin-dsl`
}

val kotlinVersion = "1.8.10"

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
  implementation("com.android.tools.build:gradle:7.4.1")
  implementation("com.google.gms:google-services:4.3.15")
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${kotlinVersion}")
  implementation("com.squareup.okhttp3:okhttp:4.9.3")
  implementation("com.squareup.okhttp3:logging-interceptor:4.9.3")
  implementation("com.beust:klaxon:5.6")
}