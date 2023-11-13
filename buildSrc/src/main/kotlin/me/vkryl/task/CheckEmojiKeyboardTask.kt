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

import com.beust.klaxon.Klaxon
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.*
import kotlin.contracts.ExperimentalContracts
import kotlin.streams.asSequence

open class CheckEmojiKeyboardTask : BaseTask() {
  private fun String.getArray(name: String): String {
    return Regex("(?<= $name = )[^;]+").find(this)!!.value
      .replace('{', '[')
      .replace('}', ']')
      .replace(Regex("/\\*.*?(?=\\*/)\\*/"), "")
      .replace(Regex("//[^\n]+"), "")
  }

  private fun hex(text: String): String {
    return text.chars().asSequence().joinToString("") { "\\u" + it.toString(16).uppercase(Locale.US) }
  }

  private fun javaWrap(text: String): String {
    return "\"${hex(text)}\" /* $text */"
  }

  private fun emojiSignature(emoji: String?): String {
    return emoji?.let {
      "$emoji (${hex(emoji)})"
    } ?: "null"
  }

  private fun findGender(emoji: String): Pair<String, Char>? {
    var open = false
    var index = 0
    for (c in emoji) {
      if (c == '\u200D') {
        open = true
      } else if (open) {
        if (c == '\u2640' || c == '\u2642') {
          return Pair(emoji.removeRange(index - 1, index + 1), c)
        }
        open = false
      }
      index++
    }
    return null
  }

  @Suppress("SameParameterValue", "unused")
  private fun findTones(emoji: String/*, defaultSkinTone: Char*/): Pair<String, List<Char>?> {
    var open = false
    var tones: MutableList<Char>? = null
    var result = emoji
    var index = 0
    for (c in emoji) {
      if (c == '\uD83C') {
        open = index > 0
      } else if (open) {
        open = false
        if (c in '\uDFFB'..'\uDFFF') {
          if (tones == null)
            tones = mutableListOf(c)
          else
            tones.add(c)
          result = result.substring(0, index - 1) + result.substring(index + 1)
          index -= 2
        }
      }
      index++
    }
    /*if (tones != null && tones.size > 1) {
      val b = StringBuilder(emoji.length)
      open = false
      for (c in emoji) {
        if (c == '\uD83C') {
          open = b.isNotEmpty()
        } else if (open) {
          open = false
          if (c in '\uDFFB'..'\uDFFF') {
            b.append(defaultSkinTone)
            continue
          }
        }
        b.append(c)
      }
      result = emoji.replace(Regex("(?<=\\uD83C)"), "ELLO world")
      error(emojiSignature(result))
    }*/
    return Pair<String, List<Char>?>(result, tones)
  }

  private fun <T> List<T>.findDuplicates (): List<T>? {
    var duplicates: MutableList<T>? = null
    val set = mutableSetOf<T>()
    for (item in this) {
      if (!set.add(item)) {
        if (duplicates != null) {
          duplicates.add(item)
        } else {
          duplicates = mutableListOf(item)
        }
      }
    }
    return duplicates
  }

  @ExperimentalContracts
  @TaskAction
  fun checkEmojiKeyboard () {
    val supportedFile = File("app/src/main/java/org/thunderdog/challegram/tool/EmojiCode.java").readText()
    val displayingFile = File("app/src/main/java/org/thunderdog/challegram/tool/EmojiCodeColored.java").readText()

    val supportedArray = supportedFile.getArray("DATA")

    val displayingArray = displayingFile.getArray("DATA_COLORED").replace("EmojiCode.DATA[1]", "[]")

    val supported = try {
      Klaxon().parseArray<List<String>>(supportedArray)!!
    } catch (e: Throwable) {
      error("Unable to parse supportedArray: ${e.message}\n${supportedArray}")
    }
    val displaying = try {
      Klaxon().parseArray<List<String>>(displayingArray)!!
    } catch (e: Throwable) {
      error("Unable to parse displayingArray: ${e.message}\n${displayingArray}")
    }

    val modifierEmoji = setOf(
      // tones
      "\uD83C\uDFFB", // 🏻
      "\uD83C\uDFFE", // 🏾
      "\uD83C\uDFFD", // 🏽
      "\uD83C\uDFFC", // 🏼
      "\uD83C\uDFFF", // 🏿

      // hair component
      "\uD83E\uDDB0", // 🦰
      "\uD83E\uDDB1", // 🦱
      "\uD83E\uDDB3", // 🦳
      "\uD83E\uDDB2"  // 🦲
    )
    // val defaultSkinTone = '\uDFFD' // 🏽

    val genderOrder = listOf(
      '\u2640',
      (0).toChar(),
      '\u2642'
    )
    val tone2dAliases = mapOf(
      Pair("\uD83D\uDC69\u200D\uD83E\uDD1D\u200D\uD83D\uDC68", "\uD83D\uDC6B"),
      Pair("\uD83D\uDC69\u200D\uD83E\uDD1D\u200D\uD83D\uDC69", "\uD83D\uDC6D"),
      Pair("\uD83D\uDC68\u200D\uD83E\uDD1D\u200D\uD83D\uDC68", "\uD83D\uDC6C"),
      Pair("\uD83E\uDDD1\u200D\u2764\u200D\uD83D\uDC8B\u200D\uD83E\uDDD1", "\uD83D\uDC8F"),
      Pair("\uD83E\uDDD1\u200D\u2764\u200D\uD83E\uDDD1", "\uD83D\uDC91"),
      Pair("\uD83E\uDEF1\u200D\uD83E\uDEF2", "\uD83E\uDD1D" /* 🤝 */)
    )

    val supportedTonedEmoji = mutableSetOf<String>()
    val tonedEmoji1d = mutableMapOf<String, MutableList<Pair<Char, String>>>()
    val tonedEmoji2d = mutableMapOf<String, MutableList<Pair<Pair<Char, Char>, String>>>()

    val mixed2dEmoji = mutableSetOf<String>()
    val genderedEmoji = mutableMapOf<String, MutableList<Char>>()

    val supportedSet = mutableSetOf<String>()

    var maxEmojiLength = 0

    for (chunk in supported) {
      for (emoji in chunk) {
        maxEmojiLength = maxOf(maxEmojiLength, emoji.length)
        val toned = findTones(emoji/*, defaultSkinTone*/)
        val originalEmoji = tone2dAliases[toned.first] ?: toned.first
        val tones = toned.second
        if (tones.isNullOrEmpty()) {
          if (!supportedSet.add(emoji))
            error("Duplicate supported emoji: ${emojiSignature(emoji)}")
          continue
        }

        // Tone

        if (!supportedTonedEmoji.add(emoji))
          error("Duplicate supported emoji (toned): ${emojiSignature(emoji)}")

        when (tones.size) {
          1 -> {
            val list = tonedEmoji1d[originalEmoji]
            val pair = Pair(tones[0], emoji)
            if (list == null) {
              val existing2dList = tonedEmoji2d[originalEmoji]
              if (existing2dList != null) {
                existing2dList.add(Pair(Pair(pair.first, pair.first), pair.second))
                mixed2dEmoji.add(originalEmoji)
              } else {
                tonedEmoji1d[originalEmoji] = mutableListOf(pair)
              }
            } else {
              list.add(pair)
            }
          }
          2 -> {
            val pair = Pair(Pair(tones[0], tones[1]), emoji)
            val list = tonedEmoji2d[originalEmoji]
            if (list == null) {
              val existing1dList = tonedEmoji1d.remove(originalEmoji)
              if (existing1dList != null) {
                tonedEmoji2d[originalEmoji] = existing1dList.map {
                  Pair(Pair(it.first, it.first), it.second)
                }.toMutableList().let {
                  it.add(pair)
                  it
                }
                mixed2dEmoji.add(originalEmoji)
              } else {
                tonedEmoji2d[originalEmoji] = mutableListOf(pair)
              }
            } else {
              list.add(pair)
            }
          }
          else -> {
            error("Too many tones for one emoji: ${emojiSignature(emoji)}")
          }
        }
      }
    }

    tonedEmoji1d.forEach {
      if (it.value.size != 5) {
        error("Missing ${5 - it.value.size} tones for 1d-emoji: ${emojiSignature(it.key)})")
      }
      if (!supportedSet.contains(it.key)) {
        error("Unsupported base 1d-emoji: ${emojiSignature(it.key)}")
      }
    }
    tonedEmoji2d.forEach {
      if (it.value.size != 5 * 5) {
        error("Missing ${(5 * 5) - it.value.size} tones for 2d-emoji: ${emojiSignature(it.key)}")
      }
      if (!supportedSet.contains(it.key)) {
        error("Unsupported base 2d-emoji: ${emojiSignature(it.key)}")
      }
    }

    val missingEmoji = mutableListOf<Pair<String, String>>()
    val displayingSet = mutableSetOf<String>()
    displaying.forEachIndexed { index, emojis ->
      for (emoji in emojis) {
        if (!supportedSet.contains(emoji)) {
          error("Unknown displayed emoji: ${emojiSignature(emoji)})")
        }
      }
      val addingEmoji = if (emojis.isEmpty()) {
        supported[index]
      } else {
        emojis
      }
      for (emoji in addingEmoji) {
        if (!displayingSet.add(emoji)) {
          error("Duplicate displaying emoji: ${emojiSignature(emoji)}")
        }
        val gender = findGender(emoji)
        if (gender != null) {
          val genderList = genderedEmoji[gender.first]
          if (genderList == null) {
            genderedEmoji[gender.first] = if (displayingSet.contains(gender.first)) {
              mutableListOf((0).toChar(), gender.second)
            } else {
              mutableListOf(gender.second)
            }
          } else {
            genderList.add(gender.second)
          }
        } else {
          genderedEmoji[emoji]?.add((0).toChar())
        }
      }
    }

    var prevEmoji: String? = null
    var prevIsMissing = false

    for (supportedEmoji in supportedSet) {
      prevIsMissing = if (!displayingSet.contains(supportedEmoji)) {
        if (!modifierEmoji.contains(supportedEmoji)) {
          missingEmoji.add(Pair(supportedEmoji, "${emojiSignature(prevEmoji)}${if (prevIsMissing) " (missing)" else ""}"))
        }
        true
      } else {
        false
      }
      prevEmoji = supportedEmoji
    }

    val incorrectOrderEmoji = mutableListOf<Pair<String, String>>()
    for (emoji in genderedEmoji) {
      if (emoji.value != genderOrder) {
        incorrectOrderEmoji.add(Pair(emoji.key, emoji.value.joinToString { emojiSignature(it.toString()) }))
      }
    }

    val errors = mutableListOf<String>()

    if (incorrectOrderEmoji.isNotEmpty()) {
      errors.add("${incorrectOrderEmoji.size} incorrectly ordered genders:\n${incorrectOrderEmoji.joinToString("\n") { "${emojiSignature(it.first)}: ${it.second}" }}")
    }

    if (missingEmoji.isNotEmpty()) {
      errors.add("${missingEmoji.size} missing emoji:\n${missingEmoji.joinToString("\n") { "${emojiSignature(it.first)}: after ${it.second}" }}")
    }

    if (errors.isNotEmpty()) {
      error("${missingEmoji.size + incorrectOrderEmoji.size} emoji-related error(s).\n\n${errors.joinToString("\n\n")}")
    }

    writeToFile("app/src/main/java/org/thunderdog/challegram/tool/Emojis.kt") { kt ->
      kt.append("""
        @file:JvmName("Emojis")

        package org.thunderdog.challegram.tool

        import me.vkryl.annotation.Autogenerated
        
        const val MAX_EMOJI_LENGTH = ${maxEmojiLength}

        @Autogenerated fun colored1dSet () = setOf(
          ${tonedEmoji1d.keys.joinToString(",\n          ") { javaWrap(it) }}
        )
        
        @Autogenerated fun colored2dSet () = setOf(
          ${tonedEmoji2d.keys.joinToString(",\n          ") { javaWrap(it) }}
        )
        
        @Autogenerated fun colored2dMap () = mapOf(
          ${
        tonedEmoji2d.entries.joinToString(",\n          ") { emoji ->
          emoji.value.joinToString(",\n          ") { tone ->
            "Pair(" +
              "\"${hex(emoji.key)}_${
                if (tone.first.first == tone.first.second) {
                  hex(tone.first.first.toString())
                } else {
                  "${hex(tone.first.first.toString())}_${hex(tone.first.second.toString())}"
                }
              }\", ${javaWrap(tone.second)})"
          }
        }
      }
        )
        
        @Autogenerated fun colorize (emoji: String, tone: Char, secondTone: Char): String? = if (tone == secondTone) {
          when (emoji) {${
        tonedEmoji2d.entries.filter { !mixed2dEmoji.contains(it.key) }.joinToString("            ") { entry ->
          """
            ${javaWrap(entry.key)} -> when (tone) {
              ${
            entry.value.filter { it.first.first == it.first.second }.joinToString("\n              ") {
              "'${hex(it.first.first.toString())}' -> ${javaWrap(it.second)}"
            }
          }
              else -> null
            }"""
        }
      }
            else -> null
          }
        } else {
          when (emoji) {${
        tonedEmoji2d.entries.joinToString("            ") { entry ->
          """
            ${javaWrap(entry.key)} -> when {
              ${
            entry.value.filter { it.first.first != it.first.second }.joinToString("\n              ") {
              "tone == '${hex(it.first.first.toString())}' && secondTone == '${hex(it.first.second.toString())}' -> ${javaWrap(it.second)}"
            }
          }
              else -> null
            }"""
        }
      }
            else -> null
          }
        }
      """.trimIndent())
    }
  }
}