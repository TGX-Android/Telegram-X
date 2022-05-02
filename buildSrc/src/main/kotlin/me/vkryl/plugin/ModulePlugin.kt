package me.vkryl.plugin

import Config
import Keystore
import LibraryVersions
import buildDate
import com.android.build.gradle.AppExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.ProguardFiles
import getOrThrow
import loadProperties
import monthYears
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.get
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File
import java.net.URI
import java.util.*

open class ModulePlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val plugins = arrayOf(
      "kotlin-android"
    )
    for (plugin in plugins) {
      project.plugins.apply(plugin)
    }

    project.dependencies {
      add("coreLibraryDesugaring", "com.android.tools:desugar_jdk_libs:${LibraryVersions.DESUGAR}")
    }

    val javaVersion = JavaVersion.VERSION_11

    val properties = loadProperties()
    val keystore = Keystore(properties.getOrThrow("keystore.file"))
    val appVersionOverride = properties.getProperty("app.version", "0").toInt()

    val versions = loadProperties("version.properties")
    val appVersion = if (appVersionOverride > 0) appVersionOverride else versions.getOrThrow("version.app").toInt()
    val majorVersion = versions.getOrThrow("version.major").toInt()

    val timeZone = TimeZone.getTimeZone("UTC")
    val then = Calendar.getInstance(timeZone)
    then.timeInMillis = versions.getOrThrow("version.creation").toLong()
    val now = buildDate(timeZone)
    if (now.timeInMillis < then.timeInMillis)
      error("Invalid commit time!")
    val minorVersion = monthYears(now, then)

    val androidExt = project.extensions.getByName("android")
    if (androidExt is BaseExtension) {
      androidExt.apply {
        compileSdkVersion(versions.getOrThrow("version.sdk_compile").toInt())
        buildToolsVersion(versions.getOrThrow("version.build_tools"))
        ndkVersion = versions.getOrThrow("version.ndk")
        ndkPath = File(sdkDirectory, "ndk/$ndkVersion").absolutePath

        compileOptions {
          isCoreLibraryDesugaringEnabled = true
          sourceCompatibility = javaVersion
          targetCompatibility = javaVersion
        }

        testOptions {
          unitTests.isReturnDefaultValues = true
        }

        sourceSets.configureEach {
          java.srcDirs("src/${this.name}/kotlin")
          jniLibs.srcDirs("jniLibs")
        }

        project.tasks.withType(KotlinCompile::class.java).configureEach {
          kotlinOptions {
            jvmTarget = javaVersion.toString()
            allWarningsAsErrors = true
          }
        }

        project.afterEvaluate {
          tasks.withType(JavaCompile::class.java).configureEach {
            options.compilerArgs.addAll(listOf("-Xmaxerrs", "2000"))
          }
        }

        defaultConfig {
          minSdk = Config.MIN_SDK_VERSION
          targetSdk = versions.getOrThrow("version.sdk_target").toInt()
          multiDexEnabled = true
          versionCode = appVersion
          versionName = "${majorVersion}.${minorVersion}"
        }

        when (this) {
          is LibraryExtension -> {
            defaultConfig {
              consumerProguardFiles("consumer-rules.pro")
            }
          }

          is AppExtension -> {
            var git: List<String>
            val process = ProcessBuilder("bash", "-c", "echo \"$(git rev-parse --short HEAD) $(git rev-parse HEAD) $(git show -s --format=%ct) $(git config --get remote.origin.url) $(git rev-parse --abbrev-ref HEAD)\"").start()
            process.inputStream.reader(Charsets.UTF_8).use {
              git = it.readText().trim().split(' ', limit = 5)
            }
            process.waitFor()
            if (git.size != 5) {
              error("Source code must be fetched from git repository.")
            }
            val commitHashShort = git[0]
            val commitHashLong = git[1]
            val commitDate = git[2].toLong()
            val remoteUrl = if (git[3].startsWith("git@")) {
              val index = git[3].indexOf(':', 4)
              val domain = git[3].substring(4, index)
              val endIndex = if (git[3].endsWith(".git")) {
                git[3].length - 4
              } else {
                git[3].length
              }
              val query = git[3].substring(index + 1, endIndex)
              "https://${domain}/${query}"
            } else {
              git[3]
            }

            if (URI.create(remoteUrl).host != "github.com") {
              error("Unfortunately, currently you must host your fork on github.com.")
            }

            val commitUrl = String.format(Locale.ENGLISH, "%1\$s/commit/%3\$s", remoteUrl, commitHashShort, commitHashLong)
            val commitBranch = git[4]

            project.extra.set("properties", properties)
            project.extra.set("versions", versions)
            project.extra.set("app_version", appVersion)
            project.extra.set("commit_short", commitHashShort)
            project.extra.set("commit_long", commitHashLong)
            project.extra.set("commit_date", commitDate)
            project.extra.set("remote_url", remoteUrl)
            project.extra.set("commit_url", commitUrl)
            project.extra.set("commit_branch", commitBranch)

            signingConfigs {
              arrayOf(getByName("debug"), maybeCreate("release")).forEach { config ->
                config.storeFile = keystore.file
                config.storePassword = keystore.password
                config.keyAlias = keystore.keyAlias
                config.keyPassword = keystore.keyPassword
                config.enableV2Signing = true
              }
            }

            buildTypes {
              getByName("debug") {
                signingConfig = signingConfigs["debug"]

                isDebuggable = true
                isJniDebuggable = true
                isMinifyEnabled = false

                ndk.debugSymbolLevel = "full"
                ndk.jobs = Runtime.getRuntime().availableProcessors()
              }

              getByName("release") {
                signingConfig = signingConfigs["release"]

                isMinifyEnabled = true
                isShrinkResources = true

                ndk.debugSymbolLevel = "full"
                ndk.jobs = Runtime.getRuntime().availableProcessors()

                proguardFiles(
                  getDefaultProguardFile(ProguardFiles.ProguardFile.OPTIMIZE.fileName),
                  "proguard-rules.pro"
                )
              }

              lintOptions {
                disable("MissingTranslation")
                isCheckDependencies = true
              }
            }

            project.dependencies {
              add("implementation", "androidx.multidex:multidex:${LibraryVersions.MULTIDEX}")
            }
          }
        }
      }
    }
  }
}