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