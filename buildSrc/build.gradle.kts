plugins {
  `kotlin-dsl`
}

val kotlinVersion = "1.9.23"

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
        "kotlin-assignment",
        "kotlin-assignment-compiler-plugin-embeddable",
        "kotlin-stdlib",
        "kotlin-stdlib-common",
        "kotlin-stdlib-jdk7",
        "kotlin-stdlib-jdk8",
        "kotlin-reflect",
        "kotlin-compiler-embeddable",
        "kotlin-scripting-compiler-embeddable",
        "kotlin-sam-with-receiver",
        "kotlin-sam-with-receiver-compiler-plugin-embeddable" -> {
          this.useVersion(kotlinVersion)
        }
        else -> if (requested.version != kotlinVersion) {
          error("Incompatible package: ${requested.group}:${requested.name}:${requested.version}, ${target.group}:${target.name}:${target.version}")
        }
      }
    }
  }
}

dependencies {
  compileOnly(gradleApi())
  implementation("com.android.tools.build:gradle:8.3.0")
  implementation("com.google.gms:google-services:4.4.1")
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${kotlinVersion}")
  implementation("com.squareup.okhttp3:okhttp:4.12.0")
  implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
  implementation("com.beust:klaxon:5.6")
}