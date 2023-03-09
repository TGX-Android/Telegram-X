/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
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
import com.android.build.gradle.BaseExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

open class CMakePlugin : Plugin<Project> {
  override fun apply (project: Project) {
    val androidExt = project.extensions.getByName("android")

    if (androidExt is BaseExtension) {
      androidExt.apply {
        externalNativeBuild {
          cmake {
            path("jni/CMakeLists.txt")
          }
        }
        defaultConfig {
          externalNativeBuild {
            cmake {
              arguments(
                "-DANDROID_STL=c++_shared",
                "-DANDROID_PLATFORM=android-${Config.MIN_SDK_VERSION}",
                "-DCMAKE_BUILD_WITH_INSTALL_RPATH=ON",
                "-DCMAKE_SKIP_RPATH=ON",
                "-DCMAKE_C_VISIBILITY_PRESET=hidden",
                "-DCMAKE_CXX_VISIBILITY_PRESET=hidden",
                "-DCMAKE_BUILD_PARALLEL_LEVEL=${Runtime.getRuntime().availableProcessors()}"
              )
            }
          }
        }
      }
    }
  }
}