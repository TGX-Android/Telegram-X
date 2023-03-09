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
 */
package org.thunderdog.challegram.unsorted

import androidx.annotation.DrawableRes
import org.drinkless.td.libcore.telegram.TdApi.*
import org.thunderdog.challegram.R

@DrawableRes
fun Session.asIcon (): Int {
  return if (apiId == 21724) {
    R.drawable.baseline_device_android_x_24
  } else when (type.constructor) {
    SessionTypeFirefox.CONSTRUCTOR -> R.drawable.templarian_device_web_firefox_24
    SessionTypeOpera.CONSTRUCTOR -> R.drawable.baseline_device_web_opera_24
    SessionTypeEdge.CONSTRUCTOR -> R.drawable.templarian_device_web_edge_24
    SessionTypeChrome.CONSTRUCTOR -> R.drawable.templarian_device_web_chrome_24
    SessionTypeSafari.CONSTRUCTOR -> R.drawable.templarian_device_web_safari_24
    SessionTypeAndroid.CONSTRUCTOR -> R.drawable.baseline_device_android_24
    SessionTypeWindows.CONSTRUCTOR -> R.drawable.baseline_device_windows_24
    SessionTypeMac.CONSTRUCTOR -> if (containsModel("macbook")) R.drawable.baseline_device_macbook_24 else R.drawable.baseline_device_imac_24
    SessionTypeIphone.CONSTRUCTOR, SessionTypeApple.CONSTRUCTOR -> R.drawable.baseline_device_iphone_24
    SessionTypeIpad.CONSTRUCTOR -> R.drawable.baseline_device_ipad_24
    SessionTypeXbox.CONSTRUCTOR -> R.drawable.baseline_device_xbox_24
    SessionTypeVivaldi.CONSTRUCTOR -> R.drawable.baseline_device_web_vivaldi_24
    SessionTypeBrave.CONSTRUCTOR -> R.drawable.baseline_device_web_brave_24
    SessionTypeUbuntu.CONSTRUCTOR -> R.drawable.templarian_baseline_device_ubuntu_24
    SessionTypeLinux.CONSTRUCTOR -> R.drawable.baseline_device_linux_24
    SessionTypeUnknown.CONSTRUCTOR -> R.drawable.baseline_device_other_24
    else -> R.drawable.baseline_device_other_24
  }
}

private fun Session.containsModel (target: String) = deviceModel.contains(target, true)