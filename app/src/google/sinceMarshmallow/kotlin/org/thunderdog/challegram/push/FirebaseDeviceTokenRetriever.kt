package org.thunderdog.challegram.push

import android.content.Context
import com.google.android.gms.tasks.OnSuccessListener
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import org.drinkless.tdlib.TdApi.DeviceTokenFirebaseCloudMessaging
import org.thunderdog.challegram.service.DefaultFirebaseTokenRetriever
import tgx.bridge.PushManagerBridge
import tgx.bridge.TokenRetrieverListener

class FirebaseDeviceTokenRetriever : DefaultFirebaseTokenRetriever() {
  override fun fetchDeviceToken(context: Context, listener: TokenRetrieverListener) {
    try {
      PushManagerBridge.log("FirebaseMessaging: registering...")
      FirebaseMessaging.getInstance().register().addOnSuccessListener(OnSuccessListener { _ ->
        PushManagerBridge.log("FirebaseMessaging: successfully registered, obtaining installation ID...")
        FirebaseInstallations.getInstance().id
          .addOnSuccessListener { installationId ->
            PushManagerBridge.log("FirebaseMessaging: successfully fetched installation ID: \"%s\"", installationId)
            listener.onTokenRetrievalSuccess(DeviceTokenFirebaseCloudMessaging(installationId, true))
          }
          .addOnFailureListener { e: Exception? ->
            val errorName = extractFirebaseErrorName(e!!)
            PushManagerBridge.error(
              "FirebaseMessaging: installation ID fetch failed ($errorName)",
              e
            )
            listener.onTokenRetrievalError(errorName, e)
          }
      }).addOnFailureListener { e: Exception? ->
        val errorName = extractFirebaseErrorName(e!!)
        PushManagerBridge.error(
          "FirebaseMessaging: registration failed ($errorName)",
          e
        )
        listener.onTokenRetrievalError(errorName, e)
      }
    } catch (e: Throwable) {
      PushManagerBridge.error("FirebaseMessaging: token fetch failed with error", e)
      listener.onTokenRetrievalError("FIREBASE_REQUEST_ERROR", e)
    }
  }
}