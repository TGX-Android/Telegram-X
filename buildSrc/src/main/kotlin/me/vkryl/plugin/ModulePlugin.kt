/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
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
import buildConfigLong
import buildConfigString
import buildDate
import com.android.build.gradle.AppExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.ProguardFiles
import getLongOrThrow
import getOrThrow
import loadProperties
import monthYears
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.get
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File
import java.net.URI
import java.util.*

open class ModulePlugin : Plugin<Project> {
  data class PullRequest (
    val id: Long,
    val commitShort: String,
    val commitLong: String,
    val commitDate: Long,
    val author: String
  ) {
    constructor(id: Long, properties: Properties) : this(
      id,
      properties.getOrThrow("pr.$id.commit_short"),
      properties.getOrThrow("pr.$id.commit_long"),
      properties.getLongOrThrow("pr.$id.date"),
      properties.getOrThrow("pr.$id.author")
    )
  }

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
    val keystoreFilePath = properties.getProperty("keystore.file", "")
    val disableSigning = properties.getProperty("app.disable_signing", "false") == "true"
    val keystore = if (keystoreFilePath.isNotEmpty() && !disableSigning) {
      Keystore(keystoreFilePath)
    } else {
      null
    }
    val appVersionOverride = properties.getProperty("app.version", "0").toInt()
    val appId = properties.getOrThrow("app.id")
    val isExperimentalBuild = keystore == null || properties.getProperty("app.experimental", "false") == "true"
    val dontObfuscate = properties.getProperty("app.dontobfuscate", "false") == "true"

    project.extra.set("experimental", isExperimentalBuild)

    val versions = loadProperties("version.properties")
    val appVersion = if (appVersionOverride > 0) appVersionOverride else versions.getOrThrow("version.app").toInt()
    val majorVersion = versions.getOrThrow("version.major").toInt()

    val timeZone = TimeZone.getTimeZone("UTC")
    val then = Calendar.getInstance(timeZone)
    then.timeInMillis = versions.getOrThrow("version.creation").toLong()
    val now = buildDate(timeZone)
    if (now.timeInMillis < then.timeInMillis)
      error("Invalid commit time!")
    val minorVersion = monthYears(now, then)

    val androidExt = project.extensions.getByName("android")
    if (androidExt is BaseExtension) {
      androidExt.apply {
        compileSdkVersion(versions.getOrThrow("version.sdk_compile").toInt())
        buildToolsVersion(versions.getOrThrow("version.build_tools"))
        ndkVersion = versions.getOrThrow("version.ndk")
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
            options.compilerArgs.addAll(listOf("-Xmaxerrs", "2000"))
          }
        }

        defaultConfig {
          minSdk = Config.MIN_SDK_VERSION
          targetSdk = versions.getOrThrow("version.sdk_target").toInt()
          multiDexEnabled = true
          versionCode = appVersion
          versionName = "${majorVersion}.${minorVersion}"
        }

        when (this) {
          is LibraryExtension -> {
            defaultConfig {
              consumerProguardFiles("consumer-rules.pro")
            }
          }

          is AppExtension -> {
            var git: List<String>
            val process = ProcessBuilder("bash", "-c", "echo \"$(git rev-parse --short HEAD) $(git rev-parse HEAD) $(git show -s --format=%ct) $(git config --get remote.origin.url) $(git log -1 --pretty=format:'%an')\"").start()
            process.inputStream.reader(Charsets.UTF_8).use {
              git = it.readText().trim().split(' ', limit = 5)
            }
            process.waitFor()
            if (git.size != 5) {
              error("Source code must be fetched from git repository.")
            }
            val commitHashShort = git[0]
            val commitHashLong = git[1]
            val commitDate = git[2].toLong()
            val commitAuthor = git[4]
            val remoteUrl = if (git[3].startsWith("git@")) {
              val index = git[3].indexOf(':', 4)
              val domain = git[3].substring(4, index)
              val endIndex = if (git[3].endsWith(".git")) {
                git[3].length - 4
              } else {
                git[3].length
              }
              val query = git[3].substring(index + 1, endIndex)
              "https://${domain}/${query}"
            } else {
              git[3]
            }

            if (URI.create(remoteUrl).host != "github.com") {
              error("Unfortunately, currently you must host your fork on github.com.")
            }

            val commitUrl = String.format(Locale.ENGLISH, "%1\$s/tree/%3\$s", remoteUrl, commitHashShort, commitHashLong)

            project.extra.set("properties", properties)
            project.extra.set("versions", versions)

            val pullRequests: List<PullRequest> = properties.getProperty("pr.ids", "").split(',').filter { it.matches(Regex("^[0-9]+$")) }.map {
              PullRequest(it.toLong(), properties)
            }.sortedBy { it.id }

            namespace = "org.thunderdog.challegram"

            defaultConfig {
              applicationId = appId

              buildConfigString("PROJECT_NAME", properties.getOrThrow("app.name"))
              buildConfigString("MARKET_URL", "https://play.google.com/store/apps/details?id=${appId}")

              buildConfigString("DOWNLOAD_URL", properties.getOrThrow("app.download_url"))

              buildConfigString("REMOTE_URL", remoteUrl)
              buildConfigString("COMMIT_URL", commitUrl)
              buildConfigString("COMMIT", commitHashShort)
              buildConfigString("COMMIT_FULL", commitHashLong)
              buildConfigLong("COMMIT_DATE", commitDate)
              buildConfigString("SOURCES_URL", properties.getProperty("app.sources_url", remoteUrl))

              buildConfigField("long[]", "PULL_REQUEST_ID", "{${
                pullRequests.joinToString(", ") { it.id.toString() }
              }}")
              buildConfigField("long[]", "PULL_REQUEST_COMMIT_DATE", "{${
                pullRequests.joinToString(", ") { it.commitDate.toString() }
              }}")
              buildConfigField("String[]", "PULL_REQUEST_COMMIT", "{${
                pullRequests.joinToString(", ") { "\"${it.commitShort}\"" }
              }}")
              buildConfigField("String[]", "PULL_REQUEST_COMMIT_FULL", "{${
                pullRequests.joinToString(", ") { "\"${it.commitLong}\"" }
              }}")
              buildConfigField("String[]", "PULL_REQUEST_URL", "{${
                pullRequests.joinToString(", ") { "\"${remoteUrl}/pull/${it.id}/files/${it.commitLong}\"" } 
              }}")
              buildConfigField("String[]", "PULL_REQUEST_AUTHOR", "{${
                pullRequests.joinToString(", ") { "\"${it.author}\"" }
              }}")
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

            buildTypes {
              lintOptions {
                disable("MissingTranslation")
                isCheckDependencies = true
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