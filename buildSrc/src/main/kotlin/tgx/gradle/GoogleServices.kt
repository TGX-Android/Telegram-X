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
 * File created on 04/11/2026, 21:40.
 */
package tgx.gradle

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

private fun googleServicesClient(json: JsonObject, applicationId: String): JsonObject? =
  json["client"]!!.jsonArray.map { it.jsonObject }
    .firstOrNull {
      val info = it["client_info"]!!.jsonObject["android_client_info"]!!.jsonObject
      info["package_name"]!!.jsonPrimitive.content == applicationId
    }

fun hasGoogleServicesClient(file: File, applicationId: String): Boolean =
  file.exists() &&
    googleServicesClient(Json.parseToJsonElement(file.readText()).jsonObject, applicationId) != null

fun googleServicesValues(file: File, applicationId: String): Map<String, String> {
  val json = Json.parseToJsonElement(file.readText()).jsonObject
  val project = json["project_info"]!!.jsonObject
  val client = googleServicesClient(json, applicationId)
    ?: fatal("${file.path} has no client of $applicationId")
  val clientInfo = client["client_info"]!!.jsonObject
  val apiKey = client["api_key"]!!.jsonArray.first().jsonObject["current_key"]!!
    .jsonPrimitive.content
  return buildMap {
    put("google_app_id", clientInfo["mobilesdk_app_id"]!!.jsonPrimitive.content)
    put("gcm_defaultSenderId", project["project_number"]!!.jsonPrimitive.content)
    put("google_api_key", apiKey)
    put("google_crash_reporting_api_key", apiKey)
    put("project_id", project["project_id"]!!.jsonPrimitive.content)
    project["firebase_url"]?.jsonPrimitive?.content?.let {
      put("firebase_database_url", it)
    }
    project["storage_bucket"]?.jsonPrimitive?.content?.let {
      put("google_storage_bucket", it)
    }
  }
}
