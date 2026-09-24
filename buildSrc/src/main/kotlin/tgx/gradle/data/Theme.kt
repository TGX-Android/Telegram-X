package tgx.gradle.data

import App
import tgx.gradle.task.camelCaseToUpperCase
import tgx.gradle.task.normalizeArgbHex
import tgx.gradle.task.unwrapDoubleQuotes
import java.io.File

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