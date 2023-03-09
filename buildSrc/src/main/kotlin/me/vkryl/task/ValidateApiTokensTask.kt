/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
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
package me.vkryl.task

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import org.gradle.api.tasks.TaskAction

open class ValidateApiTokensTask : BaseTask() {
  @TaskAction
  fun validateGoogleServicesJsonFile () {
    val parser: Parser = Parser.default()
    val googleServices = parser.parse("app/google-services.json") as JsonObject
    var foundPackageName: String? = null
    val appId = applicationId()
    googleServices.array<JsonObject>("client")!!.filter {
      // client_info.android_client_info.package_name
      val clientInfo = it.obj("client_info")!!
      val googleAppId = clientInfo.string("mobilesdk_app_id")!!
      val clientInfoPackage = clientInfo.obj("android_client_info")?.string("package_name")
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