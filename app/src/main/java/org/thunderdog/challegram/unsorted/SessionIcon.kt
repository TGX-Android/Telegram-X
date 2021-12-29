package org.thunderdog.challegram.unsorted

import org.drinkless.td.libcore.telegram.TdApi
import org.thunderdog.challegram.R

/*
  Android X:
- apiId check

Android, Windows, Linux, Ubuntu:
- platform field match

iMac, iPhone, iPad, MacBook, iWatch:
- platform + deviceModel field match

Chrome, Edge, Firefox, Safari:
- platform or deviceModel (needs further researching)

other cases - Other is used
   */

fun TdApi.Session.asIcon () = when {
  apiId == 21724 -> R.drawable.baseline_device_android_x
  containsPlatform("android") -> R.drawable.baseline_device_android
  containsPlatform("windows") -> R.drawable.baseline_device_windows
  containsPlatform("mac") -> if (containsModel("macbook")) R.drawable.baseline_device_macbook else R.drawable.baseline_device_imac
  containsPlatform("ios") -> if (containsModel("ipad")) R.drawable.baseline_device_ipad else R.drawable.baseline_device_iphone
  containsModel("edg") -> R.drawable.templarian_device_web_edge // Chrome-based Edge names itself as "Edg", while EdgeHTML one is "Edge"
  containsModel("chrome") -> R.drawable.templarian_device_web_chrome
  containsModel("firefox") -> R.drawable.templarian_device_web_firefox
  containsModel("safari") -> R.drawable.templarian_device_web_safari
  else -> R.drawable.baseline_device_other
}

private fun TdApi.Session.containsModel (target: String) = deviceModel.contains(target, true)
private fun TdApi.Session.containsPlatform (target: String) = platform.contains(target, true) || systemVersion.contains(target, true)