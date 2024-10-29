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

import org.gradle.api.tasks.StopExecutionException
import java.io.File
import java.util.*

fun fatal(message: String): Nothing =
  throw StopExecutionException(message)

fun loadProperties (path: String = "local.properties"): Properties {
  val file = File(path)
  if (!file.canRead())
    fatal("Cannot read ${file.absolutePath}")
  if (file.isDirectory)
    fatal("Is a directory: ${file.absolutePath}")
  val properties = Properties()
  file.bufferedReader().use {
    properties.load(it)
  }
  return properties
}

fun monthYears (now: Calendar, then: Calendar): String {
  var years = now.get(Calendar.YEAR) - then.get(Calendar.YEAR)
  var months = if (now.get(Calendar.MONTH) < then.get(Calendar.MONTH) ||
    (now.get(Calendar.MONTH) == then.get(Calendar.MONTH) && now.get(Calendar.DAY_OF_MONTH) < then.get(Calendar.DAY_OF_MONTH))) {
    years--
    Calendar.DECEMBER - then.get(Calendar.MONTH) + now.get(Calendar.MONTH) + 1
  } else {
    now.get(Calendar.MONTH) - then.get(Calendar.MONTH)
  }
  if (now.get(Calendar.DAY_OF_MONTH) < then.get(Calendar.DAY_OF_MONTH)) {
    months--
  }
  return "${years}.${months}"
}

fun Properties.getIntOrThrow (key: String): Int {
  val value = this.getOrThrow(key)
  return try {
    value.toInt()
  } catch (e: NumberFormatException) {
    fatal("$key is set, but not a number")
  }
}

fun Properties.getLongOrThrow (key: String): Long {
  val value = this.getOrThrow(key)
  return try {
    value.toLong()
  } catch (e: NumberFormatException) {
    fatal("$key is set, but not a number")
  }
}

fun Properties.getOrThrow (key: String): String {
  return this[key]?.let {
    val res = it.toString()
    if (res.isEmpty())
      fatal("$key is set, but empty")
    res
  } ?: fatal("$key is not set, available: ${keys.joinToString(", ")}")
}