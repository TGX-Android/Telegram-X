/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.testapp.controller

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.media.session.MediaController
import android.os.Bundle
import androidx.media3.common.util.Util
import androidx.media3.session.SessionToken

/** Stores details about a media app. */
class MediaAppDetails
private constructor(
  val packageName: String,
  val appName: String,
  val icon: Bitmap,
  val sessionToken: SessionToken,
  val supportsAutomotive: Boolean,
  val supportsAuto: Boolean,
) {

  companion object {
    fun create(
      packageManager: PackageManager,
      resources: Resources,
      sessionToken: SessionToken,
    ): MediaAppDetails {
      val packageName = sessionToken.packageName
      val appInfo = packageManager.getApplicationInfo(packageName, /* flags= */ 0)
      val appName = appInfo.loadLabel(packageManager).toString()
      val icon =
        BitmapUtils.convertDrawable(resources, appInfo.loadIcon(packageManager), downScale = true)
      return MediaAppDetails(
        packageName,
        appName,
        icon,
        sessionToken,
        getSupportsAutomotive(packageManager, packageName),
        getSupportsAuto(packageManager, packageName),
      )
    }

    fun create(
      packageManager: PackageManager,
      resources: Resources,
      controller: MediaController,
      context: Context,
    ): MediaAppDetails {
      val sessionToken = SessionToken.createSessionToken(context, controller.sessionToken).get()
      val packageName = sessionToken.packageName
      val appInfo = packageManager.getApplicationInfo(packageName, 0)
      val appName = appInfo.loadLabel(packageManager).toString()
      val icon =
        BitmapUtils.convertDrawable(resources, appInfo.loadIcon(packageManager), downScale = true)
      return MediaAppDetails(
        packageName,
        appName,
        icon,
        sessionToken,
        getSupportsAutomotive(packageManager, packageName),
        getSupportsAuto(packageManager, packageName),
      )
    }

    private fun getSupportsAutomotive(
      packageManager: PackageManager,
      packageName: String,
    ): Boolean {
      val features =
        packageManager.getPackageInfo(packageName, PackageManager.GET_CONFIGURATIONS).reqFeatures
      return features?.any { it?.name == "android.hardware.type.automotive" } == true
    }

    private fun getSupportsAuto(packageManager: PackageManager, packageName: String): Boolean {
      val metaData =
        packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA).metaData
      return metaData?.containsKey("com.google.android.gms.car.application") == true
    }

    private val PACKAGE_NAME = Util.intToStringMaxRadix(0)
    private val APP_NAME = Util.intToStringMaxRadix(1)
    private val ICON = Util.intToStringMaxRadix(2)
    private val SESSION_TOKEN = Util.intToStringMaxRadix(3)
    private val SUPPORTS_AUTO = Util.intToStringMaxRadix(4)
    private val SUPPORTS_AUTOMOTIVE = Util.intToStringMaxRadix(5)

    fun fromBundle(bundle: Bundle): MediaAppDetails {
      return MediaAppDetails(
        bundle.getString(PACKAGE_NAME)!!,
        bundle.getString(APP_NAME)!!,
        (bundle.getParcelable(ICON) as Bitmap?)!!,
        SessionToken.fromBundle(bundle.getBundle(SESSION_TOKEN)!!),
        bundle.getBoolean(SUPPORTS_AUTO),
        bundle.getBoolean(SUPPORTS_AUTOMOTIVE),
      )
    }
  }

  fun toBundle(): Bundle {
    val bundle = Bundle()
    bundle.putString(PACKAGE_NAME, packageName)
    bundle.putString(APP_NAME, appName)
    bundle.putParcelable(ICON, icon)
    bundle.putBundle(SESSION_TOKEN, sessionToken.toBundle())
    bundle.putBoolean(SUPPORTS_AUTO, supportsAuto)
    bundle.putBoolean(SUPPORTS_AUTOMOTIVE, supportsAutomotive)
    return bundle
  }
}
