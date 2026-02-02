plugins {
  id(libs.plugins.android.library.get().pluginId)
  alias(libs.plugins.kotlin.android)
  id("tgx-module")
}

dependencies {
  implementation(libs.androidx.annotation)
}

android {
  lint {
    disable += "RawTypes"
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

  sourceSets.getByName("main") {
    val webrtcDir = "./../app/jni/tgvoip/third_party/webrtc"
    java.srcDirs(
      "${webrtcDir}/rtc_base/java/src",
      "${webrtcDir}/modules/audio_device/android/java/src",
      "${webrtcDir}/sdk/android/api",
      "${webrtcDir}/sdk/android/src/java",
      "../thirdparty/WebRTC/src/java"
    )
  }

  namespace = "tgx.tgcalls"
}