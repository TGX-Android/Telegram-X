package tgx.test

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.thunderdog.challegram.tool.UI
import org.thunderdog.challegram.unsorted.Passcode

private const val TAG = "PasscodeState"

/**
 * The passcode of the app for UI tests, without screens of settings, logging with tag
 * `PasscodeState`, e.g. `adb shell am broadcast -n org.example.tgx/tgx.test.PasscodeReceiver
 * --es action lock --es password <text> --ez biometrics true`: a text password, or `--es pincode
 * <4 digits>`, `--es pattern <digits of dots>` or `--ez gesture true`, unlocked also by biometrics
 * with `biometrics`, its screens seen by screenshots with `--ez screenshots true`, and the app
 * locked at once; `--es action state` logs whether it's locked
 */
class PasscodeReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    when (intent.getStringExtra("action")) {
      "lock" -> UI.post {
        val passcode = Passcode.instance()
        when {
          intent.hasExtra("pincode") -> passcode.setPincode(intent.getStringExtra("pincode"))
          intent.hasExtra("pattern") -> passcode.setPattern(intent.getStringExtra("pattern"))
          intent.getBooleanExtra("gesture", false) -> passcode.setGesture()
          else -> passcode.setPassword(intent.getStringExtra("password").orEmpty())
        }
        if (intent.getBooleanExtra("biometrics", false)) {
          passcode.enableUnlockByBiometrics(0, false)
        }
        // Screens of a locked app are secure, so that checks can see them only with this
        passcode.setAllowScreenshots(intent.getBooleanExtra("screenshots", false))
        if (!passcode.isLocked) {
          passcode.toggleLock()
        }
        Log.i(TAG, "locked ${passcode.isLocked}")
      }
      "state" -> UI.post { Log.i(TAG, "locked ${Passcode.instance().isLocked}") }
      else -> Log.i(TAG, "failed: no action")
    }
  }
}
