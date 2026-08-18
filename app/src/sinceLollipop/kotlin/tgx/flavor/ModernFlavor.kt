@file:JvmName("Flavor")
@file:JvmMultifileClass

package tgx.flavor

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.Task
import com.google.android.recaptcha.Recaptcha
import com.google.android.recaptcha.RecaptchaTasksClient

typealias Barcode = com.google.mlkit.vision.barcode.common.Barcode

typealias TgxApplication = Application

fun getRecaptchaTasksClient(application: Application, siteKey: String): Task<RecaptchaTasksClient> =
  Recaptcha.fetchTaskClient(application, siteKey)

fun registerReceiver(
  context: Context,
  receiver: BroadcastReceiver,
  intentFilter: IntentFilter,
  isExported: Boolean
): Intent? {
  val flags = if (isExported) {
    ContextCompat.RECEIVER_EXPORTED
  } else {
    ContextCompat.RECEIVER_NOT_EXPORTED
  }
  return ContextCompat.registerReceiver(context, receiver, intentFilter, flags);
}