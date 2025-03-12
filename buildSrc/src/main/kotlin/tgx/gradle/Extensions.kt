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

import com.android.build.api.dsl.ApplicationBaseFlavor
import com.android.build.api.dsl.VariantDimension

fun ApplicationBaseFlavor.buildConfigInt (name: String, value: Int) =
  this.buildConfigField("int", name, value.toString())
fun ApplicationBaseFlavor.buildConfigLong (name: String, value: Long) =
  this.buildConfigField("long", name, value.toString())
fun ApplicationBaseFlavor.buildConfigBool (name: String, value: Boolean) =
  this.buildConfigField("boolean", name, value.toString())
fun ApplicationBaseFlavor.buildConfigString (name: String, value: String?) =
  this.buildConfigField("String", name, if (value != null) {
    "\"$value\""
  } else {
    "null"
  })
fun VariantDimension.buildConfigInt (name: String, value: Int) =
  this.buildConfigField("int", name, value.toString())
fun VariantDimension.buildConfigLong (name: String, value: Long) =
  this.buildConfigField("long", name, value.toString())
fun VariantDimension.buildConfigString (name: String, value: String?) =
  this.buildConfigField("String", name, if (value != null) {
    "\"$value\""
  } else {
    "null"
  })