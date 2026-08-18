@file:JvmName("Flavor")
@file:JvmMultifileClass

package tgx.flavor

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.multidex.MultiDexApplication
import com.google.android.gms.tasks.Task
import com.google.android.recaptcha.Recaptcha
import com.google.android.recaptcha.RecaptchaTasksClient

typealias Barcode = com.google.mlkit.vision.barcode.Barcode

typealias TgxApplication = MultiDexApplication

fun getRecaptchaTasksClient(application: Application, siteKey: String): Task<RecaptchaTasksClient> =
  Recaptcha.getTasksClient(application, siteKey)

@Suppress("UnspecifiedRegisterReceiverFlag")
fun registerReceiver(
  context: Context,
  receiver: BroadcastReceiver,
  intentFilter: IntentFilter,
  isExported: Boolean
): Intent? {
  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    val flags = if (isExported) {
      Context.RECEIVER_EXPORTED
    } else {
      Context.RECEIVER_NOT_EXPORTED
    }
    context.registerReceiver(receiver, intentFilter, flags)
  } else {
    context.registerReceiver(receiver, intentFilter)
  }
}