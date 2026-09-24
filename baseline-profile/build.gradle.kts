import tgx.gradle.*

plugins {
  id("java-toolchain-convention")
  id(libs.plugins.android.test.get().pluginId)
  id(libs.plugins.androidx.baselineprofile.get().pluginId)
  id("tgx-module")
}

android {
  namespace = "tgx.baselineprofile"
  defaultConfig {
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  targetProjectPath = ":app"

  flavorDimensions += listOf("SDK", "ABI")
  androidComponents.disableRudimentaryVariants { _, abiVariant ->
    abiVariant.isTestingLab
  }
  productFlavors {
    create(Sdk.VARIANTS[Sdk.LATEST]!!.flavor) {
      dimension = "SDK"
    }
    create(Abi.VARIANTS[Abi.LAB]!!.flavor) {
      dimension = "ABI"
    }
  }

  testOptions {
    managedDevices {
      localDevices {
        create("pixel6Api34") {
          device = "Pixel 6"
          apiLevel = 34 /*Android 14*/
          systemImageSource = "aosp"
          require64Bit = true
        }
      }
    }
  }
}

baselineProfile {
  managedDevices += "pixel6Api34"
  useConnectedDevices = true
}

dependencies {
  implementation(libs.androidx.benchmark.macro.junit4)
  implementation(libs.androidx.espresso.core)
  implementation(libs.androidx.junit)
  implementation(libs.androidx.uiautomator)
}

androidComponents {
  onVariants { variant ->
    val artifactsLoader = variant.artifacts.getBuiltArtifactsLoader()
    variant.instrumentationRunnerArguments.put(
      "app.id",
      variant.testedApks.map {
        artifactsLoader.load(it)?.applicationId
      }
    )
  }
}