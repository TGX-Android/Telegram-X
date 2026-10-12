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
package tgx.gradle.task

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import tgx.gradle.validateDir
import kotlin.contracts.ExperimentalContracts

@CacheableTask
abstract class GenerateExceptionsTask : DefaultTask() {
  @get:Input
  abstract val applicationVersion: Property<Int>

  @get:OutputDirectory
  abstract val kotlinOutputDir: DirectoryProperty

  @ExperimentalContracts
  @TaskAction
  fun updateExceptions () {
    val appVersion = applicationVersion.get()
    val kotlin = validateDir(kotlinOutputDir.get().asFile)
    writeToFile(
      kotlin.resolve("org/drinkmore/ClientException.kt")
    ) { kt ->
      kt.append("""
        @file:MadeByGenerator("${className()}")
        
        package org.drinkmore
        
        import androidx.annotation.Keep
        import org.thunderdog.challegram.BuildConfig
        import me.vkryl.annotation.MadeByGenerator
        
        open class ClientException_${appVersion}(message: String?) : RuntimeException(message)
        
        open class ClientException(message: String?) : ClientException_${appVersion}(message) {
          companion object {
            @JvmStatic fun throwTestError (error: Throwable): Nothing =
              throw ClientException(error.message)
            @JvmStatic fun throwAssertionError (error: Throwable): Nothing =
              throw ClientException(error.message)
          }
        }
        
        class DatabaseError(message: String?) : ClientException(
          message + ", versionCode: " + BuildConfig.ORIGINAL_VERSION_CODE
        )
        
        class TdlibLaunchError(message: String?) : ClientException(message)
        class TdlibLostPromiseError(message: String?) : ClientException(message)
      """.trimIndent())
    }
  }
}