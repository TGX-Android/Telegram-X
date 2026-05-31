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
package tgx.gradle.plugin

import ApplicationConfig
import PullRequest
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.extra
import tgx.gradle.fatal
import tgx.gradle.getIntOrThrow
import tgx.gradle.getOrThrow
import tgx.gradle.loadProperties
import java.io.File

class Keystore (configPath: String) {
  val file: File
  val password: String
  val keyAlias: String
  val keyPassword: String

  init {
    val config = loadProperties(configPath)
    this.file = File(config.getOrThrow("keystore.file"))
    this.password = config.getOrThrow("keystore.password")
    this.keyAlias = config.getOrThrow("key.alias")
    this.keyPassword = config.getOrThrow("key.password")
  }
}

open class ConfigurationPlugin : Plugin<Project> {
  override fun apply(project: Project) {
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
    val applicationId = getOrSample("app.id")
    val applicationName = getOrSample("app.name")
    val appDownloadUrl = getOrSample("app.download_url")
    val googlePlayUrl = properties.getProperty("app.google_download_url", null)
    val galaxyStoreUrl = properties.getProperty("app.galaxy_download_url", null)
    val huaweiAppGalleryUrl = properties.getProperty("app.huawei_download_url", null)
    val amazonAppStoreUrl = properties.getProperty("app.amazon_download_url", null)
    val isExampleBuild = applicationId.startsWith("com.example.") || applicationId.startsWith("org.example.")
    val isExperimentalBuild = isExampleBuild || keystore == null || properties.getProperty("app.experimental", "false") == "true"
    val doNotObfuscate = isExampleBuild || properties.getProperty("app.dontobfuscate", "false") == "true"
    val forceOptimize = properties.getProperty("app.forceoptimize") == "true"
    val appExtension = getOrSample("tgx.extension")
    if (appExtension != "none" && appExtension != "hms") {
      error("Unknown tgx.extension: $appExtension")
    }
    val isHuaweiBuild = appExtension == "hms"

    val versions = loadProperties("version.properties")

    val compileSdkVersion = versions.getIntOrThrow("version.sdk_compile")
    val buildToolsVersion = versions.getOrThrow("version.build_tools")
    val targetSdkVersion = versions.getIntOrThrow("version.sdk_target")

    val legacyNdkVersion = versions.getOrThrow("version.ndk_legacy")
    val primaryNdkVersion = versions.getOrThrow("version.ndk_primary")

    if (properties.getProperty("telegram.api_id", "").isEmpty() || properties.getProperty("telegram.api_hash").isEmpty()) {
      fatal("""
        Telegram API credentials missing.
        
        Set them in your local.properties file:
        telegram.api_id=YOUR_API_ID_HERE
        telegram.api_hash=YOUR_API_HASH_HERE
        
        Obtain them at https://core.telegram.org/api/obtaining_api_id
      """.trimIndent())
    }

    val nativeLibraryVersion = versions.getProperty("version.jni")
    val leveldbVersion = versions.getProperty("version.leveldb")
    val emojiVersion = versions.getIntOrThrow("version.emoji")

    val telegramApiId = properties.getIntOrThrow("telegram.api_id")
    val telegramApiHash = properties.getOrThrow("telegram.api_hash")

    val creationDateMillis = versions.getOrThrow("version.creation").toLong()

    val pullRequests = properties.getProperty("pr.ids", "").split(',').filter { it.matches(Regex("^[0-9]+$")) }.map {
      PullRequest(it.toLong(), properties)
    }.sortedBy { it.id }

    val defaultFileNamePrefix = applicationName.replace(" ", "-").replace("#", "")
    val outputFileNamePrefix = properties.getProperty("app.file", defaultFileNamePrefix)

    val appVersionOverride = properties.getProperty("app.version", "0").toInt()
    val applicationVersion = if (appVersionOverride > 0) appVersionOverride else versions.getOrThrow("version.app").toInt()
    val majorVersion = versions.getOrThrow("version.major").toInt()

    val sourceCodeUrl = properties.getProperty("app.sources_url", "")

    val config = ApplicationConfig(
      applicationName,
      applicationId,
      appExtension,
      sourceCodeUrl,
      applicationVersion,
      majorVersion,
      isExperimentalBuild,
      isHuaweiBuild,
      forceOptimize,
      doNotObfuscate,
      compileSdkVersion,
      targetSdkVersion,
      buildToolsVersion,
      legacyNdkVersion,
      primaryNdkVersion,
      nativeLibraryVersion,
      leveldbVersion,
      emojiVersion,

      telegramApiId,
      telegramApiHash,
      safetyNetToken,
      appDownloadUrl,
      googlePlayUrl,
      galaxyStoreUrl,
      huaweiAppGalleryUrl,
      amazonAppStoreUrl,

      pullRequests,

      outputFileNamePrefix,
      creationDateMillis,

      keystore
    )
    project.extra.set("config", config)
  }
}