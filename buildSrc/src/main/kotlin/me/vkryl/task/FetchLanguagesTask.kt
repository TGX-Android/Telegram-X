package me.vkryl.task

import Telegram
import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon
import groovy.util.Node
import groovy.util.NodeList
import groovy.xml.XmlParser
import okhttp3.*
import org.gradle.api.tasks.TaskAction
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.contracts.ExperimentalContracts
import kotlin.system.measureTimeMillis

open class FetchLanguagesTask : BaseTask() {
  data class Languages (
    @Json("lang_codes")
    val codes: Array<String>,

    @Json("date")
    val date: Int = 0
  ) {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as Languages

      if (!codes.contentEquals(other.codes)) return false

      return true
    }

    override fun hashCode(): Int {
      return codes.contentHashCode()
    }
  }

  @ExperimentalContracts
  @TaskAction
  fun fetchLanguages () {
    val rtlKey = "language_rtl"
    val nameKey = "AppName"
    val defaultLanguageCode = "en"

    val knownOutputFolders = mapOf(
      Pair("pt-br", arrayOf("pt-rBR")),
      Pair("he", arrayOf("he", "iw")),

      Pair("zh-hans", arrayOf("zh", "b+zh+Hans+HK", "b+zh+Hans+MO")), // Chinese (Simplified)
      Pair("zh-hant", arrayOf("zh-rTW", "zh-rHK", "zh-rMO")) // Chinese (Traditional)
    )

    val requiredKeys = arrayOf(
      rtlKey,
      nameKey,

      "StartMessaging",

      "Page1Title",
      "Page1Message",
      "Page2Title",
      "Page2Message",
      "Page3Title",
      "Page3Message",
      "Page4Title",
      "Page4Message",
      "Page5Title",
      "Page5Message",
      "Page6Title",
      "Page6Message",

      "network_WaitingForNetwork",
      "network_Connecting",
      "network_Updating",
      "Connected",
      "ConnectingToProxy",

      "LoginErrorOffline",
      "LoginErrorAirplane",
      "LoginErrorLongConnecting",

      "email_LoginTooLong_text",
      "email_LoginTooLong_subject",
      "HelpEmailError",

      "Settings",
      "Help",

      "ProxyAdd",
      "AddMtprotoProxy",
      "AddSocks5Proxy",
      "AddHttpProxy",
      "ProxyInfo",
      "Socks5Proxy",
      "MtprotoProxy",
      "HttpProxy",
      "Connection",
      "UseProxyServer",
      "UseProxyPort",
      "HttpProxyTransparent",
      "HttpProxyTransparentHint",
      "ProxyCredentialsOptional",
      "ProxyUsernameHint",
      "ProxyPasswordHint",
      "ProxyCredentials",
      "ProxySecretHint",

      "LaunchTitle",
      "LaunchApp",
      "LaunchAppShareError",
      "LaunchAppViewError",
      "LaunchSubtitleDiskFull",
      "LaunchAppGuideDiskFull",
      "LaunchSubtitleDatabaseBroken",
      "LaunchAppGuideDatabaseBroken",
      "LaunchSubtitleExternalError",
      "LaunchAppGuideExternalError",
      "LaunchSubtitleTdlibIssue",
      "LaunchAppGuideTdlibIssue"
    )

    // 1. Setup OkHttp

    val dispatcher = Dispatcher()
    dispatcher.maxRequests = 100
    dispatcher.maxRequestsPerHost = 30
    val client = OkHttpClient.Builder()
      .connectTimeout(10, TimeUnit.SECONDS)
      .writeTimeout(10, TimeUnit.SECONDS)
      .readTimeout(10, TimeUnit.MINUTES)
      .dispatcher(dispatcher)
      .build()

    // 2. Fetch languages list

    val languageCodesRes = client.newCall(Request.Builder()
      .url("https://translations.telegram.org/languages/list/${Telegram.LANGUAGE_PACK}")
      .build()
    ).execute()
    val languageCodes = Klaxon().parse<Languages>(languageCodesRes.body!!.string())!!.codes
    languageCodes.sortWith { a, b -> (b == defaultLanguageCode).compareTo(a == defaultLanguageCode) }

    if (languageCodes[0] != defaultLanguageCode) {
      throw AssertionError("Default language not found: $languageCodes")
    }

    logger.lifecycle("Fetched languages list")

    // 3. Get strings

    val defaultStrings = mutableMapOf<String, String>()
    val threeDotFixKeysList = mutableListOf<Pair<String, List<String>>>()

    val allFolders = mutableListOf<String>()

    val latch = CountDownLatch(languageCodes.size)

    logger.lifecycle("Fetching ${languageCodes.size} languages...")

    for (languageCode in languageCodes) {
      val isDefault = languageCode == defaultLanguageCode

      if (languageCode.isEmpty() || languageCode.startsWith("night") || languageCode.matches(Regex("^(?:v|sw)\\d+.*$"))) {
        throw AssertionError("Bad language code: $languageCode")
      }

      var rtlValue = "0"

      logger.lifecycle("Fetching $languageCode...")

      val callback: Callback = object : Callback {
        override fun onResponse(call: Call, response: Response) {
          val time = measureTimeMillis {
            val strings = XmlParser().parseText(response.body!!.string())

            val threeDotFixKeys = mutableListOf<String>()

            val outputStrings = mutableListOf<Pair<String, String>>()

            for (string in strings["string"] as NodeList) {
              val name = (string as Node)["@name"].toString()
              val value = string.text()

              if (value.contains("...")) {
                threeDotFixKeys.add(name)
              }

              if (!requiredKeys.contains(name))
                continue

              if (isDefault) {
                defaultStrings[name] = value
              } else if (defaultStrings[name] != value) {
                if (name == rtlKey) {
                  rtlValue = value
                } else {
                  outputStrings.add(Pair(name, value))
                }
              }
            }

            if (threeDotFixKeys.isNotEmpty()) {
              threeDotFixKeysList.add(Pair(languageCode, threeDotFixKeys))
            }

            if (!isDefault) {
              val outputFolders = knownOutputFolders[languageCode] ?: when {
                languageCode.contains('-') -> throw UnsupportedOperationException("Unsupported language code: $languageCode")
                else -> arrayOf(languageCode)
              }

              allFolders.addAll(outputFolders)

              for (outputFolder in outputFolders) {
                writeToFile("app/src/main/res/values-${outputFolder}/strings.xml", true) { xml ->
                  xml.append("""
                    <?xml version="1.0" encoding="utf-8"?>
                    <!-- AUTOGENERATED, DO NOT MODIFY -->
                    <resources xmlns:tools="http://schemas.android.com/tools" tools:ignore="MissingTranslation">
                      <string name="suggested_language_code">$languageCode</string>
                      ${if (rtlValue == defaultStrings[rtlKey]) "<!--<string name=\"suggested_language_rtl\">$rtlValue</string>-->" else "<string name=\"suggested_language_rtl\">$rtlValue</string>" }
                      ${outputStrings.joinToString("\n                      ") {
                    if (it.first == nameKey || it.second == defaultStrings[it.first]) "<!--<string name=\"${it.first}\">${it.second}</string>-->" else "<string name=\"${it.first}\">${it.second}</string>"
                  }
                  }
                    </resources>
                  """.trimIndent())
                }
              }
            }
          }

          logger.lifecycle("Processed \"$languageCode\" in ${time}ms")

          latch.countDown()
        }

        override fun onFailure(call: Call, e: IOException) {
          throw e
        }
      }

      val call = client.newCall(Request.Builder()
        .url("https://translations.telegram.org/${languageCode}/${Telegram.LANGUAGE_PACK}/export")
        .build()
      )
      if (isDefault) {
        try {
          val response = call.execute()
          callback.onResponse(call, response)
        } catch (e: IOException) {
          callback.onFailure(call, e)
        }
      } else {
        call.enqueue(callback)
      }
    }

    latch.await()

    val time = measureTimeMillis {
      writeToFile("app/src/main/res/.gitignore") { gitignore ->
        gitignore.append(allFolders.sorted().joinToString("\n") { "values-$it" })
      }
    }
    logger.lifecycle("Updated .gitignore in ${time}ms")

    if (threeDotFixKeysList.isNotEmpty()) {
      val message = StringBuilder("In ${threeDotFixKeysList.size} language(s) \"...\" could be replaced with \"…\" in these keys:")
      for (pair in threeDotFixKeysList) {
        message.append("\n\n")
        message.append(pair.first).append(": ").append(pair.second)
      }
      logger.warn(message.toString())
    }

    logger.lifecycle("Updated all languages")
  }
}