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

import Abi
import AbiVariant
import Sdk
import SdkVariant
import com.android.build.api.dsl.BaseFlavor
import com.android.build.api.dsl.VariantDimension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.TestAndroidComponentsExtension
import org.gradle.api.Action
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.DependencyHandlerScope
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
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
  dependency: Provider<MinimalExternalModuleDependency>,
  dependencyConfiguration: Action<ExternalModuleDependency>? = null
) =
  this.flavorImplementation("legacy", dependency, dependencyConfiguration)

fun DependencyHandlerScope.sinceLollipopImplementation(
  sinceLollipop: Provider<MinimalExternalModuleDependency>,
  sinceMarshmallow: Provider<MinimalExternalModuleDependency>? = null,
  dependencyConfiguration: Action<ExternalModuleDependency>? = null
) =
  this.flavorImplementation(null,
    sinceLollipop,
    sinceMarshmallow ?: sinceLollipop,
    sinceMarshmallow ?: sinceLollipop,
    dependencyConfiguration
  )

fun DependencyHandlerScope.lollipopImplementation(
  dependency: Provider<MinimalExternalModuleDependency>,
  dependencyConfiguration: Action<ExternalModuleDependency>? = null
) =
  this.flavorImplementation("lollipop", dependency, dependencyConfiguration)

fun DependencyHandlerScope.sinceMarshmallowImplementation(
  sinceMarshmallow: Provider<MinimalExternalModuleDependency>,
  sinceNougat: Provider<MinimalExternalModuleDependency>? = null,
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
  legacyAndLollipop: Provider<MinimalExternalModuleDependency>,
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
  sinceNougat: Provider<MinimalExternalModuleDependency>,
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
    if (variant.minSdk >= 21) {
      this += "sinceLollipop"
    }
    if (variant.minSdk < 23) {
      this += "preMarshmallow"
    }
    if (variant.minSdk >= 23 || variant.isLatest) {
      this += "sinceMarshmallow"
    }
    if (variant.minSdk >= 26 || variant.isLatest) {
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
    "latest" -> latest
    else -> error(variant.flavor)
  }

fun <T> selectAbiFlavor(
  variant: AbiVariant,
  universal: T,
  arm32: T,
  arm64: T,
  x86: T,
  x64: T,
  lab: T
): T =
  when (variant.flavor) {
    "universal" -> universal
    "arm32" -> arm32
    "arm64" -> arm64
    "x86" -> x86
    "x64", "x86_64" -> x64
    "lab" -> lab
    else -> error(variant.flavor)
  }

private fun DependencyHandlerScope.flavorImplementation(
  flavor: String,
  dependency: Provider<MinimalExternalModuleDependency>?,
  dependencyConfiguration: Action<ExternalModuleDependency>? = null
) {
  if (dependency != null) {
    if (dependencyConfiguration != null) {
      "${flavor}Implementation"(dependency) {
        dependencyConfiguration.execute(this)
      }
    } else {
      "${flavor}Implementation"(dependency)
    }
  }
}

fun DependencyHandlerScope.flavorImplementation(
  legacy: Provider<MinimalExternalModuleDependency>?,
  sinceLollipop: Provider<MinimalExternalModuleDependency>?,
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
  legacy: Provider<MinimalExternalModuleDependency>?,
  lollipop: Provider<MinimalExternalModuleDependency>?,
  sinceMarshmallow: Provider<MinimalExternalModuleDependency>?,
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
  legacy: Provider<MinimalExternalModuleDependency>?,
  lollipop: Provider<MinimalExternalModuleDependency>?,
  marshmallow: Provider<MinimalExternalModuleDependency>?,
  latest: Provider<MinimalExternalModuleDependency>?,
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

fun isVariantEnabled(sdkVariant: SdkVariant, abiVariant: AbiVariant, isDebug: Boolean): Boolean =
  sdkVariant.minSdk >= abiVariant.minSdk &&
  !(abiVariant.flavor == "universal" && sdkVariant.flavor == "legacy")

fun ApplicationAndroidComponentsExtension.disableRudimentaryVariants(
  filter: (SdkVariant, AbiVariant) -> Boolean = { _, _ -> true }
) =
  beforeVariants { variantBuilder ->
    val sdkFlavor = variantBuilder.productFlavors.first { it.first == "SDK" }.second
    val sdkVariant = Sdk.VARIANTS.values.first { it.flavor == sdkFlavor }
    val abiFlavor = variantBuilder.productFlavors.first { it.first == "ABI" }.second
    val abiVariant = Abi.VARIANTS.values.first { it.flavor == abiFlavor }
    if (sdkVariant.maxSdk != null) {
      variantBuilder.maxSdk = sdkVariant.maxSdk
    }
    variantBuilder.enable = isVariantEnabled(sdkVariant, abiVariant, variantBuilder.buildType == "debug") && filter(sdkVariant, abiVariant)
  }

fun TestAndroidComponentsExtension.disableRudimentaryVariants(
  filter: (SdkVariant, AbiVariant) -> Boolean = { _, _ -> true }
) =
  beforeVariants { variantBuilder ->
    val sdkFlavor = variantBuilder.productFlavors.first { it.first == "SDK" }.second
    val sdkVariant = Sdk.VARIANTS.values.first { it.flavor == sdkFlavor }
    val abiFlavor = variantBuilder.productFlavors.first { it.first == "ABI" }.second
    val abiVariant = Abi.VARIANTS.values.first { it.flavor == abiFlavor }
    if (sdkVariant.maxSdk != null) {
      variantBuilder.maxSdk = sdkVariant.maxSdk
    }
    variantBuilder.enable = isVariantEnabled(sdkVariant, abiVariant, variantBuilder.buildType == "debug") && filter(sdkVariant, abiVariant)
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

fun String.toAbiFilter(): String = when (this) {
  "arm64" -> "arm64-v8a"
  "arm32" -> "armeabi-v7a"
  "x86" -> "x86"
  "x64" -> "x86_64"
  else -> error("Unknown abi variant: $this")
}

fun String.toAbiVariant(): String = when (this) {
  "arm64-v8a" -> "arm64"
  "armeabi-v7a" -> "arm32"
  "x86" -> "x86"
  "x86_64" -> "x64"
  else -> error("Unknown abi filter: $this")
}