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

import groovy.util.Node
import groovy.util.NodeList
import groovy.xml.XmlParser
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import tgx.gradle.validateDir
import java.util.*
import kotlin.contracts.ExperimentalContracts

@CacheableTask
abstract class GenerateLangFunctions : DefaultTask() {
  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val stringsXml: RegularFileProperty

  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val colorIdJava: RegularFileProperty

  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val propertyIdJava: RegularFileProperty

  @get:OutputDirectory
  abstract val resOutputDir: DirectoryProperty

  @get:OutputDirectory
  abstract val kotlinOutputDir: DirectoryProperty

  @ExperimentalContracts
  @TaskAction
  fun generateResourcesAndThemes () {
    val kotlin = validateDir(kotlinOutputDir.get().asFile)
    val res = validateDir(resOutputDir.get().asFile)

    val strings = XmlParser().parse(stringsXml.get().asFile)

    val generatedStrings = ArrayList<String>()

    val relativeDateForms = sortedSetOf(
      "now",
      "seconds", "minutes", "hours",
      "today", "tomorrow", "yesterday", "weekday", "date",
      "days", "weeks", "months", "years"
    )
    val relativeDateWhiteList = sortedSetOf(
      "format_tomorrow"
    )
    val pluralForms = sortedSetOf(
      "zero", "one", "two", "few", "many", "other"
    )

    // Strings

    val blacklistedKeys = arrayOf(
      "ExplicitDice",
      "PsaInfo"
    )
    val ordinaryKeys = HashSet<String>()
    val pluralKeys = HashSet<String>()
    val foundPlurals = HashMap<String,MutableSet<String>>()
    val foundRelativeDates = HashMap<String,MutableSet<String>>()
    val foundColorDescriptions = HashMap<String,String>()
    val foundPropertyDescriptions = HashMap<String,String>()

    val invalidArgsRegex = Regex("(?<!%)%(?:[^0-9%]|\$)")

    for (string in strings["string"] as NodeList) {
      val name = (string as Node)["@name"].toString()
      val value = string.text()

      invalidArgsRegex.find(value)?.let { matchResult ->
        matchResult.groups.forEach {
          val arg = it?.value
          if (arg != "%") {
            error("Invalid string argument in \"$name\" ($arg):\n$value")
          } else {
            logger.lifecycle("\"$name\" contains '%' without argument")
          }
        }
      }

      if (name.startsWith("c_")) {
        foundColorDescriptions[name.substring("c_".length)] = name
        ordinaryKeys.add(name)
      } else if (name.startsWith("p_")) {
        foundPropertyDescriptions[name.substring("p_".length)] = name
        ordinaryKeys.add(name)
      } else {
        var index = name.lastIndexOf('_')
        if (index == -1) {
          ordinaryKeys.add(name)
          continue
        }
        var suffix = name.substring(index + 1)
        var key = name.substring(0, index)
        if (pluralForms.contains(suffix)) {
          val list = foundPlurals[key]
          if (list != null) {
            list.add(suffix)
          } else {
            foundPlurals[key] = sortedSetOf(suffix)
            pluralKeys.add(key)
          }
          index = key.lastIndexOf('_')
          if (index != -1) {
            suffix = key.substring(index + 1)
            key = key.substring(0, index)
          }
        } else {
          ordinaryKeys.add(name)
        }
        if (relativeDateForms.contains(suffix) && !relativeDateWhiteList.contains(name)) {
          val list = foundRelativeDates[key]
          if (list != null) {
            list.add(suffix)
          } else {
            foundRelativeDates[key] = sortedSetOf(suffix)
          }
        }
      }
    }
    ordinaryKeys.removeAll(blacklistedKeys)
    pluralKeys.removeAll(blacklistedKeys)

    // Check: Relative dates

    val requiredRelativeDateForms = listOf(
      sortedSetOf(
        "now", "seconds", "minutes", "hours", "today", "yesterday", "weekday", "date"
      ), sortedSetOf(
        "now", "seconds", "minutes", "hours", "today", "tomorrow", "weekday", "date"
      ), sortedSetOf(
        "now", "seconds", "minutes", "hours", "today", "yesterday", "days", "weeks", "months", "years"
      ), sortedSetOf(
        "now", "seconds", "minutes", "hours", "today", "tomorrow", "days", "weeks", "months", "years"
      )
    )
    for (entry in foundRelativeDates) {
      var found = entry.value == relativeDateForms
      if (!found) {
        for (requiredRelativeDateForm in requiredRelativeDateForms) {
          if (entry.value == requiredRelativeDateForm) {
            found = true
            break
          }
        }
      }
      if (!found) {
        error("Invalid relative date: ${entry.key}. Defined: ${entry.value}, expected: $requiredRelativeDateForms")
      }
      generatedStrings.add(entry.key)
    }

    // Check: Plurals

    val requiredPluralForms = sortedSetOf("one", "other")
    for (entry in foundPlurals) {
      if (entry.value != requiredPluralForms) {
        error("Invalid plural: ${entry.key}. Defined: ${entry.value}, expected: $requiredPluralForms")
      }
      generatedStrings.add(entry.key)
      for (form in pluralForms) {
        if (!entry.value.contains(form)) {
          generatedStrings.add("${entry.key}_${form}")
        }
      }
    }

    

    // generated.xml

    writeToFile(
      res.resolve("values/generated.xml")
    ) { xml ->
      xml.append("""
        <?xml version="1.0" encoding="utf-8"?>
        <!-- AUTOGENERATED, DO NOT MODIFY -->
        <resources xmlns:tools="http://schemas.android.com/tools" tools:ignore="MissingTranslation">
        
      """.trimIndent())
      generatedStrings.forEach { name ->
        xml.append(
          "  <string name=\"$name\" />\n"
        )
      }
      xml.append("""
        </resources>
      """.trimIndent())
    }

    // LangUtils.kt

    writeToFile(
      kotlin.resolve("org/thunderdog/challegram/core/LangUtils.kt")
    ) { kt ->
      kt.append("@file:JvmName(\"LangUtils\")\n\n")
        .append("package org.thunderdog.challegram.core\n\n")
        .append("import androidx.annotation.StringRes\n")
        .append("import me.vkryl.annotation.Autogenerated\n")
        .append("import org.thunderdog.challegram.theme.ColorId\n")
        .append("import org.thunderdog.challegram.theme.PropertyId\n")

      kt.append("import org.thunderdog.challegram.R\n\n")

      kt.append("""
        @Autogenerated fun getAllKeys (): Array<Array<String>> = arrayOf(
          arrayOf(
            ${ordinaryKeys.toSortedSet().joinToString(",\n            ") { "\"$it\"" }}
          ),
          arrayOf(
            ${pluralKeys.toSortedSet().joinToString(",\n            ") { "\"$it\"" }}
          )
        )
        
        @Autogenerated fun getBlacklistedKeys (): Array<String> = arrayOf(
          ${blacklistedKeys.toSortedSet().joinToString(",\n          ") { "\"$it\"" }}
        )
        
        
      """.trimIndent())

      kt.append("@Autogenerated @StringRes fun getRelativeDateForm (@StringRes res: Int, @Lang.RelativeDateForm form: Int): Int = when (res) {\n")
      for (entry in foundRelativeDates) {
        kt.append("  R.string.").append(entry.key).append(" -> when (form) {\n")
        entry.value.forEach { form ->
          kt.append("    Lang.RelativeDateForm.").append(form.uppercase(Locale.US))
                .append(" -> ")
                .append("R.string.").append(entry.key).append("_").append(form)
                .append("\n")
        }
        kt.append("    else -> throw IllegalArgumentException(form.toString())\n")
          .append("  }\n")
      }
      kt.append("  else -> throw IllegalArgumentException(Lang.getResourceEntryName(res))\n")
        .append("}\n\n")


      kt.append("@Autogenerated @StringRes fun getColorIdDescription (@ColorId colorId: Int): Int = when (colorId) {\n")
      foundColorDescriptions.keys.forEach { key ->
        kt.append("  ColorId.")
        kt.append(key).append(" -> R.string.c_").append(key).append("\n")
      }
      kt.append("  else -> 0\n")
         .append("}\n\n")

      kt.append("@Autogenerated @StringRes fun getPropertyIdDescription (@PropertyId propertyId: Int): Int = when (propertyId) {\n")
      foundPropertyDescriptions.keys.forEach { key ->
        kt.append("  PropertyId.${key.camelCaseToUpperCase()}")
          .append(" -> R.string.p_").append(key)
          .append("\n")
      }
      kt.append("  else -> 0\n")
        .append("}\n\n")

      kt.append("@Autogenerated @StringRes fun getPluralForm (@StringRes res: Int, @Lang.PluralForm form: Int): Int = when (res) {\n")
      for (entry in foundPlurals) {
        kt.append("  R.string.").append(entry.key)
              .append(" -> when (form) {\n")
        pluralForms.forEach { form ->
          kt.append("    Lang.PluralForm.").append(form.uppercase(Locale.US))
                .append(" -> ")
                .append("R.string.").append(entry.key).append("_").append(form).append("\n")
        }
        kt.append("    else -> throw IllegalArgumentException(form.toString())\n")
        kt.append("  }\n")
      }
      kt.append("  else -> throw IllegalArgumentException(Lang.getResourceEntryName(res))\n")
        .append("}\n")
    }
  }
}