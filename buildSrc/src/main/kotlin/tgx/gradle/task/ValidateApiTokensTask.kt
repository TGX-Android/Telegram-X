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
 * File created on 19/06/2022, 02:06.
 */
package tgx.gradle.task

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.api.tasks.TaskAction
import java.io.File

open class ValidateApiTokensTask : BaseTask() {
  @TaskAction
  fun validateGoogleServicesJsonFile () {
    val appId = applicationId()
    val json = File("app/google-services.json").readText()
    val googleServices = Json.parseToJsonElement(json)
    var foundPackageName: String? = null
    googleServices.jsonObject["client"]!!.jsonArray.filter {
      // client_info.android_client_info.package_name
      val clientInfo = it.jsonObject["client_info"]!!.jsonObject
      val googleAppId = clientInfo["mobilesdk_app_id"]!!.jsonPrimitive.content
      val clientInfoPackage = clientInfo["android_client_info"]!!.jsonObject["package_name"]!!.jsonPrimitive.content
      foundPackageName = clientInfoPackage
      clientInfoPackage == appId && (googleAppId != "1:1037154859800:android:683d617a5fe76437" || clientInfoPackage == "org.thunderdog.challegram")
    }.ifEmpty {
      error(
        "google_services.json is not updated for $appId package. Found: $foundPackageName. " +
        "Notifications in the app won't work without properly updating it."
      )
    }
  }
}