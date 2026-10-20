package tgx.gradle.plugin

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
import tgx.gradle.*
import tgx.gradle.source.AppBuildVersionSource
import tgx.gradle.source.KeystoreSource
import java.io.File

@Suppress("UnstableApiUsage")
open class ModulePlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val config =
      project.extensions.findByType<AppConfigurationExtension>()?.config?.get()
    val doNotObfuscate =
      config?.doNotObfuscate ?: false
    val useLegacyNdk = (
      project.extensions.findByType<AppConfigurationExtension>()?.useLegacyNdk ?:
      project.gradleFlag("useLegacyNdk")
    )
    val build =
      config?.build ?:
      project.providers.of(AppBuildVersionSource::class) {
        parameters.version.set(
          project.isolated.rootProject.projectDirectory.file("version.properties")
        )
      }.get()

    val appliedNdkVersion = useLegacyNdk.map { useLegacyNdk ->
      if (useLegacyNdk) {
        build.legacyNdkVersion
      } else {
        build.primaryNdkVersion
      }
    }.get()

    val globalAbiFiltersProvider = (
      project.extensions.findByType<AppConfigurationExtension>()?.abiFilters ?:
      project.findAbiFilters(useLegacyNdk)
    )
    val globalAbiFilters = globalAbiFiltersProvider.get()

    val enableSplitAbi = (
      project.extensions.findByType<AppConfigurationExtension>()?.enableSplitAbi ?:
      project.needEnableSplitAbi(globalAbiFiltersProvider)
    ).get()

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
          "-Xlint:-dangling-doc-comments",

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
          ndkVersion = appliedNdkVersion
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
            if (!enableSplitAbi) {
              ndk.abiFilters += globalAbiFilters
              externalNativeBuild.cmake.abiFilters += globalAbiFilters
            }
          }
          splits.abi {
            isEnable = enableSplitAbi
            reset()
            include(*globalAbiFilters.toTypedArray())
            isUniversalApk = false
          }
          flavorDimensions += "SDK"
          productFlavors {
            Sdk.VARIANTS.values.forEach { variant ->
              val selectedMinSdk = maxOf(
                Config.MIN_SDK_VERSION,
                variant.minSdk,
                ndkVersion.ndkVersionToMinSdk()
              )
              register(variant.flavor) {
                dimension = "SDK"
                externalNativeBuild.cmake.arguments(
                  "-DANDROID_PLATFORM=android-${selectedMinSdk}",
                  "-DANDROID_MIN_SDK_VERSION=${selectedMinSdk}",
                  "-DANDROID_STL=c++_shared",
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
          ndkVersion = appliedNdkVersion
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
            if (!enableSplitAbi) {
              ndk.abiFilters += globalAbiFilters
              externalNativeBuild.cmake.abiFilters += globalAbiFilters
            }
          }
          splits.abi {
            isEnable = enableSplitAbi
            reset()
            include(*globalAbiFilters.toTypedArray())
            isUniversalApk = false
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
          }

          buildTypes {
            getByName("debug") {
              signingConfig = signingConfigs["debug"]

              isDebuggable = true
              isJniDebuggable = true
              isMinifyEnabled = false

              multiDexKeepProguard = requireFile(project.layout.projectDirectory.file("multidex-rules.pro").asFile)

              ndk.debugSymbolLevel = "full"

              config?.takeIf {
                it.forceOptimize
              }?.let { config ->
                isMinifyEnabled = !doNotObfuscate
                isShrinkResources = !doNotObfuscate

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
              signingConfig = signingConfigs.findByName("release") ?: signingConfigs["debug"].takeIf {
                config?.isExperimentalBuild ?: false
              }

              isMinifyEnabled = !doNotObfuscate
              isShrinkResources = !doNotObfuscate

              multiDexKeepProguard = requireFile(project.layout.projectDirectory.file("multidex-rules.pro").asFile)

              ndk.debugSymbolLevel = "full"

              proguardFiles(
                getDefaultProguardFile(ProguardFiles.ProguardFile.OPTIMIZE.fileName),
                "proguard-rules.pro"
              )

              if (config?.isHuaweiBuild ?: false) {
                proguardFile("proguard-hms.pro")
              }
            }
          }
        }

        is TestExtension -> {
          buildToolsVersion = build.buildToolsVersion
          ndkVersion = appliedNdkVersion
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

                config?.let { config ->
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
              }

              getByName("release") {
                signingConfig = signingConfigs["release"]

                val doNotObfuscate = config?.doNotObfuscate ?: false
                isMinifyEnabled = !doNotObfuscate
                isShrinkResources = !doNotObfuscate

                ndk.debugSymbolLevel = "full"

                proguardFiles(
                  getDefaultProguardFile(ProguardFiles.ProguardFile.OPTIMIZE.fileName),
                  "proguard-rules.pro"
                )

                if (config?.isHuaweiBuild ?: false) {
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