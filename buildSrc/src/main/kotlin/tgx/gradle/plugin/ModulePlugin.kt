package tgx.gradle.plugin

import ApplicationConfig
import Config
import com.android.build.gradle.AppExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.ProguardFiles
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.the
import tgx.gradle.getIntOrThrow
import tgx.gradle.getOrThrow
import tgx.gradle.loadProperties
import java.io.File

open class ModulePlugin : Plugin<Project> {
  override fun apply(project: Project) {
    var compileSdkVersion: Int
    var buildToolsVersion: String
    var legacyNdkVersion: String
    var targetSdkVersion: Int

    val config = try {
      project.extra["config"] as ApplicationConfig
    } catch (_: Exception) {
      null
    }
    if (config != null) {
      compileSdkVersion = config.compileSdkVersion
      buildToolsVersion = config.buildToolsVersion
      legacyNdkVersion = config.legacyNdkVersion
      targetSdkVersion = config.targetSdkVersion
    } else {
      val versions = loadProperties("version.properties")
      compileSdkVersion = versions.getIntOrThrow("version.sdk_compile")
      buildToolsVersion = versions.getOrThrow("version.build_tools")
      targetSdkVersion = versions.getIntOrThrow("version.sdk_target")
      legacyNdkVersion = versions.getOrThrow("version.ndk_legacy")
    }

    val libs = project.the<LibrariesForLibs>()
    project.dependencies {
      add("coreLibraryDesugaring", libs.desugaring)
    }

    val androidExt = project.extensions.getByName("android")
    if (androidExt is BaseExtension) {
      androidExt.apply {
        compileSdkVersion(compileSdkVersion)
        buildToolsVersion(buildToolsVersion)

        ndkVersion = legacyNdkVersion
        ndkPath = File(sdkDirectory, "ndk/$ndkVersion").absolutePath

        compileOptions {
          isCoreLibraryDesugaringEnabled = true
          sourceCompatibility = Config.JAVA_VERSION
          targetCompatibility = Config.JAVA_VERSION
        }

        testOptions {
          unitTests.isReturnDefaultValues = true
        }

        sourceSets.configureEach {
          jniLibs.srcDirs("jniLibs")
        }

        project.afterEvaluate {
          tasks.withType(JavaCompile::class.java).configureEach {
            options.compilerArgs.addAll(listOf(
              "-Xmaxerrs", "2000",
              "-Xmaxwarns", "2000",

              "-Xlint:all",
              "-Xlint:unchecked",

              "-Xlint:-serial",
              "-Xlint:-lossy-conversions",
              "-Xlint:-overloads",
              "-Xlint:-overrides",
              "-Xlint:-this-escape",

              // TODO: fix deprecation warnings by migrating to newer APIs.
              "-Xlint:-deprecation",
            ))
          }
        }

        defaultConfig {
          minSdk = Config.MIN_SDK_VERSION
          targetSdk = targetSdkVersion
          multiDexEnabled = true
        }

        when (this) {
          is LibraryExtension -> {
            defaultConfig {
              consumerProguardFiles("consumer-rules.pro")
            }
            flavorDimensions += "SDK"
            productFlavors {
              Sdk.VARIANTS.forEach { (_, variant) ->
                register(variant.flavor) {
                  externalNativeBuild.cmake.arguments(
                    "-DANDROID_PLATFORM=android-${variant.minSdk}",
                    "-DTGX_FLAVOR=${variant.flavor}"
                  )
                }
              }
            }
          }

          is AppExtension -> {
            config?.keystore?.let { keystore ->
              signingConfigs {
                arrayOf(
                  getByName("debug"),
                  maybeCreate("release")
                ).forEach { config ->
                  config.storeFile = keystore.file
                  config.storePassword = keystore.password
                  config.keyAlias = keystore.keyAlias
                  config.keyPassword = keystore.keyPassword
                  config.enableV2Signing = true
                }
              }

              buildTypes {
                getByName("debug") {
                  signingConfig = signingConfigs["debug"]

                  isDebuggable = true
                  isJniDebuggable = true
                  isMinifyEnabled = false

                  ndk.debugSymbolLevel = "full"

                  if (config.forceOptimize) {
                    proguardFiles(
                      getDefaultProguardFile(ProguardFiles.ProguardFile.OPTIMIZE.fileName),
                      "proguard-rules.pro"
                    )
                    if (config.isHuaweiBuild) {
                      proguardFile("proguard-hms.pro")
                    }
                  }
                }

                getByName("release") {
                  signingConfig = signingConfigs["release"]

                  isMinifyEnabled = !config.doNotObfuscate
                  isShrinkResources = !config.doNotObfuscate

                  ndk.debugSymbolLevel = "full"

                  proguardFiles(
                    getDefaultProguardFile(ProguardFiles.ProguardFile.OPTIMIZE.fileName),
                    "proguard-rules.pro"
                  )

                  if (config.isHuaweiBuild) {
                    proguardFile("proguard-hms.pro")
                  }
                }
              }
            }

            project.dependencies {
              add("implementation", libs.androidx.multidex)
            }
          }
        }

        if (this is LibraryExtension) {
          defaultConfig {
            consumerProguardFiles("consumer-rules.pro")
          }
        }
      }
    }
  }
}