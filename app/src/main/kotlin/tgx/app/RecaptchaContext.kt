package tgx.app

import android.app.Application
import com.google.android.recaptcha.Recaptcha
import com.google.android.recaptcha.RecaptchaTasksClient
import org.thunderdog.challegram.BuildConfig
import org.thunderdog.challegram.telegram.Tdlib

private typealias Callback = (RecaptchaTasksClient?, Exception?) -> Unit

class RecaptchaContext @JvmOverloads constructor(
  val application: Application,
  val recaptchaKeyId: String? = BuildConfig.RECAPTCHA_KEY_ID
) {
  private var initializationStarted: Boolean = false
  private var client: RecaptchaTasksClient? = null
  private var fatalError: Exception? = null
  private val pendingCallbacks: ArrayDeque<Callback> = ArrayDeque()

  val isInitialized: Boolean
    get() = client != null || fatalError != null

  fun initialize () {
    if (initializationStarted)
      return
    initializationStarted = true
    recaptchaKeyId.takeIf { !it.isNullOrEmpty() }?.let { key ->
      Recaptcha.getTasksClient(application, key)
        .addOnSuccessListener {
          client = it
          executeScheduledTasks()
        }
        .addOnFailureListener {
          fatalError = it
          executeScheduledTasks()
        }
    } ?: {
      fatalError = Tdlib.ApplicationVerificationException("RECAPTCHA_FAILED_NO_KEY_ID")
      executeScheduledTasks()
    }
  }

  fun withClient(callback: Callback) {
    if (isInitialized) {
      callback(client, fatalError)
    } else {
      pendingCallbacks.addLast(callback)
      initialize()
    }
  }

  private fun executeScheduledTasks () {
    if (!isInitialized)
      return
    while (pendingCallbacks.isNotEmpty()) {
      val callback = pendingCallbacks.removeFirst()
      callback(client, fatalError)
    }
  }
}