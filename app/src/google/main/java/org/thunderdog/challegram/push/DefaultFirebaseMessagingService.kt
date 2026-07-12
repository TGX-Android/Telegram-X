package org.thunderdog.challegram.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.thunderdog.challegram.TDLib
import org.thunderdog.challegram.telegram.TdlibAccount
import org.thunderdog.challegram.telegram.TdlibManager
import org.thunderdog.challegram.tool.UI
import tgx.bridge.PushManagerBridge

abstract class DefaultFirebaseMessagingService : FirebaseMessagingService() {

  override fun onMessageReceived(remoteMessage: RemoteMessage) {
    val payload = makePayload(remoteMessage)
    val sentTime: Long = remoteMessage.getSentTime()
    val ttl: Int = remoteMessage.getTtl()
    PushManagerBridge.onMessageReceived(this, payload, sentTime, ttl)
  }

  override fun onDeletedMessages() {
    UI.initApp(applicationContext)
    TDLib.Tag.notifications("onDeletedMessages: performing sync for all accounts")
    TdlibManager.makeSync(applicationContext, TdlibAccount.NO_ID, TdlibManager.SYNC_CAUSE_DELETED_MESSAGES, 0, !TdlibManager.inUiThread(), 0)
  }

  companion object {
    private fun makePayload(remoteMessage: RemoteMessage): Map<String, Any> {
      val payload = mutableMapOf<String, Any>()
      payload["google.sent_time"] = remoteMessage.getSentTime()
      val notification = remoteMessage.getNotification()
      if (notification != null) {
        notification.sound?.takeIf { it.isNotEmpty() }?.let { sound ->
          payload["google.notification.sound"] = sound
        }
      } else {
        val intent = remoteMessage.toIntent()
        val extras = intent.extras
        if (extras != null) {
          val sound =
            extras.getString("gcm.n.sound2") ?:
            extras.getString("gcm.n.sound") ?:
            ""
          if (sound.isNotEmpty()) {
            payload["google.notification.sound"] = sound
          }
        }
      }
      val data = remoteMessage.getData()
      if (data != null) {
        payload.putAll(data)
      }
      return payload
    }
  }
}