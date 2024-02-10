/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package me.vkryl.plugin

import Config
import Keystore
import LibraryVersions
import buildConfigString
import com.android.build.gradle.AppExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.ProguardFiles
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import getOrThrow
import loadProperties
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.get
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File

open class ModulePlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val plugins = arrayOf(
      "kotlin-android"
    )
    for (plugin in plugins) {
      project.plugins.apply(plugin)
    }

    project.dependencies {
      add("coreLibraryDesugaring", "com.android.tools:desugar_jdk_libs:${LibraryVersions.DESUGAR}")
    }

    val properties = loadProperties()
    val sampleProperties = loadProperties("local.properties.sample")
    val keystoreFilePath = properties.getProperty("keystore.file", "")
    val disableSigning = properties.getProperty("app.disable_signing", "false") == "true"
    val keystore = if (keystoreFilePath.isNotEmpty() && !disableSigning) {
      Keystore(keystoreFilePath)
    } else {
      null
    }
    val safetyNetToken = if (keystore != null) {
      properties.getProperty("safetynet.api_key", "")
    } else {
      null
    }
    fun getOrSample (key: String): String {
      return properties.getProperty(key, null) ?: sampleProperties.getOrThrow(key)
    }
    val appId = getOrSample("app.id")
    val appName = getOrSample("app.name")
    val appDownloadUrl = getOrSample("app.download_url")
    val googlePlayUrl = properties.getProperty("app.google_download_url", null)
    val galaxyStoreUrl = properties.getProperty("app.galaxy_download_url", null)
    val huaweiAppGalleryUrl = properties.getProperty("app.huawei_download_url", null)
    val amazonAppStoreUrl = properties.getProperty("app.amazon_download_url", null)
    val isExampleBuild = appId.startsWith("com.example.") || appId.startsWith("org.example.")
    val isExperimentalBuild = isExampleBuild || keystore == null || properties.getProperty("app.experimental", "false") == "true"
    val dontObfuscate = isExampleBuild || properties.getProperty("app.dontobfuscate", "false") == "true"

    project.extra.set("experimental", isExperimentalBuild)
    project.extra.set("app_name", appName)

    val versions = loadProperties("version.properties")

    val androidExt = project.extensions.getByName("android")
    if (androidExt is BaseExtension) {
      androidExt.apply {
        compileSdkVersion(versions.getOrThrow("version.sdk_compile").toInt())
        buildToolsVersion(versions.getOrThrow("version.build_tools"))

        // TODO: investigate why AGP 8.1.2 forces default ndkVersion,
        // despite having it properly set in productFlavors.${flavor}
        ndkVersion = versions.getOrThrow("version.ndk_legacy")
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
          java.srcDirs("src/${this.name}/kotlin")
          jniLibs.srcDirs("jniLibs")
        }

        project.tasks.withType(KotlinCompile::class.java).configureEach {
          kotlinOptions {
            jvmTarget = Config.JAVA_VERSION.toString()
            allWarningsAsErrors = true
          }
        }

        project.afterEvaluate {
          tasks.withType(JavaCompile::class.java).configureEach {
            options.compilerArgs.addAll(listOf("-Xmaxerrs", "2000", "-Xlint:unchecked", "-Xlint:deprecation"))
          }
        }

        defaultConfig {
          minSdk = Config.MIN_SDK_VERSION
          targetSdk = versions.getOrThrow("version.sdk_target").toInt()
          multiDexEnabled = true
        }

        when (this) {
          is LibraryExtension -> {
            defaultConfig {
              consumerProguardFiles("consumer-rules.pro")
            }
          }

          is AppExtension -> {
            if (properties.getProperty("telegram.api_id", "").isEmpty() || properties.getProperty("telegram.api_hash").isEmpty()) {
              error("""
                Telegram API credentials missing.
                
                Set them in your local.properties file:
                telegram.api_id=YOUR_API_ID_HERE
                telegram.api_hash=YOUR_API_HASH_HERE
                
                Obtain them at https://core.telegram.org/api/obtaining_api_id
              """.trimIndent())
            }

            project.extra.set("properties", properties)
            project.extra.set("versions", versions)

            namespace = "org.thunderdog.challegram"

            defaultConfig {
              applicationId = appId

              buildConfigString("PROJECT_NAME", appName)

              buildConfigString("SAFETYNET_API_KEY", safetyNetToken)

              buildConfigString("DOWNLOAD_URL", appDownloadUrl)
              buildConfigString("GOOGLE_PLAY_URL", googlePlayUrl)
              buildConfigString("GALAXY_STORE_URL", galaxyStoreUrl)
              buildConfigString("HUAWEI_APPGALLERY_URL", huaweiAppGalleryUrl)
              buildConfigString("AMAZON_APPSTORE_URL", amazonAppStoreUrl)
            }

            if (keystore != null) {
              signingConfigs {
                arrayOf(getByName("debug"), maybeCreate("release")).forEach { config ->
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
                  ndk.jobs = Runtime.getRuntime().availableProcessors()
                }

                getByName("release") {
                  signingConfig = signingConfigs["release"]

                  isMinifyEnabled = !dontObfuscate
                  isShrinkResources = !dontObfuscate

                  ndk.debugSymbolLevel = "full"
                  ndk.jobs = Runtime.getRuntime().availableProcessors()

                  proguardFiles(
                    getDefaultProguardFile(ProguardFiles.ProguardFile.OPTIMIZE.fileName),
                    "proguard-rules.pro"
                  )
                }
              }
            }

            if (this is BaseAppModuleExtension) {
              // FIXME[gradle]: lint is still not available through AppExtension
              lint {
                disable += "MissingTranslation"
                checkDependencies = true
              }
            }

            project.dependencies {
              add("implementation", "androidx.multidex:multidex:${LibraryVersions.MULTIDEX}")
            }
          }
        }
      }
    }
  }
}