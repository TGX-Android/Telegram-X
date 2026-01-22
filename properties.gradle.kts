import java.io.File
import java.util.*

var localProps: Properties? = null
var rootProps: Properties? = null

// CLI properties: gradle.startParameter.projectProperties
val cliProps = gradle.startParameter.projectProperties

fun readProperties (fileName: String): Properties = Properties().apply {
  val file = if (rootDir.name == "buildSrc") {
    File("${rootDir.parentFile}/${fileName}")
  } else {
    File("${rootProject.projectDir}/${fileName}")
  }
  val f = file.canonicalFile
  if (!f.exists()) {
    error(f.absolutePath)
  } else {
    f.inputStream().use {
      load(it)
    }
  }
}

fun resolveGradleProperty(name: String): String? {
  return providers.gradleProperty(name).orNull ?: let {
    cliProps[name] ?: System.getProperty(name) ?: let {
      if (localProps == null) {
        localProps = readProperties("local.properties")
      }
      localProps!![name]?.toString() ?: let {
        if (rootProps == null) {
          rootProps = readProperties("gradle.properties")
        }
        rootProps!![name]?.toString()
      }
    }
  }
}

val extension = resolveGradleProperty("tgx.extension") ?: "none"
if (extension != "none" && extension != "hms") {
  error("Unknown extension: ${extension}")
}
extra["huawei"] = (extension == "hms")
extra["extension"] = extension
