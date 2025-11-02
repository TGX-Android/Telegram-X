package tgx.bridge

import android.app.Service
import org.drinkless.tdlib.TdApi.DeviceToken

interface PushReceiver {
  fun onNewToken (service: Service, token: DeviceToken)
  fun onMessageReceived (service: Service, message: Map<String, Any>, sentTime: Long, ttl: Int)
}

object PushReceiverBridge {
  private var receiver: PushReceiver? = null

  @JvmStatic fun registerReceiver (receiver: PushReceiver) {
    this.receiver = receiver
  }

  @JvmStatic fun onNewToken (service: Service, token: DeviceToken) {
    receiver!!.onNewToken(service, token)
  }

  @JvmStatic fun onMessageReceived (service: Service, payload: Map<String, Any>, sentTime: Long, ttl: Int) {
    receiver!!.onMessageReceived(service, payload, sentTime, ttl)
  }
}