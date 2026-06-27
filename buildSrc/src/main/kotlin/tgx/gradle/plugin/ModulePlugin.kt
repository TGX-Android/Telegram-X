package tgx.gradle.plugin

import ApplicationConfig
import Config
import Sdk
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.gradle.ProguardFiles
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.the
import tgx.gradle.findExtraFolders
import tgx.gradle.getIntOrThrow
import tgx.gradle.getOrThrow
import tgx.gradle.loadProperties

private data class Versions(
  val compileSdk: Int,
  val buildTools: String,
  val legacyNdk: String,
  val targetSdk: Int,
) {
  constructor(config: ApplicationConfig) : this(
    compileSdk = config.compileSdkVersion,
    buildTools = config.buildToolsVersion,
    legacyNdk = config.legacyNdkVersion,
    targetSdk = config.targetSdkVersion
  )
}

open class ModulePlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val config = try {
      project.extra["config"] as ApplicationConfig
    } catch (_: Exception) {
      null
    }
    val versions = if (config != null) {
      Versions(config)
    } else {
      val versions = loadProperties("version.properties")
      Versions(
        compileSdk = versions.getIntOrThrow("version.sdk_compile"),
        buildTools = versions.getOrThrow("version.build_tools"),
        targetSdk = versions.getIntOrThrow("version.sdk_target"),
        legacyNdk = versions.getOrThrow("version.ndk_legacy")
      )
    }

    val libs = project.the<LibrariesForLibs>()
    project.dependencies {
      add("coreLibraryDesugaring", libs.desugaring)
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

    val androidExt = project.extensions.getByName("android")

    androidExt.apply {
      when (this) {
        is LibraryExtension -> {
          buildToolsVersion = versions.buildTools
          ndkVersion = versions.legacyNdk
          compileSdk {
            version = release(versions.compileSdk)
          }
          compileOptions {
            isCoreLibraryDesugaringEnabled = true
            sourceCompatibility = Config.JAVA_VERSION
            targetCompatibility = Config.JAVA_VERSION
          }
          testOptions {
            unitTests.isReturnDefaultValues = true
          }
          sourceSets.configureEach {
            jniLibs.directories += "jniLibs"
          }

          defaultConfig {
            minSdk = Config.MIN_SDK_VERSION
            multiDexEnabled = true
          }
          flavorDimensions += "SDK"
          productFlavors {
            Sdk.VARIANTS.forEach { (_, variant) ->
              register(variant.flavor) {
                externalNativeBuild.cmake.arguments(
                  "-DANDROID_PLATFORM=android-${variant.minSdk}",
                  "-DTGX_FLAVOR=${variant.flavor}"
                )
                sourceSets.getByName(variant.flavor) {
                  val extraFolders = findExtraFolders(variant)
                  extraFolders.forEach { folderName ->
                    kotlin.directories += "src/$folderName/kotlin"
                    java.directories += "src/$folderName/java"
                  }
                }
              }
            }
          }
        }

        is ApplicationExtension -> {
          buildToolsVersion = versions.buildTools
          ndkVersion = versions.legacyNdk
          compileSdk {
            version = release(versions.compileSdk)
          }
          compileOptions {
            isCoreLibraryDesugaringEnabled = true
            sourceCompatibility = Config.JAVA_VERSION
            targetCompatibility = Config.JAVA_VERSION
          }
          testOptions {
            unitTests.isReturnDefaultValues = true
          }
          sourceSets.configureEach {
            jniLibs.directories += "jniLibs"
          }

          defaultConfig {
            minSdk = Config.MIN_SDK_VERSION
            targetSdk = versions.targetSdk
            multiDexEnabled = true
          }
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
        }
      }
    }
  }
}