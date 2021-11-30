@file:JvmName("StringUtils")

package me.vkryl.core

import android.net.Uri
import android.os.Build
import android.text.Spanned
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.nio.charset.Charset
import java.util.*
import java.util.regex.Pattern

@JvmField
val UTF_8: Charset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) java.nio.charset.StandardCharsets.UTF_8 else Charset.forName("UTF-8")

fun isEmpty(str: CharSequence?): Boolean = str.isNullOrEmpty()
fun isEmptyOrBlank(str: CharSequence?): Boolean = str.isNullOrBlank()

fun isEmptyOrInvisible(str: CharSequence?): Boolean {
  when {
    str.isNullOrBlank() -> return true
    else -> {
      for (i in str.indices) {
        if (!str[i].isWhitespace() && str[i] != '\u200B') {
          return false
        }
      }
      return true
    }
  }
}

fun trim(str: String?): String? = str?.trim()

fun isDomain(str: String?): Boolean = !(str == null || str.contains('/'))

fun isNumeric(c: Char): Boolean = c in '0'..'9'
fun isNumeric(str: String?): Boolean {
  when {
    str.isNullOrBlank() -> return false
    else -> {
      for (i in str.indices) {
        if (!isNumeric(str[i]))
          return false
      }
      return true
    }
  }
}

fun String.match(pattern: Pattern): String? {
  val matcher = pattern.matcher(this)
  return if (matcher.matches()) {
    matcher.group()
  } else {
    null
  }
}

@JvmOverloads fun parseInt(str: String?, defaultValue: Int = 0): Int = str?.toIntOrNull() ?: defaultValue
@JvmOverloads fun parseLong(str: String?, defaultValue: Long = 0): Long = str?.toLongOrNull() ?: defaultValue
@JvmOverloads fun parseFloat(str: String?, defaultValue: Float = 0.0f): Float = str?.toFloatOrNull() ?: defaultValue
fun parseIntArray(str: String?, delimiter: String): IntArray? {
  return if (str.isNullOrBlank()) null else {
    val items = str.split(delimiter)
    val result = IntArray(items.size)
    for ((index, value) in items.withIndex()) {
      try {
        result[index] = value.toInt()
      } catch (e: NumberFormatException) {
        return null
      }
    }
    result
  }
}
fun parseLongArray(str: String?, delimiter: String): LongArray? {
  return if (str.isNullOrBlank()) null else {
    val items = str.split(delimiter)
    val result = LongArray(items.size)
    for ((index, value) in items.withIndex()) {
      try {
        result[index] = value.toLong()
      } catch (e: NumberFormatException) {
        return null
      }
    }
    result
  }
}

fun indexOf(source: CharSequence, target: String, startIndex: Int = 0): Int = source.indexOf(target, startIndex)
fun indexOfAny(source: CharSequence, target: CharArray, startIndex: Int = 0): Int = source.indexOfAny(target, startIndex)

fun length(str: CharSequence?): Int = str?.length ?: 0

@SuppressWarnings("DefaultLocale")
@kotlin.ExperimentalStdlibApi
fun ucfirst(str: String?, locale: Locale? = null): String? {
  return str?.replaceFirstChar {
    if (it.isLowerCase()) {
      if (locale != null) {
        it.titlecase(locale)
      } else {
        it.titlecase()
      }
    } else {
      it.toString()
    }
  }
}

@kotlin.ExperimentalStdlibApi
fun toCamelCase(str: String?): String? {
  return str?.let {
    it.replace("'", "")
      .split(" ", "-")
      .joinToString("") {
        item -> ucfirst(item)!!
      }
  }
}

fun CharSequence.equalsTo(other: CharSequence): Boolean {
  if (this is Spanned || other is Spanned) {
    try {
      return this == other
    } catch (e: Throwable) {
      if (this.toString() != other.toString())
        return false
      val thisSpans = if (this is Spanned) {
        this.getSpans(0, this.length, Object::class.java)
      } else {
        null
      }
      val otherSpans = if (other is Spanned) {
        other.getSpans(0, other.length, Object::class.java)
      } else {
        null
      }
      if ((thisSpans?.size ?: 0) != (otherSpans?.size ?: 0))
        return false
      if (thisSpans?.size ?: 0 == 0)
        return true
      require(this is Spanned && thisSpans != null)
      require(other is Spanned && otherSpans != null)
      for (i in thisSpans.indices) {
        val thisSpan = thisSpans[i]
        val otherSpan = otherSpans[i]
        try {
          if (thisSpan != otherSpan)
            return false
          val thisStart = this.getSpanStart(thisSpan)
          val otherStart = other.getSpanStart(otherSpan)
          if (thisStart != otherStart)
            return false
          val thisEnd = this.getSpanEnd(thisSpan)
          val otherEnd = other.getSpanEnd(otherSpan)
          if (thisEnd != otherEnd)
            return false
        } catch (e: Throwable) {
          return false
        }
      }
      return true
    }
  }
  return this == other
}

fun CharSequence?.equalsOrBothEmpty(other: CharSequence?): Boolean {
  val thisEmpty = isEmpty(this)
  val otherEmpty = isEmpty(other)
  return thisEmpty == otherEmpty && (thisEmpty || this!!.equalsTo(other!!))
}
@SuppressWarnings("DefaultLocale")
@kotlin.ExperimentalStdlibApi
fun equalsOrEmptyIgnoreCase(a: CharSequence?, b: CharSequence?, locale: Locale? = null): Boolean {
  return when (locale) {
    null -> a?.toString()?.lowercase().equalsOrBothEmpty(b?.toString()?.lowercase())
    else -> a?.toString()?.lowercase(locale).equalsOrBothEmpty(b?.toString()?.lowercase(locale))
  }
}

fun join(separator: String, tokens: IntArray): String = tokens.joinToString(separator)
fun join(separator: String, tokens: LongArray): String = tokens.joinToString(separator)
fun join(separator: String, separatorForLastItem: String, list: List<String>): String {
  val size = list.size
  if (size == 0) {
    return ""
  }
  var totalSize: Int = 0.coerceAtLeast(separator.length * (size - 2)) + separatorForLastItem.length
  var index = 0
  for (item in list) {
    totalSize += item.length
    if (++index == size) {
      break
    }
  }
  val b = StringBuilder(totalSize)
  index = 0
  for (item in list) {
    if (index > 0) {
      b.append(if (index == size - 1) separatorForLastItem else separator)
    }
    b.append(item)
    if (++index == size) {
      break
    }
  }
  return b.toString()
}

fun decodeURIComponent(component: String): String? {
  return try {
    URLDecoder.decode(component, "UTF-8")
  } catch (ignored: UnsupportedEncodingException) {
    null
  }
}

fun urlWithoutProtocol(url: String): String {
  return when {
    url.startsWith("https://") -> url.substring("https://".length)
    url.startsWith("http://") -> url.substring("http://".length)
    else -> url
  }
}

@kotlin.ExperimentalStdlibApi
fun wrapHttps(url: String?): Uri? {
  if (url.isNullOrEmpty())
    return null
  return try {
    val uri = Uri.parse(url)
    val scheme = uri.scheme
    when {
      scheme.isNullOrEmpty() -> Uri.parse("https://$url")
      scheme.lowercase() != scheme -> uri.buildUpon().scheme(scheme.lowercase()).build()
      else -> uri
    }
  } catch (e: Throwable) {
    e.printStackTrace()
    null
  }
}

fun domainOf(url: String?): String? {
  when {
    url.isNullOrBlank() -> return null
    else -> {
      val startIndex = when {
        url.startsWith("https://") -> "https://".length
        url.startsWith("http://") -> "http://".length
        else -> 0
      }
      val endIndex = url.indexOf('/', startIndex)
      return when {
        startIndex == 0 && endIndex == -1 -> url
        endIndex != -1 -> url.substring(startIndex, endIndex)
        startIndex > 0 -> url.substring(startIndex)
        else -> url
      }
    }
  }
}

fun random(source: String, length: Int): String {
  return (1..length)
    .map { source.random() }
    .joinToString("")
}

fun hasFormatArguments(str: String): Boolean {
  var startIndex = 0
  val len = str.length
  do {
    val i = str.indexOf('%', startIndex)
    if (i == -1 || i + 1 >= len) return false
    val c = str[i + 1]
    if (c != '%') return true
    startIndex = i + 2
  } while (startIndex != -1)
  return false
}

fun makeSortId(num: Int): String {
  val len = Int.MAX_VALUE.toString().length
  val res = num.toString()
  val zeros = len - res.length
  if (zeros > 0) {
    val b = java.lang.StringBuilder(len)
    for (i in 0 until zeros) {
      b.append('0')
    }
    b.append(res)
    return b.toString()
  }
  return res
}

@kotlin.ExperimentalStdlibApi
fun String?.secureFileName (): String? {
  if (this.isNullOrEmpty())
    return this
  var b: StringBuilder? = null
  val len = this.length
  var i = 0
  while (i < len) {
    val codePoint = this.codePointAt(i)
    val replacement = when (codePoint) {
      '\\'.code -> '\u29F5'
      '/'.code -> '\u2044'
      else -> (0).toChar()
    }
    if (replacement.code != 0) {
      if (b == null) {
        b = StringBuilder(len)
        if (i > 0)
          b.append(this, 0, i)
      }
      b.append(replacement)
    } else {
      b?.appendCodePoint(codePoint)
    }
    i += Character.charCount(codePoint)
  }
  return b?.toString() ?: this
}