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
import org.gradle.api.artifacts.dsl.DependencyHandler
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
  dependency: Provider<MinimalExternalModuleDependency>,
  dependencyConfiguration: Action<ExternalModuleDependency>? = null
) =
  this.flavorImplementation(null, dependency, dependency, dependency, dependencyConfiguration)

fun DependencyHandlerScope.lollipopImplementation(
  dependency: Provider<MinimalExternalModuleDependency>,
  dependencyConfiguration: Action<ExternalModuleDependency>? = null
) =
  this.flavorImplementation("lollipop", dependency, dependencyConfiguration)

fun DependencyHandlerScope.sinceMarshmallowImplementation(
  dependency: Provider<MinimalExternalModuleDependency>,
  dependencyConfiguration: Action<ExternalModuleDependency>? = null
) =
  this.flavorImplementation(null, null, dependency, dependency, dependencyConfiguration)

fun DependencyHandlerScope.preMarshmallowImplementation(
  dependency: Provider<MinimalExternalModuleDependency>,
  dependencyConfiguration: Action<ExternalModuleDependency>? = null
) =
  this.flavorImplementation(dependency, dependency, null, null, dependencyConfiguration)

fun DependencyHandlerScope.postMarshmallowImplementation(
  dependency: Provider<MinimalExternalModuleDependency>,
  dependencyConfiguration: Action<ExternalModuleDependency>? = null
) =
  this.flavorImplementation(null, null, null, dependency, dependencyConfiguration)

fun DependencyHandler.postMarshmallowBaselineProfile(
  dependencyNotation: Any
) =
  this.flavorBaselineProfile(null, null, null, dependencyNotation)

fun findExtraFolders(variant: SdkVariant): Set<String> =
  mutableSetOf<String>().apply {
    if (variant.minSdk >= 21) {
      this += "sinceLollipop"
    }
    if (variant.minSdk < 23) {
      this += "preMarshmallow"
    }
    if (variant.minSdk >= 23) {
      this += "sinceMarshmallow"
    }
    this += "only${variant.flavor.replaceFirstChar { it.uppercase() }}"
  }.toSet()

fun <T> selectFlavor(
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
  postLegacy: Provider<MinimalExternalModuleDependency>?,
  dependencyConfiguration: Action<ExternalModuleDependency>? = null
) =
  this.flavorImplementation(
    legacy,
    postLegacy,
    postLegacy,
    postLegacy,
    dependencyConfiguration
  )

fun DependencyHandlerScope.flavorImplementation(
  legacy: Provider<MinimalExternalModuleDependency>?,
  lollipop: Provider<MinimalExternalModuleDependency>?,
  postLollipop: Provider<MinimalExternalModuleDependency>?,
  dependencyConfiguration: Action<ExternalModuleDependency>? = null
) =
  this.flavorImplementation(
    legacy,
    lollipop,
    postLollipop,
    postLollipop,
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
    val library = selectFlavor(
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

private fun DependencyHandler.flavorBaselineProfile(
  flavor: String,
  dependencyNotation: Any?
) =
  dependencyNotation?.let {
    Abi.VARIANTS.forEach { (_, abiVariant) ->
      add("${flavor}${abiVariant.flavor.uppercaseFirstChar()}ReleaseBaselineProfile", it)
    }
  }

fun DependencyHandler.flavorBaselineProfile(
  legacy: Any?,
  lollipop: Any?,
  marshmallow: Any?,
  latest: Any?
) {
  Sdk.VARIANTS.values.forEach { sdkVariant ->
    val project = selectFlavor(
      sdkVariant,
      legacy,
      lollipop,
      marshmallow,
      latest
    )
    flavorBaselineProfile(sdkVariant.flavor, project)
  }
}

fun findHostAbi(): String =
  if (System.getProperty("os.arch") in listOf("aarch64", "arm64")) {
    "arm64"
  } else {
    "x64"
  }