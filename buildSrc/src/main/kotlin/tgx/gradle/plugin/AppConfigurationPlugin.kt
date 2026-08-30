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
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.of
import tgx.gradle.source.AppConfigurationSource

abstract class AppConfigurationExtension {
  abstract val config: Property<ApplicationConfig>
  abstract val generateBaselineProfile: Property<Boolean>
  abstract val useLegacyNdk: Property<Boolean>
}

@Suppress("UnstableApiUsage")
open class AppConfigurationPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val ext = project.extensions.create<AppConfigurationExtension>("tgxConfig")

    ext.config.set(project.providers.of(AppConfigurationSource::class) {
      parameters.version.set(
        project.isolated.rootProject.projectDirectory.file("version.properties")
      )
      parameters.properties.set(
        project.isolated.rootProject.projectDirectory.file("local.properties")
      )
      parameters.defaults.set(
        project.isolated.rootProject.projectDirectory.file("local.properties.sample")
      )
    })

    ext.generateBaselineProfile.set(
      project.providers.gradleProperty("generateBaselineProfile").map {
        it.toBoolean()
      }.orElse(false)
    )

    ext.useLegacyNdk.set(
      project.providers.gradleProperty("useLegacyNdk").map {
        it.toBoolean()
      }.orElse(false)
    )
  }
}