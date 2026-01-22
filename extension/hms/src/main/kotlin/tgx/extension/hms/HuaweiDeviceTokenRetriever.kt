package tgx.extension.hms

import android.content.Context
import com.huawei.hms.aaid.HmsInstanceId
import com.huawei.hms.api.ConnectionResult
import com.huawei.hms.api.HuaweiApiAvailability
import com.huawei.hms.push.HmsMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi.DeviceTokenHuaweiPush
import tgx.bridge.DeviceTokenRetriever
import tgx.bridge.PushManagerBridge
import tgx.bridge.TokenRetrieverListener


class HuaweiDeviceTokenRetriever(val appId: String) : DeviceTokenRetriever("hms") {
  override fun isAvailable(context: Context): Boolean =
    HuaweiApiAvailability.getInstance().isHuaweiMobileNoticeAvailable(context) == ConnectionResult.SUCCESS

  override fun performInitialization(context: Context): Boolean {
    PushManagerBridge.log("HMS: initializing...")
    try {
      return if (appId.isNotEmpty()) {
        PushManagerBridge.log("HMS: initialized app_id: %s", appId)
        true
      } else {
        PushManagerBridge.log("HMS: initialization failed: missing app_id")
        return false
      }
    } catch (e: Throwable) {
      PushManagerBridge.error("HMS: initialization failed with error", e)
      return false
    }
  }

  override fun fetchDeviceToken(context: Context, listener: TokenRetrieverListener) {
    PushManagerBridge.applicationScope.launch {
      withContext(Dispatchers.IO) {
        try {
          PushManagerBridge.log("HMS: requesting token...")
          val token = HmsInstanceId.getInstance(context)
            .getToken(appId, HmsMessaging.DEFAULT_TOKEN_SCOPE)
          PushManagerBridge.log("HMS: successfully fetched token: %s", token)
          withContext(Dispatchers.Main) {
            listener.onTokenRetrievalSuccess(DeviceTokenHuaweiPush(token, true))
          }
        } catch (e: Throwable) {
          PushManagerBridge.error("HMS: token fetch failed with error", e)
          withContext(Dispatchers.Main) {
            listener.onTokenRetrievalError("HUAWEI_REQUEST_ERROR", e)
          }
        }
      }
    }
  }
}