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

  private var _sampleProperties: Properties? = null
  private val sampleProperties: Properties
    get() {
      if (_sampleProperties == null) {
        _sampleProperties = loadProperties("local.properties.sample")
      }
      return _sampleProperties!!
    }

  private fun propertyOrSample (key: String): String {
    return properties.getProperty(key, null) ?: sampleProperties.getOrThrow(key)
  }

  fun applicationId (): String = propertyOrSample("app.id")
  fun applicationName (): String = propertyOrSample("app.name")
}