plugins {
  id(libs.plugins.android.library.get().pluginId)
  id("tgx-module")
}

dependencies {
  implementation(libs.androidx.annotation)
}

android {
  lint {
    disable += "RawTypes"
  }

  defaultConfig {
    consumerProguardFiles("consumer-rules.pro")
  }

  project.afterEvaluate {
    tasks.withType(JavaCompile::class.java).configureEach {
      options.compilerArgs.addAll(listOf(
        "-Xlint:-rawtypes",
        "-Xlint:-cast",
        "-Xlint:-deprecation"
      ))
    }
  }

  sourceSets.named<com.android.build.api.dsl.AndroidSourceSet>("main") {
    val webrtcDir = "./../app/jni/tgvoip/third_party/webrtc"
    java.directories.addAll(listOf(
      "${webrtcDir}/rtc_base/java/src",
      "${webrtcDir}/modules/audio_device/android/java/src",
      "${webrtcDir}/sdk/android/api",
      "${webrtcDir}/sdk/android/src/java",
      "../thirdparty/WebRTC/src/java"
    ))
  }

  namespace = "tgx.tgcalls"
}