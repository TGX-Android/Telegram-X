package tgx.extension

import android.app.Application
import com.huawei.hms.push.HmsMessaging
import tgx.bridge.Extension

object TelegramXExtension : Extension("hms") {
  override fun configure(application: Application, googlePlayServicesAvailable: Boolean) {
    if (googlePlayServicesAvailable) {
      // Prefer GMS over HMS
      HmsMessaging.getInstance(application).isAutoInitEnabled = false
    }
  }

  // Disable GMS if Google Play Services are unavailable
  override fun shouldDisableFirebaseMessaging(googlePlayServicesAvailable: Boolean): Boolean =
    !googlePlayServicesAvailable
}