package tgx.flavor

import android.app.Application
import com.google.android.gms.tasks.Task
import com.google.android.recaptcha.Recaptcha
import com.google.android.recaptcha.RecaptchaTasksClient

typealias Barcode = com.google.mlkit.vision.barcode.common.Barcode

fun getRecaptchaTasksClient(application: Application, siteKey: String): Task<RecaptchaTasksClient> =
  Recaptcha.fetchTaskClient(application, siteKey)