package org.thunderdog.challegram.unsorted

import androidx.annotation.DrawableRes
import org.drinkless.td.libcore.telegram.TdApi.*
import org.thunderdog.challegram.R

@DrawableRes
fun Session.asIcon (): Int {
  return if (apiId == 21724) {
    R.drawable.baseline_device_android_x
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
    SessionTypeLinux.CONSTRUCTOR, // TODO: find a nice-looking penguin
    SessionTypeUnknown.CONSTRUCTOR -> R.drawable.baseline_device_other_24
    else -> R.drawable.baseline_device_other_24
  }
}

private fun Session.containsModel (target: String) = deviceModel.contains(target, true)