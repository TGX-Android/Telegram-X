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