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

import App
import groovy.util.Node
import groovy.util.NodeList
import groovy.xml.XmlParser
import okhttp3.internal.toHexString
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.*
import java.util.zip.CRC32
import kotlin.contracts.ExperimentalContracts

class Theme (file: File) {
  enum class Section {
    UNKNOWN, SETTINGS, PROPERTIES, COLORS
  }

  var id: Int = 0
  var name: String = ""
  var creationTime: Long = 0
  var parentTheme: Int = 0
  var bubbleTheme: Int = 0
  var isTinted: Boolean = false

  val namesByProperty = mutableMapOf<Float, MutableSet<String>>()
  val namesByColor = mutableMapOf<String, MutableSet<String>>()

  val propertyByName = mutableMapOf<String, Float>()
  val colorByName = mutableMapOf<String, String>()

  val className: String
    get() = this.name.replace(" ", "")
  val idName: String
    get() = this.name.replace(" ", "").camelCaseToUpperCase()

  private fun parentTheme (themes: MutableMap<Int, Theme>, sourceThemeId: Int): Theme? {
    return themes[parentTheme] ?: (if (id != sourceThemeId) themes[sourceThemeId] else null)
  }

  fun effectiveColor (name: String, themes: MutableMap<Int, Theme>, sourceThemeId: Int): String? {
    var theme: Theme? = this
    while (theme != null) {
      val color = theme.colorByName[name]
      if (color != null)
        return color
      theme = theme.parentTheme(themes, sourceThemeId)
    }
    return null
  }

  fun effectiveProperty (name: String, themes: MutableMap<Int, Theme>, sourceThemeId: Int): Float? {
    var theme: Theme? = this
    while (theme != null) {
      val color = theme.propertyByName[name]
      if (color != null)
        return color
      theme = theme.parentTheme(themes, sourceThemeId)
    }
    return null
  }

  init {
    var section = Section.UNKNOWN
    file.bufferedReader().use { reader ->
      while (true) {
        var line = reader.readLine() ?: break
        when (line) {
          "!" -> section = Section.SETTINGS
          "@" -> section = Section.PROPERTIES
          "#" -> section = Section.COLORS
          else -> if (section != Section.UNKNOWN) {
            var i = line.indexOf("//")
            if (i != -1) {
              line = line.substring(0, i).trim()
            }
            i = line.indexOf(":")
            if (i != -1) {
              val stringValue = line.substring(i + 1).trim()
              val names = line.substring(0, i).split(Regex("\\s*,\\s*"))
              if (names.isNotEmpty()) {
                for (name in names) {
                  when (section) {
                    Section.SETTINGS -> when (name) {
                      "id" -> this.id = stringValue.toInt()
                      "name" -> this.name = stringValue.unwrapDoubleQuotes()
                      "time" -> this.creationTime = stringValue.toLong()
                      "tinted" -> this.isTinted = stringValue.toBoolean()
                      else -> error("Unknown theme setting: $name")
                    }
                    Section.PROPERTIES -> {
                      val property = stringValue.toFloat()
                      if (propertyByName.containsKey(name)) {
                        error("Duplicate definition of $name in ${file.name}")
                      }
                      var skip = false
                      when (name) {
                        "parentTheme" -> this.parentTheme = stringValue.toInt()
                        "bubbleOutTheme" -> {
                          this.bubbleTheme = stringValue.toInt()
                          skip = true
                        }
                      }
                      if (!skip) {
                        propertyByName[name] = property
                        if (namesByProperty.containsKey(property)) {
                          namesByProperty[property]!!.add(name)
                        } else {
                          namesByProperty[property] = mutableSetOf(name)
                        }
                      }
                    }
                    Section.COLORS -> {
                      val color = stringValue.normalizeArgbHex()
                      if (colorByName.containsKey(name)) {
                        error("Duplicate definition of $name in ${file.name}")
                      }
                      colorByName[name] = color
                      if (namesByColor.containsKey(color)) {
                        namesByColor[color]!!.add(name)
                      } else {
                        namesByColor[color] = mutableSetOf(name)
                      }
                    }
                    Section.UNKNOWN -> { }
                  }
                }
              }
            }
          }
        }
      }
    }
    if (this.name.isEmpty()) {
      this.name = file.name.substring(0, file.name.length - (App.THEME_EXTENSION.length + 1))
    }
  }
}

open class GenerateResourcesAndThemesTask : BaseTask() {
  @ExperimentalContracts
  @TaskAction
  fun generateResourcesAndThemes () {
    val strings = XmlParser().parse("app/src/main/res/values/strings.xml")
    val themes = XmlParser().parse("app/src/main/other/themes/colors-and-properties.xml")

    val generatedIds = ArrayList<String>()
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

    // Themes

    val themeColors = ArrayList<String>()
    val themeProperties = ArrayList<String>()
    val tintedColors = mutableSetOf<String>()
    val porterDuffColors = mutableSetOf<String>()

    for (item in (((themes["sections"] as NodeList)[0] as Node)["item"]) as NodeList) {
      val name = (item as Node)["@name"].toString()
      generatedIds.add("theme_category_${name}")
    }
    for (item in (((themes["properties"] as NodeList)[0] as Node)["item"]) as NodeList) {
      val name = (item as Node)["@name"].toString()
      // generatedIds.add("theme_property_${name}")
      themeProperties.add(name)
    }
    for (item in (((themes["colors"] as NodeList)[0] as Node)["item"]) as NodeList) {
      val name = (item as Node)["@name"].toString()
      val tinted = item["@tinted"]?.toString() == "true"
      val needsPorterDuff = item["@porterDuff"]?.toString() == "true"
      // generatedIds.add("theme_color_${name}")
      themeColors.add(name)
      if (tinted) {
        tintedColors.add(name)
      }
      if (needsPorterDuff) {
        porterDuffColors.add(name)
      }
    }
    val themeSettingsNode = (themes["settings"] as NodeList)[0] as Node
    val defaultThemeNode = (themeSettingsNode["default-theme"] as NodeList)[0] as Node
    val sourceTheme = ((themeSettingsNode["source-theme"] as NodeList)[0] as Node).toInteger()
    val defaultLightTheme = ((defaultThemeNode["light"] as NodeList)[0] as Node).toInteger()
    val defaultDarkTheme = ((defaultThemeNode["dark"] as NodeList)[0] as Node).toInteger()

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

    // TODO: Use proper output folder for generated code and resources?

    // generated.xml

    writeToFile("app/src/main/res/values/generated.xml") { xml ->
      xml.append("""
        <?xml version="1.0" encoding="utf-8"?>
        <!-- AUTOGENERATED, DO NOT MODIFY -->
        <resources xmlns:tools="http://schemas.android.com/tools" tools:ignore="MissingTranslation">
          <string name="AppName">${applicationName()}</string>
          ${generatedStrings.joinToString("\n          ") { "<string name=\"$it\" />" }}
          ${generatedIds.joinToString("\n          ") { "<item type=\"id\" name=\"$it\" />" }}
        </resources>
      """.trimIndent())
    }

    // LangUtils.kt

    writeToFile("app/src/main/java/org/thunderdog/challegram/core/LangUtils.kt") { kt ->
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

    // Themes

    val themesList = mutableListOf<Theme>()
    val themesMap = mutableMapOf<Int, Theme>()
    val missingMap = mutableMapOf<Int, Set<String>>()
    File("app/src/main/other/themes").listFiles {
      s -> s.name.endsWith(".tgx-theme")
    }?.forEach { themeFile ->
      val theme = Theme(themeFile)

      val missingKeys = if (theme.isTinted) tintedColors.toMutableList() else ArrayList(themeColors)
      missingKeys.removeAll(theme.colorByName.keys.toList())
      if (missingKeys.isNotEmpty()) {
        missingMap[theme.id] = missingKeys.toSet()
      }

      themesMap[theme.id] = theme
      themesList.add(theme)
    }
    themesList.sortBy { it.id }
    var prevTheme: Theme? = null
    for (theme in themesList) {
      if (prevTheme != null && prevTheme.id + 1 != theme.id) {
        error("Missing gap in themes identifiers: ${prevTheme.id} .. ${theme.id}")
      }
      prevTheme = theme
    }

    if (!themesMap.containsKey(defaultLightTheme))
      error("Default light theme #${defaultLightTheme} is missing")
    if (!themesMap.containsKey(defaultDarkTheme))
      error("Default dark theme #${defaultDarkTheme} is missing")

    if (missingMap.isNotEmpty()) {
      val warn = StringBuilder()
      val fatal = StringBuilder()
      for (entry in missingMap) {
        val theme = themesMap[entry.key]!!
        val warning = "\"${theme.name}\" theme ${if (theme.isTinted) "(tinted) misses" else "does not define"} ${entry.value.size} color(s): ${entry.value}"
        val b = if (theme.isTinted || theme.parentTheme == 0) fatal else warn
        if (b.isNotEmpty())
          b.append("\n\n")
        b.append(warning)
      }
      if (warn.isNotEmpty())
        logger.info(warn.toString())
      if (fatal.isNotEmpty()) {
        error(fatal.toString())
      }
    }

    // ThemeId.java

    writeToFile("app/src/main/java/org/thunderdog/challegram/theme/ThemeId.java") { java ->
      java.append("""
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
         *
         * File initially created on 19/01/2017
         *
         * AUTOGENERATED. DO NOT MODIFY
         */
        package org.thunderdog.challegram.theme;

        import androidx.annotation.IntDef;
        
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        
        @Retention(RetentionPolicy.SOURCE)
        @IntDef({
          ThemeId.NONE,
          ThemeId.TEMPORARY,
          ThemeId.CUSTOM,
        
          ${themesList.joinToString(",\n          ") { "ThemeId.${it.idName}" }}
        })
        public @interface ThemeId {
          int NONE = 0;
          int TEMPORARY = -1;
          int CUSTOM = -2;
        
          ${themesList.joinToString("\n          ") { "int ${it.idName} = ${it.id};" }}
        
          int ID_MIN = ThemeId.${themesList[0].idName};
          int ID_MAX = ThemeId.${themesList[themesList.size - 1].idName};
          int COUNT = ID_MAX - ID_MIN + 1;
        }
      """.trimIndent())
    }

    // PropertyId.java

    writeToFile("app/src/main/java/org/thunderdog/challegram/theme/PropertyId.java") { java ->
      java.append("""
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
         *
         * File initially created on 04/11/2018
         *
         * AUTOGENERATED. DO NOT MODIFY
         */
        package org.thunderdog.challegram.theme;
        
        import androidx.annotation.IntDef;

        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;

        import me.vkryl.annotation.Autogenerated;
        
        @Autogenerated
        @Retention(RetentionPolicy.SOURCE)
        @IntDef({
          ${themeProperties.joinToString(",\n          ") {
            "PropertyId.${it.camelCaseToUpperCase()}"
          }}
        })
        public @interface PropertyId {
          int
            ${
              themeProperties.mapIndexed { index, propertyId ->
                "${propertyId.camelCaseToUpperCase()} = ${index + 1}"
              }.joinToString(",\n            ", postfix = ";")
            }
        }
      """.trimIndent())
    }

    // ColorId.java

    writeToFile("app/src/main/java/org/thunderdog/challegram/theme/ColorId.java") { java ->
      java.append("""
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
         *
         * File initially created on 19/01/2017
         *
         * AUTOGENERATED. DO NOT MODIFY
         */
        package org.thunderdog.challegram.theme;

        import androidx.annotation.IntDef;

        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;

        import me.vkryl.annotation.Autogenerated;

        @Autogenerated
        @Retention(RetentionPolicy.SOURCE)
        @IntDef({
          ColorId.NONE,
          ${themeColors.joinToString(",\n          ") {
            "ColorId.$it"
          }}
        })
        public @interface ColorId {
          int
            NONE = 0,
            ${
              themeColors.mapIndexed { index, colorId ->
                "$colorId = ${index + 1}"
              }.joinToString(",\n            ", postfix = ";")
            }
        }
      """.trimIndent())
    }

    // ColorIdTinted.java

    writeToFile("app/src/main/java/org/thunderdog/challegram/theme/ColorIdTinted.java") { java ->
      java.append("""
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
         *
         * File initially created on 08/07/2017
         *
         * AUTOGENERATED. DO NOT MODIFY
         */
        package org.thunderdog.challegram.theme;

        import androidx.annotation.IntDef;

        import org.thunderdog.challegram.R;

        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;

        import me.vkryl.annotation.Autogenerated;

        @Autogenerated
        @Retention(RetentionPolicy.SOURCE)
        @IntDef({
          ${tintedColors.joinToString(",\n          ") {
            "ColorId.$it"
          }}
        })
        public @interface ColorIdTinted { }
      """.trimIndent())
    }
    writeToFile("app/src/main/java/org/thunderdog/challegram/theme/PorterDuffColorId.java") { java ->
      java.append("""
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
         *
         * File initially created on 25/12/2020
         *
         * AUTOGENERATED. DO NOT MODIFY
         */
        package org.thunderdog.challegram.theme;

        import androidx.annotation.IntDef;

        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;

        import me.vkryl.annotation.Autogenerated;

        @Autogenerated
        @Retention(RetentionPolicy.SOURCE)
        @IntDef({
          ${porterDuffColors.joinToString(",\n          ") {
            "ColorId.$it"
          }}
        })
        public @interface PorterDuffColorId { }
      """.trimIndent())
    }
    writeToFile("app/src/main/java/org/thunderdog/challegram/tool/PorterDuffPaint.kt") { kt ->
      kt.append("""
        @file:JvmName("PorterDuffPaint")

        package org.thunderdog.challegram.tool
        
        import android.graphics.Paint
        import android.graphics.PorterDuff
        import android.graphics.PorterDuffColorFilter
        import me.vkryl.core.alphaColor
        import org.thunderdog.challegram.core.Lang
        import org.thunderdog.challegram.theme.ColorId
        import org.thunderdog.challegram.theme.PorterDuffColorId
        import org.thunderdog.challegram.theme.Theme
        
        private fun Paint?.changePorterDuff (color: Int): Paint {
          if (this != null && this.color == color)
            return this
          val result = this ?: Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG)
          result.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
          result.color = color
          return result
        }
        
        @JvmName("get") @JvmOverloads fun getPorterDuffPaint (@PorterDuffColorId colorId: Int, alpha: Float = 1.0f): Paint {
          val color = if (alpha != 1.0f) {
            alphaColor(alpha, Theme.getColor(colorId))
          } else {
            Theme.getColor(colorId)
          }
          return when (colorId) {
            ${porterDuffColors.joinToString("\n            ") { "ColorId.$it -> if (paint_$it != null) { paint_$it.changePorterDuff(color) } else { paint_$it = paint_$it.changePorterDuff(color); paint_$it!! }" }}
            else -> throw IllegalArgumentException(Integer.toString(colorId))
          }
        }
        
        ${porterDuffColors.joinToString("\n        ") { "private var paint_$it: Paint? = null" }}
      """.trimIndent())
    }

    // ThemeProperties.kt

    writeToFile("app/src/main/java/org/thunderdog/challegram/theme/ThemeProperties.kt") { kt ->
      kt.append("""
        @file:JvmName("ThemeProperties")
        
        package org.thunderdog.challegram.theme
        
        import me.vkryl.annotation.Autogenerated
        
        @Autogenerated class ThemeProperties {
          ${themeProperties.joinToString("\n          ") { "private var $it: Float? = null" } }
          
          constructor ()
          constructor (copy: ThemeProperties) {
            ${themeProperties.joinToString("\n            ") { "this.$it = copy.$it" }}
          }
        
          fun get (@PropertyId propertyId: Int): Float? = when (propertyId) {
            ${themeProperties.joinToString("\n            ") { "PropertyId.${it.camelCaseToUpperCase()} -> this.$it" }}
            else -> throw Theme.newError(propertyId, "propertyId")
          }
        
          fun set (@PropertyId propertyId: Int, value: Float?) = when (propertyId) {
            ${themeProperties.joinToString("\n            ") { "PropertyId.${it.camelCaseToUpperCase()} -> this.$it = value" }}
            else -> throw Theme.newError(propertyId, "propertyId")
          }
          
          companion object {
            const val COUNT = ${themeProperties.size}
        
            @JvmStatic fun getAll (): Array<Int> = arrayOf(
              ${themeProperties.joinToString(",\n              ") { "PropertyId.${it.camelCaseToUpperCase()}" }}
            )
        
            @JvmStatic fun getMap (): Map<String, Int> = mapOf(
              ${themeProperties.joinToString(",\n              ") { "Pair(\"$it\", PropertyId.${it.camelCaseToUpperCase()})" } }
            )
        
            @JvmStatic fun getName (@PropertyId propertyId: Int): String = when (propertyId) {
              ${themeProperties.joinToString("\n              ") { "PropertyId.${it.camelCaseToUpperCase()} -> \"$it\"" } }
              else -> throw Theme.newError(propertyId, "propertyId")
            }
          }
        }
      """.trimIndent())
    }

    // ThemeColors.kt

    writeToFile("app/src/main/java/org/thunderdog/challegram/theme/ThemeColors.kt") { kt ->
      kt.append("""
        @file:JvmName("ThemeColors")
        
        package org.thunderdog.challegram.theme
        
        import me.vkryl.annotation.Autogenerated
        
        @Autogenerated class ThemeColors {
          ${themeColors.joinToString("\n          ") { "private var $it: Int? = null" }}
        
          constructor ()
          constructor (copy: ThemeColors) {
            ${themeColors.joinToString("\n            ") { "this.$it = copy.$it" }}
          }
        
          fun get (@ColorId colorId: Int): Int? = when (colorId) {
            ${themeColors.joinToString("\n            ") { "ColorId.$it -> this.$it" }}
            ColorId.NONE -> null
            else -> throw Theme.newError(colorId, "colorId")
          }
        
          fun set (@ColorId colorId: Int, value: Int?) = when (colorId) {
            ${themeColors.joinToString("\n            ") { "ColorId.$it -> this.$it = value" }}
            else -> throw Theme.newError(colorId, "colorId")
          }
          
          companion object {
            const val COUNT = ${themeColors.size}
        
            @JvmStatic fun getAll (): MutableSet<Int> = mutableSetOf(
              ${themeColors.joinToString(",\n              ") {
                "ColorId.$it"
              }}
            )
        
            @JvmStatic fun getMap (): Map<String, Int> = mapOf(
              ${themeColors.joinToString(",\n              ") { "Pair(\"$it\", ${
                "ColorId.$it"
              })" }}
            )
        
            @JvmStatic fun getName (@ColorId colorId: Int): String = when (colorId) {
              ${themeColors.joinToString("\n              ") { "${
                "ColorId.$it"
              } -> \"$it\""} }
              else -> throw Theme.newError(colorId, "colorId")
            }
        
            @JvmStatic fun getNames (): Set<String> = setOf(
              ${themeColors.joinToString(",\n              ") { "\"$it\"" }}
            )
          }
        }
      """.trimIndent())
    }

    // XML Resources

    writeToFile("app/src/main/res/xml/authenticator.xml") { xml ->
      xml.append("""
        <?xml version="1.0" encoding="utf-8"?>
        <account-authenticator xmlns:android="http://schemas.android.com/apk/res/android"
          android:accountType="${applicationId()}.sync.account"
          android:icon="@mipmap/app_launcher_round"
          android:smallIcon="@mipmap/app_launcher_round"
          android:label="@string/AppName" />
      """.trimIndent())
    }

    writeToFile("app/src/main/res/xml/syncadapter.xml") { xml ->
      xml.append("""
        <?xml version="1.0" encoding="utf-8"?>
        <sync-adapter xmlns:android="http://schemas.android.com/apk/res/android"
          android:contentAuthority="${applicationId()}.sync.provider"
          android:accountType="${applicationId()}.sync.account"
          android:userVisible="true"
          android:supportsUploading="true"
          android:allowParallelSyncs="false"
          android:isAlwaysSyncable="true" />
      """.trimIndent())
    }

    // Themes

    writeToFile("app/src/main/java/org/thunderdog/challegram/theme/builtin/ThemeBubbleOverride.kt") { kt ->
      kt.append("""
        @file:JvmName("ThemeBubbleOverride")
        
        package org.thunderdog.challegram.theme.builtin
        
        import androidx.annotation.ColorInt
        import me.vkryl.annotation.Autogenerated
        import org.thunderdog.challegram.theme.ColorId
        import org.thunderdog.challegram.theme.ThemeDelegate
        import org.thunderdog.challegram.theme.ThemeId
        import org.thunderdog.challegram.theme.ThemeSet
        
        @Autogenerated open class ThemeBubbleOverride (@ThemeId id: Int, @ThemeId bubbleThemeId: Int) : ThemeDefault(id) {
          private val bubbleTheme: ThemeDelegate = ThemeSet.getBuiltinTheme(bubbleThemeId)
          
          @ColorInt override fun getColor (colorId: Int) = when (colorId) {
            ${themeColors.filter { it.startsWith("bubbleOut_") }.joinToString(",\n            ") {
              "ColorId.$it"
            }} -> bubbleTheme.getColor(colorId)
            else -> super.getColor(colorId)
          }
        }
      """.trimIndent())
    }

    for (entry in themesMap) {
      val theme = entry.value
      val isSource = theme.id == sourceTheme
      val className = if (isSource) "Default" else theme.className
      val parentTheme = if (theme.parentTheme != 0) themesMap[theme.parentTheme] else if (!isSource) themesMap[sourceTheme] else null

      writeToFile("app/src/main/java/org/thunderdog/challegram/theme/builtin/Theme${className}.kt") { writer ->
        writer.append("""
          @file:JvmName("Theme${className}")
          
          package org.thunderdog.challegram.theme.builtin
          
          """.trimIndent())

        if (theme.colorByName.isNotEmpty()) writer.append("\nimport androidx.annotation.ColorInt")

        writer.append("\nimport me.vkryl.annotation.Autogenerated")
        if (theme.colorByName.isNotEmpty()) {
          writer.append("\nimport org.thunderdog.challegram.theme.ColorId")
          if (theme.isTinted) {
            writer.append("\nimport org.thunderdog.challegram.theme.ColorIdTinted")
          }
        }
        writer.append("\nimport org.thunderdog.challegram.theme.ThemeId")
        if (theme.propertyByName.isNotEmpty()) {
          writer.append("\nimport org.thunderdog.challegram.theme.PropertyId")
        }
        writer.append("\n")

        writer.append(when {
          isSource -> """
            import org.thunderdog.challegram.theme.ThemeObject
            
            @Autogenerated open class Theme$className (@ThemeId id: Int = ThemeId.${theme.idName}) : ThemeObject(id)
          """.trimIndent()
          theme.bubbleTheme != 0 -> """
            
            @Autogenerated class Theme$className : ThemeBubbleOverride(ThemeId.${theme.idName}, ThemeId.${themesMap[theme.bubbleTheme]!!.idName})
          """.trimIndent()
          parentTheme!!.id == sourceTheme -> """
            
            @Autogenerated class Theme$className : ThemeDefault(ThemeId.${theme.idName})
          """.trimIndent()
          else -> """
            import org.thunderdog.challegram.theme.ThemeInherited
            
            @Autogenerated class Theme$className : ThemeInherited(ThemeId.${theme.idName}, ThemeId.${themesMap[theme.parentTheme]!!.idName})
          """.trimIndent()
        })

        var open = false

        var first = true
        for (property in theme.namesByProperty) {
          val names: MutableSet<String>
          if (parentTheme != null) {
            names = mutableSetOf()
            for (name in property.value) {
              if (property.key != parentTheme.effectiveProperty(name, themesMap, sourceTheme)) {
                names.add(name)
              }
            }
          } else {
            names = property.value
          }
          if (names.isEmpty())
            continue
          if (first) {
            if (!open) {
              writer.append(" {")
              open = true
            }
            writer.append("\n  override fun getProperty (@PropertyId propertyId: Int): Float = when (propertyId) {\n")
            first = false
          }
          writer.append(names.joinToString(", ", "    ", " ->\n      ") { "PropertyId.${it.camelCaseToUpperCase()}" })
            .append(property.key.toString())
            .append("f\n")
        }
        if (!first) {
          writer.append("    else -> super.getProperty(propertyId)\n")
                .append("  }\n")
        }

        first = true
        for (color in theme.namesByColor) {
          val names: MutableSet<String>
          if (parentTheme != null) {
            names = mutableSetOf()
            for (name in color.value) {
              if (color.key != parentTheme.effectiveColor(name, themesMap, sourceTheme)) {
                if (!themeColors.contains(name))
                  error("Unknown color id ${name} in ${theme.name} theme")
                names.add(name)
              }
            }
          } else {
            names = color.value
          }
          if (names.isEmpty())
            continue
          if (first) {
            if (!open) {
              writer.append(" {")
              open = true
            }
            writer.append("\n  @ColorInt override fun getColor (@${if (theme.isTinted) "ColorIdTinted" else "ColorId"} colorId: Int): Int = when (colorId) {\n")
            first = false
          }
          writer.append(names.joinToString(", ", "    ", " ->\n      ") {
            "ColorId.$it"
          }).append(Integer.parseUnsignedInt(color.key, 16).toString(10)).append(" // 0x${color.key}")
            .append("\n")
        }
        if (!first) {
          writer.append("    else -> super.getColor(colorId)\n")
                .append("  }\n")
        }
        if (open) {
          writer.append("}")
        }
      }
    }

    // TdCompileAssert.kt & TdUnsupported.kt
    // TODO: move file generation to vkryl/td

    val typesRegex = Regex("public abstract static class ([a-zA-Z]+) extends Object \\{[^@]+[^\\n]+\\s+@IntDef\\(\\{([^}]+)")
    val tdApi = File("tdlib/src/main/java/org/drinkless/tdlib/TdApi.java").readText()
    var matchResult: MatchResult?
    val hashCodes = mutableSetOf<String>()
    matchResult = typesRegex.find(tdApi)
    val tdTypes = mutableListOf<Pair<String, Pair<Set<String>, String>>>()
    while (matchResult != null) {
      val (typeName, constructorsRaw) = matchResult.destructured
      val constructors = constructorsRaw.split(",").map {
        it.trim().replace(".CONSTRUCTOR", "").replace(Regex("^${typeName}"), "")
      }.toSortedSet()
      val hashSrc = "${typeName}_${constructors.joinToString("_")}"
      val crc32 = CRC32()
      crc32.update(hashSrc.toByteArray())

      val hashCode = crc32.value.toHexString()
      if (!hashCodes.add(hashCode))
        error("hashCode collision for ${typeName}")
      val stubMethodName = "assert${typeName}_${hashCode}";
      if (stubMethodName.length > 65535) {
        error("Too long (${stubMethodName.length}) method name for type: ${typeName}")
      }
      tdTypes.add(Pair(typeName, Pair(constructors, stubMethodName)))
      matchResult = matchResult.next()
    }
    tdTypes.sortBy { it.first }

    writeToFile("vkryl/td/src/main/kotlin/me/vkryl/td/TdCompileAssert.kt") { kt ->
      kt.append("""
        /*
         * This file is a part of tdlib-utils
         * Copyright © Vyacheslav Krylov
         *
         * Licensed under the Apache License, Version 2.0 (the "License");
         * you may not use this file except in compliance with the License.
         * You may obtain a copy of the License at
         *
         *      http://www.apache.org/licenses/LICENSE-2.0
         *
         * Unless required by applicable law or agreed to in writing, software
         * distributed under the License is distributed on an "AS IS" BASIS,
         * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
         * See the License for the specific language governing permissions and
         * limitations under the License.
         */

        @file:JvmName("Td")
        @file:JvmMultifileClass

        package me.vkryl.td

        import me.vkryl.annotation.Autogenerated
        import org.drinkless.tdlib.TdApi.*
        
        // Сause compilation error whenever new constructor is added to the TDLib type
        // by calling one of the corresponding stub methods below in places, where you expect to support all of them.
        
        ${tdTypes.joinToString("\n        ") {
          "@Autogenerated fun ${it.second.second} (): ${it.first}? = null // ${it.second.first.joinToString(", ")}"
        }}
      """.trimIndent())
    }

    writeToFile("vkryl/td/src/main/kotlin/me/vkryl/td/TdUnsupported.kt") { kt ->
      kt.append("""
        /*
         * This file is a part of tdlib-utils
         * Copyright © Vyacheslav Krylov
         *
         * Licensed under the Apache License, Version 2.0 (the "License");
         * you may not use this file except in compliance with the License.
         * You may obtain a copy of the License at
         *
         *      http://www.apache.org/licenses/LICENSE-2.0
         *
         * Unless required by applicable law or agreed to in writing, software
         * distributed under the License is distributed on an "AS IS" BASIS,
         * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
         * See the License for the specific language governing permissions and
         * limitations under the License.
         */

        @file:JvmName("Td")
        @file:JvmMultifileClass

        package me.vkryl.td

        import me.vkryl.annotation.Autogenerated
        import org.drinkless.tdlib.TdApi.*
        
        // Use throw unsupported(object) to group multiple crashes of the same kind into one report,
        // and access critical places via Find Usages for the methods below when any new constructors are added

        ${tdTypes.joinToString("\n        ") {
          "@Autogenerated fun unsupported (data: ${it.first}): NotImplementedError = NotImplementedError(data.toString())"
        }}
      """.trimIndent())
    }
  }
}