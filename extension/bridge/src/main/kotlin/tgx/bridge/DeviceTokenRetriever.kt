package tgx.bridge

import android.content.Context
import org.drinkless.tdlib.TdApi.DeviceToken

interface TokenRetrieverListener {
  fun onTokenRetrievalSuccess(token: DeviceToken)
  fun onTokenRetrievalError(errorKey: String, e: Throwable?)
}

abstract class DeviceTokenRetriever(
  @JvmField val name: String
) {
  private var isInitialized = false

  fun initialize(context: Context): Boolean {
    return if (isInitialized) {
      true
    } else {
      val result = performInitialization(context)
      if (result) {
        isInitialized = true
      }
      result
    }
  }

  fun retrieveDeviceToken(context: Context, listener: TokenRetrieverListener) {
    if (!isInitialized)
      error("Not initialized")
    fetchDeviceToken(context, listener)
  }

  open val configuration: String = ""
  abstract fun isAvailable(context: Context): Boolean
  protected abstract fun performInitialization(context: Context): Boolean
  protected abstract fun fetchDeviceToken(context: Context, listener: TokenRetrieverListener)
}

fun DeviceTokenRetriever?.isAvailable(context: Context) =
  this?.isAvailable(context) ?: false