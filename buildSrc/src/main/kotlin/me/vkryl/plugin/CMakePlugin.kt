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
import com.android.build.gradle.BaseExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

open class CMakePlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.extensions.getByType<BaseExtension>().apply {
      externalNativeBuild {
        cmake {
          path("jni/CMakeLists.txt")
        }
      }
      buildTypes {
        named("debug") {
          externalNativeBuild {
            cmake {
              val flags = arrayOf(
                "-w",
                "-Werror=return-type",
                "-ferror-limit=0",
                "-fno-exceptions",

                "-O2",
                "-fno-omit-frame-pointer",
              )
              arguments(
                "-DANDROID_STL=c++_shared",
                "-DANDROID_PLATFORM=android-${Config.MIN_SDK_VERSION}",
                "-DCMAKE_BUILD_WITH_INSTALL_RPATH=ON",
                "-DCMAKE_SKIP_RPATH=ON",
                "-DCMAKE_C_VISIBILITY_PRESET=hidden",
                "-DCMAKE_CXX_VISIBILITY_PRESET=hidden",
                "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,--gc-sections,--icf=safe -Wl,--build-id=sha1",
                "-DCMAKE_C_FLAGS=-D_LARGEFILE_SOURCE=1 ${flags.joinToString(" ")}",
                "-DCMAKE_CXX_FLAGS=-std=c++17 ${flags.joinToString(" ")}",
              )
            }
          }
        }

        named("release") {
          externalNativeBuild {
            cmake {
              val flags = listOf(
                "-w",
                "-Werror=return-type",
                "-ferror-limit=0",
                "-fno-exceptions",

                "-O3",
                "-finline-functions"
              )

              arguments(
                "-DANDROID_STL=c++_shared",
                "-DANDROID_PLATFORM=android-${Config.MIN_SDK_VERSION}",
                "-DCMAKE_BUILD_WITH_INSTALL_RPATH=ON",
                "-DCMAKE_SKIP_RPATH=ON",
                "-DCMAKE_C_VISIBILITY_PRESET=hidden",
                "-DCMAKE_CXX_VISIBILITY_PRESET=hidden",
                "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,--gc-sections,--icf=safe -Wl,--build-id=sha1",
                "-DCMAKE_C_FLAGS=-D_LARGEFILE_SOURCE=1 ${flags.joinToString(" ")}",
                "-DCMAKE_CXX_FLAGS=-std=c++17 ${flags.joinToString(" ")}"
              )
            }
          }
        }
      }
    }
  }
}