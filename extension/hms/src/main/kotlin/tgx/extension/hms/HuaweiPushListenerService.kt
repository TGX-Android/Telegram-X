package tgx.extension.hms

import com.huawei.hms.push.HmsMessageService
import com.huawei.hms.push.RemoteMessage
import org.drinkless.tdlib.TdApi.DeviceTokenHuaweiPush
import tgx.bridge.PushManagerBridge

class HuaweiPushListenerService : HmsMessageService() {
  override fun onNewToken(token: String) =
    PushManagerBridge.onNewToken(this, DeviceTokenHuaweiPush(token, true))

  override fun onMessageReceived(remoteMessage: RemoteMessage) {
    val payload = mutableMapOf<String, Any>()

    payload["huawei.sent_time"] = remoteMessage.sentTime
    payload["p"] = remoteMessage.data

    PushManagerBridge.onMessageReceived(this, payload, remoteMessage.sentTime, remoteMessage.ttl)
  }
}