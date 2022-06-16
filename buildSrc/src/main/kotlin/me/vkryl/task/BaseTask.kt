/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package me.vkryl.task

import getOrThrow
import loadProperties
import org.gradle.api.DefaultTask
import java.util.*

abstract class BaseTask : DefaultTask() {
  private var _properties: Properties? = null
  private val properties: Properties
    get() {
      if (_properties == null) {
        _properties = loadProperties()
      }
      return _properties!!
    }

  fun applicationId (): String = properties.getOrThrow("app.id")
  fun applicationName (): String = properties.getOrThrow("app.name")
}