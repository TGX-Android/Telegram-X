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
package tgx.gradle

import Sdk
import SdkVariant
import com.android.build.api.dsl.BaseFlavor
import com.android.build.api.dsl.VariantDimension
import com.android.build.api.variant.*
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderConvertible
import org.gradle.kotlin.dsl.DependencyHandlerScope
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import org.gradle.util.internal.VersionNumber
import tgx.gradle.task.wrapInDoubleQuotes

fun BaseFlavor.buildConfigInt (name: String, value: Int) =
  this.buildConfigField("int", name, value.toString())
fun BaseFlavor.buildConfigLong (name: String, value: Long) =
  this.buildConfigField("long", name, value.toString())
fun BaseFlavor.buildConfigBool (name: String, value: Boolean) =
  this.buildConfigField("boolean", name, value.toString())
fun BaseFlavor.buildConfigString (name: String, value: String?) =
  this.buildConfigField("String", name, value?.wrapInDoubleQuotes() ?: "null")
fun VariantDimension.buildConfigInt (name: String, value: Int) =
  this.buildConfigField("int", name, value.toString())
fun VariantDimension.buildConfigLong (name: String, value: Long) =
  this.buildConfigField("long", name, value.toString())
fun VariantDimension.buildConfigString (name: String, value: String?) =
  this.buildConfigField("String", name, value?.wrapInDoubleQuotes() ?: "null")

fun DependencyHandlerScope.legacyImplementation(
  dependency: Any,
  dependencyConfiguration: Action<ExternalModuleDependency>? = null
) =
  this.flavorImplementation("legacy", dependency, dependencyConfiguration)

fun DependencyHandlerScope.sinceLollipopImplementation(
  sinceLollipop: Any,
  sinceMarshmallow: Any? = null,
  dependencyConfiguration: Action<ExternalModuleDependency>? = null
) =
  this.flavorImplementation(null,
    sinceLollipop,
    sinceMarshmallow ?: sinceLollipop,
    sinceMarshmallow ?: sinceLollipop,
    dependencyConfiguration
  )

fun DependencyHandlerScope.lollipopImplementation(
  dependency: Any,
  dependencyConfiguration: Action<ExternalModuleDependency>? = null
) =
  this.flavorImplementation("lollipop", dependency, dependencyConfiguration)

fun DependencyHandlerScope.sinceMarshmallowImplementation(
  sinceMarshmallow: Any,
  sinceNougat: Any? = null,
  dependencyConfiguration: Action<ExternalModuleDependency>? = null
) =
  this.flavorImplementation(
    null,
    null,
    sinceMarshmallow,
    sinceNougat ?: sinceMarshmallow,
    dependencyConfiguration
  )

fun DependencyHandlerScope.preMarshmallowImplementation(
  legacyAndLollipop: Any,
  dependencyConfiguration: Action<ExternalModuleDependency>? = null
) =
  this.flavorImplementation(
    legacyAndLollipop,
    legacyAndLollipop,
    null,
    null,
    dependencyConfiguration
  )

fun DependencyHandlerScope.sinceNougatImplementation(
  sinceNougat: Any,
  dependencyConfiguration: Action<ExternalModuleDependency>? = null
) =
  this.flavorImplementation(
    null,
    null,
    null,
    sinceNougat,
    dependencyConfiguration
  )

fun findExtraFolders(variant: SdkVariant): Set<String> =
  mutableSetOf<String>().apply {
    val isLatest = (variant.maxSdk ?: Int.MAX_VALUE) >= 26
    if (variant.minSdk >= 21 || isLatest) {
      this += "sinceLollipop"
    }
    if (variant.minSdk < 23) {
      this += "preMarshmallow"
    }
    if (variant.minSdk >= 23 || isLatest) {
      this += "sinceMarshmallow"
    }
    if (variant.minSdk >= 26 || isLatest) {
      this += "sinceOreo"
    }
    this += "only${variant.flavor.uppercaseFirstChar()}"
  }.toSet()

fun <T> selectApiFlavor(
  variant: SdkVariant,
  legacy: T,
  lollipop: T,
  marshmallow: T,
  latest: T
): T =
  when (variant.flavor) {
    "legacy" -> legacy
    "lollipop" -> lollipop
    "marshmallow" -> marshmallow
    "tens",
    "qrs",
    "latest" -> latest
    else -> error(variant.flavor)
  }

private fun DependencyHandlerScope.flavorImplementation(
  flavor: String,
  dependency: Any?,
  dependencyConfiguration: Action<ExternalModuleDependency>? = null
) {
  if (dependency != null) {
    if (dependencyConfiguration != null) {
      when (dependency) {
        is String ->
          "${flavor}Implementation"(dependency) {
            dependencyConfiguration.execute(this)
          }
        is Provider<*> ->
          "${flavor}Implementation"(dependency) {
            dependencyConfiguration.execute(this)
          }
        is ProviderConvertible<*> ->
          "${flavor}Implementation"(dependency) {
            dependencyConfiguration.execute(this)
          }
        else -> {
          error("Unknown type: $dependency of ${dependency.javaClass}")
        }
      }
    } else {
      "${flavor}Implementation"(dependency)
    }
  }
}

fun DependencyHandlerScope.flavorImplementation(
  legacy: Any?,
  sinceLollipop: Any?,
  dependencyConfiguration: Action<ExternalModuleDependency>? = null
) =
  this.flavorImplementation(
    legacy,
    sinceLollipop,
    sinceLollipop,
    sinceLollipop,
    dependencyConfiguration
  )

fun DependencyHandlerScope.flavorImplementation(
  legacy: Any?,
  lollipop: Any?,
  sinceMarshmallow: Any?,
  dependencyConfiguration: Action<ExternalModuleDependency>? = null
) =
  this.flavorImplementation(
    legacy,
    lollipop,
    sinceMarshmallow,
    sinceMarshmallow,
    dependencyConfiguration
  )

fun DependencyHandlerScope.flavorImplementation(
  legacy: Any?,
  lollipop: Any?,
  marshmallow: Any?,
  latest: Any?,
  dependencyConfiguration: Action<ExternalModuleDependency>? = null
) {
  Sdk.VARIANTS.values.forEach { sdkVariant ->
    val library = selectApiFlavor(
      sdkVariant,
      legacy,
      lollipop,
      marshmallow,
      latest
    )
    flavorImplementation(sdkVariant.flavor, library, dependencyConfiguration)
  }
}

fun isVariantEnabled(sdkVariant: SdkVariant, isDebug: Boolean): Boolean =
  true

fun ApplicationAndroidComponentsExtension.disableRudimentaryVariants(
  filter: (SdkVariant) -> Boolean = { _ -> true }
) =
  beforeVariants { variantBuilder ->
    val sdkFlavor = variantBuilder.productFlavors.first { it.first == "SDK" }.second
    val sdkVariant = Sdk.VARIANTS.values.first { it.flavor == sdkFlavor }
    val isDebug = variantBuilder.buildType == "debug"
    if (sdkVariant.maxSdk != null && !isDebug) {
      variantBuilder.maxSdk = sdkVariant.maxSdk
    }
    variantBuilder.enable = isVariantEnabled(sdkVariant, isDebug) && filter(sdkVariant)
  }

fun TestAndroidComponentsExtension.disableRudimentaryVariants(
  filter: (SdkVariant) -> Boolean = { _ -> true }
) =
  beforeVariants { variantBuilder ->
    val sdkFlavor = variantBuilder.productFlavors.first { it.first == "SDK" }.second
    val sdkVariant = Sdk.VARIANTS.values.first { it.flavor == sdkFlavor }
    val isDebug = variantBuilder.buildType == "debug"
    if (sdkVariant.maxSdk != null && !isDebug) {
      variantBuilder.maxSdk = sdkVariant.maxSdk
    }
    variantBuilder.enable = isVariantEnabled(sdkVariant, isDebug) && filter(sdkVariant)
  }

fun findHostAbi(): String =
  if (System.getProperty("os.arch") in listOf("aarch64", "arm64")) {
    "arm64"
  } else {
    "x64"
  }

fun findHostTag(): String {
  val os = System.getProperty("os.name")
  return when {
    os.startsWith("mac", ignoreCase = true) ->
      "darwin-x86_64"
    os.startsWith("Linux", ignoreCase = true) ->
      "linux-x86_64"
    os.startsWith("Windows", ignoreCase = true) ->
      "windows-x86_64"
    else ->
      error("Unknown system: $os")
  }
}

fun VariantOutput.findAbiFilters(ifEmpty: List<String>): List<String> =
  this.filters.filter { filter ->
    filter.filterType == FilterConfiguration.FilterType.ABI
  }.map {
    it.identifier
  }.ifEmpty {
    ifEmpty
  }

fun ApplicationVariant.findAbiFilters(ifEmpty: List<String>): List<String> =
  this.outputs.flatMap { output ->
    output.findAbiFilters(ifEmpty)
  }.distinct()

fun String.ndkVersionMajor(): Int =
  VersionNumber.parse(this).major

fun String.ndkVersionToMinSdk(): Int =
  this.ndkVersionMajor().let { major ->
    when {
      major >= 27 -> 21
      major >= 24 -> 19
      major >= 23 -> 16
      else -> error("Unsupported NDK version: $this")
    }
  }

fun Project.gradleFlag(name: String, defaultValue: Boolean = false): Provider<Boolean> =
  providers.gradleProperty(name).map {
    it.toBoolean()
  }.orElse(defaultValue)

fun Project.gradleOption(name: String, defaultValue: String = ""): Provider<String> =
  providers.gradleProperty(name).orElse(defaultValue)