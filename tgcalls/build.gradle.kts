plugins {
  id("java-toolchain-convention")
  id(libs.plugins.android.library.get().pluginId)
  id("tgx-module")
}

dependencies {
  implementation(libs.androidx.annotation)
}

tasks.withType(JavaCompile::class.java).configureEach {
  options.compilerArgs.addAll(listOf(
    "-Xlint:-rawtypes",
    "-Xlint:-cast",
    "-Xlint:-deprecation"
  ))
}

android {
  lint {
    disable += arrayOf(
      "ObsoleteSdkInt",
      "MissingPermission",
      "InlinedApi",
      "NewApi",
      "UseRequiresApi",
      "Range",
      "WrongConstant"
    )
  }

  defaultConfig {
    consumerProguardFiles("consumer-rules.pro")
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