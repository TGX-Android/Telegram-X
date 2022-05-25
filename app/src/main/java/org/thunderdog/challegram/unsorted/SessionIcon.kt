package org.thunderdog.challegram.unsorted

import androidx.annotation.DrawableRes
import org.drinkless.td.libcore.telegram.TdApi
import org.thunderdog.challegram.R

@DrawableRes
fun TdApi.Session.asIcon (): Int {
  return if (apiId == 21724) {
    R.drawable.baseline_device_android_x
  } else when (type.constructor) {
    TdApi.SessionTypeFirefox.CONSTRUCTOR -> R.drawable.templarian_device_web_firefox
    TdApi.SessionTypeOpera.CONSTRUCTOR -> R.drawable.baseline_device_web_opera
    TdApi.SessionTypeEdge.CONSTRUCTOR -> R.drawable.templarian_device_web_edge
    TdApi.SessionTypeChrome.CONSTRUCTOR -> R.drawable.templarian_device_web_chrome
    TdApi.SessionTypeSafari.CONSTRUCTOR -> R.drawable.templarian_device_web_safari
    TdApi.SessionTypeAndroid.CONSTRUCTOR -> R.drawable.baseline_device_android
    TdApi.SessionTypeWindows.CONSTRUCTOR -> R.drawable.baseline_device_windows
    TdApi.SessionTypeMac.CONSTRUCTOR -> if (containsModel("macbook")) R.drawable.baseline_device_macbook else R.drawable.baseline_device_imac
    TdApi.SessionTypeIphone.CONSTRUCTOR -> R.drawable.baseline_device_iphone
    TdApi.SessionTypeIpad.CONSTRUCTOR -> R.drawable.baseline_device_ipad
    else -> R.drawable.baseline_device_other
  }
}

private fun TdApi.Session.containsModel (target: String) = deviceModel.contains(target, true)