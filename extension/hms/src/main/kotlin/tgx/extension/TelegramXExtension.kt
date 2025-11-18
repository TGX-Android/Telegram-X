package tgx.extension

import android.app.Application
import android.content.Context
import com.huawei.hms.push.HmsMessaging
import tgx.bridge.DeviceTokenRetriever
import tgx.bridge.Extension
import tgx.extension.hms.HuaweiDeviceTokenRetriever
import tgx.extension.hms.obtainHuaweiAppId

object TelegramXExtension : Extension("hms") {
  override fun createNewTokenRetriever(context: Context): DeviceTokenRetriever? {
    val appId = obtainHuaweiAppId(context)
    return if (appId.isNotEmpty()) {
      // Enable HMS if Google Play Services are unavailable, and HMS is properly configured.
      HuaweiDeviceTokenRetriever(appId)
    } else {
      null
    }
  }

  override fun configure(application: Application, deviceTokenRetriever: DeviceTokenRetriever) {
    if (deviceTokenRetriever !is HuaweiDeviceTokenRetriever) {
      HmsMessaging.getInstance(application).isAutoInitEnabled = false
    }
  }
}