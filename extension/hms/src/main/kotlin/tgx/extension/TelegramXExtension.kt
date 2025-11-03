package tgx.extension

import android.app.Application
import android.content.Context
import com.huawei.hms.push.HmsMessaging
import tgx.bridge.DeviceTokenRetriever
import tgx.bridge.Extension
import tgx.extension.hms.HuaweiDeviceTokenRetriever
import tgx.extension.hms.obtainHuaweiAppId

object TelegramXExtension : Extension("hms") {
  override fun createNewTokenRetriever(context: Context, googlePlayServicesAvailable: Boolean): DeviceTokenRetriever? {
    if (!googlePlayServicesAvailable) {
      val appId = obtainHuaweiAppId(context)
      if (appId.isNotEmpty()) {
        // Enable HMS if Google Play Services are unavailable, and HMS is properly configured.
        return HuaweiDeviceTokenRetriever(appId)
      }
    }
    // Disable HMS if Google Play Services are available, or HMS is not properly configured.
    return null
  }

  override fun configure(application: Application, deviceTokenRetriever: DeviceTokenRetriever) {
    if (deviceTokenRetriever !is HuaweiDeviceTokenRetriever) {
      HmsMessaging.getInstance(application).isAutoInitEnabled = false
    }
  }
}