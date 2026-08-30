package tgx.gradle.plugin

import Abi
import Config
import Sdk
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.TestExtension
import com.android.build.gradle.ProguardFiles
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.*
import tgx.gradle.findExtraFolders
import tgx.gradle.ndkVersionToMinSdk
import tgx.gradle.source.AppBuildVersionSource
import tgx.gradle.source.KeystoreSource
import java.io.File

@Suppress("UnstableApiUsage")
open class ModulePlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val config = try {
      project.extensions.getByType<AppConfigurationExtension>().config.get()
    } catch (_: Exception) {
      null
    }
    val useLegacyNdk = try {
      project.extensions.getByType<AppConfigurationExtension>().useLegacyNdk.get()
    } catch (_: Exception) {
      project.providers.gradleProperty("useLegacyNdk").map {
        it.toBoolean()
      }.getOrElse(false)
    }
    val build by lazy {
      config?.build ?:
      project.providers.of(AppBuildVersionSource::class) {
        parameters.version.set(
          project.isolated.rootProject.projectDirectory.file("version.properties")
        )
      }.get()
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
          // "-Xlint:-dangling-doc-comments",

          // TODO: fix deprecation warnings by migrating to newer APIs.
          "-Xlint:-deprecation",
        ))
      }
    }

    val androidExt = project.extensions.getByName("android")
    val keystore = config?.keystorePropertiesPath?.let { keystorePropertiesPath ->
      project.providers.of(KeystoreSource::class) {
        parameters.properties.set(
          project.rootProject.projectDir.resolve(keystorePropertiesPath)
        )
      }
    }

    androidExt.apply {
      when (this) {
        is LibraryExtension -> {
          buildToolsVersion = build.buildToolsVersion
          ndkVersion = if (useLegacyNdk) {
            build.legacyNdkVersion
          } else {
            build.primaryNdkVersion
          }
          compileSdk {
            version = release(build.compileSdkVersion)
          }
          lint {
            checkReleaseBuilds = false
            disable += "LintError"
            // baseline = File("lint-baseline.xml")
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
            minSdk = maxOf(
              Config.MIN_SDK_VERSION,
              ndkVersion.ndkVersionToMinSdk()
            )
            multiDexEnabled = true
          }
          flavorDimensions += arrayOf("SDK", "ABI")
          productFlavors {
            Abi.VARIANTS.forEach { (_, variant) ->
              register(variant.flavor) {
                dimension = "ABI"
                ndk.abiFilters.addAll(variant.filters)
                externalNativeBuild.ndkBuild.abiFilters(*variant.filters)
                externalNativeBuild.cmake.abiFilters(*variant.filters)
              }
            }
            Sdk.VARIANTS.forEach { (_, variant) ->
              register(variant.flavor) {
                dimension = "SDK"
                externalNativeBuild.cmake.arguments(
                  "-DANDROID_PLATFORM=android-${variant.minSdk}",
                  "-DTGX_FLAVOR=${variant.flavor}"
                )
                sourceSets.getByName(variant.flavor) {
                  val extraFolders = findExtraFolders(variant)
                  extraFolders.forEach { folderName ->
                    kotlin.directories += "src/$folderName/kotlin"
                    java.directories += "src/$folderName/java"
                    res.directories += "src/$folderName/res"
                  }
                }
              }
            }
          }
        }

        is ApplicationExtension -> {
          buildToolsVersion = build.buildToolsVersion
          ndkVersion = if (useLegacyNdk) {
            build.primaryNdkVersion
          } else {
            build.legacyNdkVersion
          }
          compileSdk {
            version = release(build.compileSdkVersion)
          }
          lint {
            checkReleaseBuilds = false
            disable += "LintError"
            baseline = File("lint-baseline.xml")
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
            minSdk = maxOf(
              Config.MIN_SDK_VERSION,
              ndkVersion.ndkVersionToMinSdk()
            )
            targetSdk = build.targetSdkVersion
            multiDexEnabled = true
          }
          keystore?.orNull?.let { keystore ->
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
                config.enableV3Signing = true
                if (config.name == "debug") {
                  config.enableV4Signing = true
                }
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

        is TestExtension -> {
          buildToolsVersion = build.buildToolsVersion
          ndkVersion = if (useLegacyNdk) {
            build.legacyNdkVersion
          } else {
            build.primaryNdkVersion
          }
          compileSdk {
            version = release(build.compileSdkVersion)
          }
          compileOptions {
            isCoreLibraryDesugaringEnabled = true
            sourceCompatibility = Config.JAVA_VERSION
            targetCompatibility = Config.JAVA_VERSION
          }
          defaultConfig {
            minSdk = maxOf(
              Config.MIN_SDK_VERSION,
              ndkVersion.ndkVersionToMinSdk()
            )
            multiDexEnabled = true
          }
          sourceSets.configureEach {
            jniLibs.directories += "jniLibs"
          }

          keystore?.orNull?.let { keystore ->
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
                config.enableV3Signing = true
                if (config.name == "debug") {
                  config.enableV4Signing = true
                }
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

        else -> {
          error(this)
        }
      }
    }
  }
}