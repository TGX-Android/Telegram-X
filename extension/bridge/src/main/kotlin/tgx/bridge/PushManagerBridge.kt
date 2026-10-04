package tgx.bridge

import android.app.Service
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import org.drinkless.tdlib.TdApi.DeviceToken

interface DeviceTokenRetrieverFactory {
  fun onCreateNewTokenRetriever(context: Context): DeviceTokenRetriever
}

interface PushManagerFactory {
  fun onCreatePushManager(): PushManager
}

interface PushManager {
  fun onNewToken (service: Service, token: DeviceToken)
  fun onMessageReceived (service: Service, message: Map<String, Any>, sentTime: Long, ttl: Int)

  fun log(format: String, vararg args: Any)
  fun error(message: String, error: Throwable?)
}

object PushManagerBridge {
  lateinit var applicationScope: CoroutineScope
  lateinit var managerFactory: PushManagerFactory
  lateinit var deviceTokenRetrieverFactory: DeviceTokenRetrieverFactory

  private val manager by lazy {
    managerFactory.onCreatePushManager()
  }

  @JvmStatic fun initialize (
    applicationScope: CoroutineScope,
    managerFactory: PushManagerFactory,
    deviceTokenRetrieverFactory: DeviceTokenRetrieverFactory
  ) {
    this.applicationScope = applicationScope
    this.managerFactory = managerFactory
    this.deviceTokenRetrieverFactory = deviceTokenRetrieverFactory
  }

  @JvmStatic fun onCreateNewTokenRetriever(context: Context): DeviceTokenRetriever =
    deviceTokenRetrieverFactory.onCreateNewTokenRetriever(context)

  @JvmStatic fun onNewToken (service: Service, token: DeviceToken) =
    manager.onNewToken(service, token)

  @JvmStatic fun onMessageReceived (service: Service, payload: Map<String, Any>, sentTime: Long, ttl: Int) =
    manager.onMessageReceived(service, payload, sentTime, ttl)

  @JvmStatic fun log(format: String, vararg args: Any) =
    manager.log(format, *args)

  @JvmStatic fun error(format: String, t: Throwable?) =
    manager.error(format, t)
}