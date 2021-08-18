@file:JvmName("FileUtils")

package me.vkryl.core

import java.io.*
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

fun getSize(files: Array<File>?): Long {
  return if (files.isNullOrEmpty()) 0 else {
    var totalSize: Long = 0
    for (dir in files) totalSize += getSize(dir)
    totalSize
  }
}

fun getSize(file: File): Long {
  return if (!file.exists()) {
    0
  } else if (file.isDirectory) {
    var totalSize: Long = 0
    val files = file.listFiles()
    if (!files.isNullOrEmpty()) {
      for (nested in files) {
        totalSize += getSize(nested)
      }
    }
    totalSize
  } else {
    file.length()
  }
}

fun delete(files: Array<File>?, recursive: Boolean): Boolean {
  return if (files.isNullOrEmpty()) true else {
    var success = true
    for (file in files) {
      if (!delete(file, recursive)) success = false
    }
    return success
  }
}

fun delete(file: File, recursive: Boolean): Boolean {
  return if (!file.exists()) true
  else {
    var hasErrors = false
    if (file.isDirectory) {
      if (!recursive) return true
      val children = file.listFiles()
      if (children != null) {
        for (child in children) {
          if (!delete(child, true)) {
            hasErrors = true
          }
        }
      }
    }
    file.delete() && !hasErrors
  }
}

fun copy(src: File, dst: File): Boolean {
  try {
    FileInputStream(src).use { inputStream ->
      inputStream.channel.use { inChannel ->
        FileOutputStream(dst).use { out ->
          out.channel.use { outChannel ->
            inChannel.transferTo(0, inChannel.size(), outChannel)
            out.flush()
            return true
          }
        }
      }
    }
  } catch (t: Throwable) {
    t.printStackTrace()
    return false
  }
}

@Throws(IOException::class)
fun unzip(zipFile: File, targetDirectory: File) {
  ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { stream ->
    var entry: ZipEntry?
    var count: Int
    val buffer = ByteArray(8192)
    do {
      entry = stream.nextEntry
      if (entry == null)
        break
      val file = File(targetDirectory, entry.name)
      if (!file.canonicalPath.startsWith(targetDirectory.canonicalPath))
        throw SecurityException()
      val dir = if (entry.isDirectory) file else file.parentFile
      if (!dir.isDirectory && !dir.mkdirs()) throw FileNotFoundException("Failed to ensure directory: " +
        dir.absolutePath)
      if (entry.isDirectory) continue
      FileOutputStream(file).use { fout -> while (stream.read(buffer).also { count = it } != -1) fout.write(buffer, 0, count) }
      /* if time should be restored as well
      long time = ze.getTime();
      if (time > 0)
          file.setLastModified(time);
      */
    } while (true)
  }
}

fun getAllFiles(dir: File): Array<File> {
  val result: MutableList<File> = ArrayList()
  getAllFiles(dir, result)
  return result.toTypedArray()
}

private fun getAllFiles(dir: File, out: MutableList<File>) {
  if (!dir.exists()) return
  out.add(dir)
  if (dir.isDirectory) {
    val files = dir.listFiles()
    if (files != null) {
      for (file in files) {
        getAllFiles(file, out)
      }
    }
  }
}

fun deleteFile(path: String): Boolean = deleteFile(File(path))

fun deleteFile(file: File): Boolean {
  try {
    return !file.exists() || file.delete()
  } catch (ignored: Throwable) {
  }
  return false
}