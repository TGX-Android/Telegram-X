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
import org.gradle.api.Action
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.DependencyHandlerScope
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

fun DependencyHandlerScope.postLegacyImplementation(
  dependency: Provider<MinimalExternalModuleDependency>,
  dependencyConfiguration: Action<ExternalModuleDependency>? = null
) =
  this.flavorImplementation(null, dependency, dependency, dependency, dependencyConfiguration)

fun DependencyHandlerScope.lollipopImplementation(
  dependency: Provider<MinimalExternalModuleDependency>,
  dependencyConfiguration: Action<ExternalModuleDependency>? = null
) =
  this.flavorImplementation("lollipop", dependency, dependencyConfiguration)

fun DependencyHandlerScope.postLollipopImplementation(
  dependency: Provider<MinimalExternalModuleDependency>,
  dependencyConfiguration: Action<ExternalModuleDependency>? = null
) =
  this.flavorImplementation(null, null, dependency, dependency, dependencyConfiguration)

fun DependencyHandlerScope.preMarshmallowImplementation(
  dependency: Provider<MinimalExternalModuleDependency>,
  dependencyConfiguration: Action<ExternalModuleDependency>? = null
) =
  this.flavorImplementation(dependency, dependency, null, null, dependencyConfiguration)

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

fun selectImplementation(
  variant: SdkVariant,
  legacy: Provider<MinimalExternalModuleDependency>?,
  lollipop: Provider<MinimalExternalModuleDependency>?,
  marshmallow: Provider<MinimalExternalModuleDependency>?,
  latest: Provider<MinimalExternalModuleDependency>?
): Provider<MinimalExternalModuleDependency>? =
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
    val library = selectImplementation(
      sdkVariant,
      legacy,
      lollipop,
      marshmallow,
      latest
    )
    flavorImplementation(sdkVariant.flavor, library, dependencyConfiguration)
  }
}