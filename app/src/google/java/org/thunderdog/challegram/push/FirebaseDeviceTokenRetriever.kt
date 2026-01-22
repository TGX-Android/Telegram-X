package org.thunderdog.challegram.push

import android.content.Context
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import me.vkryl.core.isEmpty
import org.drinkless.tdlib.TdApi.DeviceTokenFirebaseCloudMessaging
import org.thunderdog.challegram.U
import tgx.bridge.DeviceTokenRetriever
import tgx.bridge.PushManagerBridge
import tgx.bridge.TokenRetrieverListener
import java.util.regex.Pattern

class FirebaseDeviceTokenRetriever : DeviceTokenRetriever("firebase") {
  override fun isAvailable(context: Context): Boolean =
    U.isGooglePlayServicesAvailable(context)

  override fun performInitialization(context: Context): Boolean {
    try {
      PushManagerBridge.log("FirebaseApp is initializing...")
      if (FirebaseApp.initializeApp(context) != null) {
        PushManagerBridge.log("FirebaseApp initialization finished successfully")
        return true
      } else {
        PushManagerBridge.log("FirebaseApp initialization failed")
      }
    } catch (e: Throwable) {
      PushManagerBridge.error("FirebaseApp initialization failed with error", e)
    }
    return false
  }

  override fun fetchDeviceToken(context: Context, listener: TokenRetrieverListener) {
    try {
      PushManagerBridge.log("FirebaseMessaging: requesting token...")
      FirebaseMessaging.getInstance().token.addOnSuccessListener(OnSuccessListener { token ->
        PushManagerBridge.log("FirebaseMessaging: successfully fetched token: \"%s\"", token)
        listener.onTokenRetrievalSuccess(DeviceTokenFirebaseCloudMessaging(token, true))
      }).addOnFailureListener(OnFailureListener { e: Exception? ->
        val errorName = extractFirebaseErrorName(e!!)
        PushManagerBridge.error(
          "FirebaseMessaging: token fetch failed ($errorName)",
          e
        )
        listener.onTokenRetrievalError(errorName, e)
      })
    } catch (e: Throwable) {
      PushManagerBridge.error("FirebaseMessaging: token fetch failed with error", e)
      listener.onTokenRetrievalError("FIREBASE_REQUEST_ERROR", e)
    }
  }

  companion object {
    fun extractFirebaseErrorName(e: Throwable): String {
      val message: String = e.message!!
      if (!isEmpty(message)) {
        val matcher = Pattern.compile("(?<=: )[A-Z_]+$").matcher(message)
        if (matcher.find()) {
          return matcher.group()
        }
      }
      return e.javaClass.getSimpleName()
    }
  }
}