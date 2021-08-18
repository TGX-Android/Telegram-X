import com.android.build.api.dsl.BaseFlavor
import com.android.build.api.dsl.VariantDimension
import com.android.build.gradle.api.BaseVariant
import java.io.File
import java.util.*

fun BaseFlavor.buildConfigInt (name: String, value: Int) =
  this.buildConfigField("int", name, value.toString())
fun BaseFlavor.buildConfigLong (name: String, value: Long) =
  this.buildConfigField("long", name, value.toString())
fun BaseFlavor.buildConfigBool (name: String, value: Boolean) =
  this.buildConfigField("boolean", name, value.toString())
fun BaseFlavor.buildConfigString (name: String, value: String) =
  this.buildConfigField("String", name, "\"$value\"")
fun BaseVariant.buildConfigInt (name: String, value: Int) =
  this.buildConfigField("int", name, value.toString())
fun BaseVariant.buildConfigLong (name: String, value: Long) =
  this.buildConfigField("long", name, value.toString())
fun BaseVariant.buildConfigBool (name: String, value: Boolean) =
  this.buildConfigField("boolean", name, value.toString())
fun BaseVariant.buildConfigString (name: String, value: String) =
  this.buildConfigField("String", name, "\"$value\"")
fun VariantDimension.buildConfigInt (name: String, value: Int) =
  this.buildConfigField("int", name, value.toString())
fun VariantDimension.buildConfigLong (name: String, value: Long) =
  this.buildConfigField("long", name, value.toString())
fun VariantDimension.buildConfigString (name: String, value: String) =
  this.buildConfigField("String", name, "\"$value\"")


fun loadProperties (path: String = "local.properties"): Properties {
  val file = File(path)
  if (!file.canRead())
    throw AssertionError("Cannot read ${file.absolutePath}")
  if (file.isDirectory)
    throw AssertionError("Is a directory: ${file.absolutePath}")
  val properties = Properties()
  file.bufferedReader().use {
    properties.load(it)
  }
  return properties
}

fun buildDate (timeZone: TimeZone): Calendar {
  val c = Calendar.getInstance(timeZone)
  var commitTime: String
  val process = ProcessBuilder("git", "show", "-s", "--format=%ct").start()
  process.inputStream.reader(Charsets.UTF_8).use {
    commitTime = it.readText().trim()
  }
  process.waitFor()
  val unixTime = commitTime.toLongOrNull()
  unixTime?.let {
    c.timeInMillis = unixTime * 1000L
  }
  return c
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
    error("$key is set, but not a number")
  }
}

fun Properties.getOrThrow (key: String): String {
  return this[key]?.let {
    val res = it.toString()
    if (res.isEmpty())
      error("$key is set, but empty")
    res
  } ?: error("$key is not set, available: ${keys.joinToString(", ")}")
}

class Keystore (configPath: String) {
  val file: File
  val password: String
  val keyAlias: String
  val keyPassword: String

  init {
    val config = loadProperties(configPath)
    this.file = File(config.getOrThrow("keystore.file"))
    this.password = config.getOrThrow("keystore.password")
    this.keyAlias = config.getOrThrow("key.alias")
    this.keyPassword = config.getOrThrow("key.password")
  }
}