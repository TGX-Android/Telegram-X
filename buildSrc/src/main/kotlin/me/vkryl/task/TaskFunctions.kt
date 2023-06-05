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

import java.io.File
import java.io.FileOutputStream
import java.io.Writer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.*

fun isWindowsHost(): Boolean {
  return System.getProperty("os.name").startsWith("Windows")
}

fun writeToFile(path: String, block: (Writer) -> Unit) {
  val isWindows = isWindowsHost()
  // TODO proper detection, but it isn't needed for now,
  // because all paths passed to this method are relative.
  val isRelativePath = !path.startsWith("/")
  val isRootFolder = !path.contains("/")
  val file = if (isRelativePath && isWindows) {
    File("${System.getProperty("user.dir")}${File.separator}$path")
  } else {
    File(path)
  }
  writeToFile(file, mkdirs = !isRootFolder, block)
}

fun writeToFile(file: File, mkdirs: Boolean = true, block: (Writer) -> Unit) {
  if (file.parentFile == null) {
    if (mkdirs) {
      error("Invalid file path: ${file.absolutePath}")
    }
  } else if (!file.parentFile.exists()) {
    if (mkdirs) {
      if (!file.parentFile.mkdirs())
        error("Could not create folder: ${file.parentFile.absolutePath}")
    } else {
      error("Folder does not exist: ${file.parentFile.absolutePath}")
    }
  }

  if (file.exists() && !file.isFile) {
    error("Not a file: ${file.absolutePath}")
  }
  val outFile = File(file.parentFile, "${file.name}.temp")
  FileOutputStream(outFile).use { stream ->
    stream.bufferedWriter().use {
      try {
        block(it)
      } catch (t: Throwable) {
        outFile.delete()
        throw t
      }
    }
    stream.flush()
  }

  if (file.exists()) {
    if (!areFileContentsIdentical(file, outFile)) {
      if (isWindowsHost()) {
        Thread.sleep(300)
        System.gc()
      }
      copyOrReplace(outFile, file)
    }
    if (!outFile.delete() && outFile.exists()) {
      // Give time to unlock the file and try again
      for(i in 0..7) {
        Thread.sleep(300)
        System.gc()
        if (outFile.delete()) return
      }

      error("Could not delete temp file: ${outFile.absolutePath}")
    }
  } else {
    outFile.renameTo(file)
  }
}

fun copyOrReplace(fromFile: File, toFile: File) {
  FileChannel.open(fromFile.toPath(), StandardOpenOption.READ).use { inChannel ->
    FileChannel.open(toFile.toPath(), setOf(
      StandardOpenOption.WRITE,
      StandardOpenOption.TRUNCATE_EXISTING)
    ).use { outChannel ->
      inChannel.transferTo(0, inChannel.size(), outChannel)
    }
  }
}

fun editFile(path: String, block: (String) -> String) {
  val file = File(path)
  if (!file.exists()) {
    error("File does not exist: ${file.absolutePath}")
  }
  if (!file.isFile) {
    error("Not a file: ${file.absolutePath}")
  }

  val tempFile = File(file.parentFile, "${file.name}.temp")
  var hasChanges = false
  tempFile.bufferedWriter().use { writer ->
    file.bufferedReader().use { reader ->
      var first = true
      while (true) {
        val line = reader.readLine() ?: break
        if (first) {
          first = false
        } else {
          writer.append("\n")
        }
        val changedLine = block(line)
        if (!hasChanges && line != changedLine) {
          hasChanges = true
        }
        writer.append(changedLine)
      }
    }
  }

  if (hasChanges) {
    copyOrReplace(tempFile, file)
  }
  tempFile.delete()
}

fun areFileContentsIdentical(a: File, b: File): Boolean {
  val areIdentical: Boolean
  FileChannel.open(a.toPath(), StandardOpenOption.READ).use { fileChannelA ->
    FileChannel.open(b.toPath(), StandardOpenOption.READ).use { fileChannelB ->
      val mapA = fileChannelA.map(FileChannel.MapMode.READ_ONLY, 0, fileChannelA.size())
      val mapB = fileChannelB.map(FileChannel.MapMode.READ_ONLY, 0, fileChannelB.size())
      areIdentical = mapA == mapB
    }
  }
  return areIdentical
}

fun String.camelCaseToUpperCase(): String {
  val upperCase = StringBuilder()
  var i = 0
  while (i < this.length) {
    val codePoint = this.codePointAt(i)
    if (Character.isUpperCase(codePoint)) {
      if (i > 0)
        upperCase.append('_')
      upperCase.appendCodePoint(codePoint)
    } else {
      upperCase.appendCodePoint(Character.toUpperCase(codePoint))
    }
    i += Character.charCount(codePoint)
  }
  return upperCase.toString()
}

fun String.stripUnderscoresWithCamelCase (): String {
  val upperCase = StringBuilder(this.length)
  var nextUpperCase = false
  for (c in this) {
    when {
      c == '_' -> nextUpperCase = true
      nextUpperCase -> {
        upperCase.append(c.uppercaseChar())
        nextUpperCase = false
      }
      else -> upperCase.append(c)
    }
  }
  return upperCase.toString()
}

fun String.normalizeArgbHex(): String {
  if (!this.startsWith("#"))
    error("Invalid color: $this")
  val hex = this.substring(1)
  when (hex.length) {
    3 -> {
      val b = StringBuilder(8).append("ff")
      for (c in hex) {
        val l = c.lowercaseChar()
        b.append(l).append(l)
      }
      return b.toString()
    }
    4 -> {
      val r = hex[0].lowercaseChar()
      val g = hex[1].lowercaseChar()
      val b = hex[2].lowercaseChar()
      val a = hex[3].lowercaseChar()
      return StringBuilder(8)
        .append(a).append(a)
        .append(r).append(r)
        .append(g).append(g)
        .append(b).append(b).toString()
    }
    6 -> {
      return "ff${hex.lowercase(Locale.US)}"
    }
    8 -> {
      return hex.substring(6, 8).lowercase(Locale.US) + hex.substring(0, 6).lowercase(Locale.US)
    }
    else -> error("Invalid color: $this")
  }
}

fun String.parseArgbColor(): Int {
  val hex = this.normalizeArgbHex()
  val colors = mutableListOf<Int>()
  for (i in 0 .. (hex.length / 2)) {
    val x = hex.substring(i * 2, i * 2 + 1)
    colors.add(x.toInt(16))
  }
  return if (colors.size == 3) {
    rgb(colors[0], colors[1], colors[2])
  } else {
    argb(colors[0], colors[1], colors[2], colors[3])
  }
}

fun rgb(red: Int, green: Int, blue: Int): Int {
  return -0x1000000 or (red shl 16) or (green shl 8) or blue
}

fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int {
  return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
}

fun String.unwrapDoubleQuotes(): String {
  if (!this.startsWith("\"") || !this.endsWith("\""))
    error("Not wrapped: \"${this}\"")
  return this.substring(1, this.length - 1).replace("\\\"", "\"")
}