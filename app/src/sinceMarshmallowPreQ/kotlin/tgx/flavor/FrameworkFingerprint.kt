/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
@file:JvmName("FrameworkFingerprint")

package tgx.flavor

import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.biometric.BiometricManager
import org.thunderdog.challegram.Log
import org.thunderdog.challegram.R
import org.thunderdog.challegram.core.BiometricAuthentication
import org.thunderdog.challegram.core.Lang
import org.thunderdog.challegram.unsorted.AppContext

private const val LATE_CANCEL_MS = 1_000L

private fun findFingerprintManager(): FingerprintManager? {
  // Before Android 10 androidx.biometric looks for the sensor by FingerprintManagerCompat, which
  // finds none since androidx.core 1.18.0, and before it on Android 7–9 only where the firmware
  // declares android.hardware.fingerprint, which some firmwares don't. Builds of these flavors
  // other than release install on newer Android too
  if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
    return null
  }
  return AppContext.get().getSystemService(FingerprintManager::class.java)?.takeIf {
    try {
      it.isHardwareDetected
    } catch (t: Throwable) {
      Log.e("Unable to detect fingerprint sensor", t)
      false
    }
  }
}

fun canAuthenticate(status: Int): Int {
  if (status == BiometricManager.BIOMETRIC_SUCCESS) {
    return status
  }
  val manager = findFingerprintManager() ?: return status
  val hasEnrolledFingerprints = try {
    manager.hasEnrolledFingerprints()
  } catch (t: Throwable) {
    Log.e("Unable to check enrolled fingerprints", t)
    false
  }
  return if (hasEnrolledFingerprints) {
    BiometricManager.BIOMETRIC_SUCCESS
  } else {
    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
  }
}

fun authenticator(
  strong: Boolean,
  callback: BiometricAuthentication.Callback,
  onComplete: Runnable
): BiometricAuthentication.Authenticator? {
  val authenticators = if (strong) {
    BiometricManager.Authenticators.BIOMETRIC_STRONG
  } else {
    BiometricManager.Authenticators.BIOMETRIC_WEAK
  }
  // The prompt of androidx works where only its check can't tell the sensor, the status unknown
  // of Android 9 with the feature declared
  val status = BiometricManager.from(AppContext.get()).canAuthenticate(authenticators)
  if (status != BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE &&
    status != BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE) {
    return null
  }
  val manager = findFingerprintManager() ?: return null
  return FingerprintAuthenticator(manager, strong, callback, onComplete)
}

// FingerprintManager keeps the callback of its last session for the life of the app, so the
// callback lets go of the screen when its session ends
private class FingerprintAuthenticator(
  private val manager: FingerprintManager,
  private val strong: Boolean,
  private var callback: BiometricAuthentication.Callback?,
  private var onComplete: Runnable?
) : FingerprintManager.AuthenticationCallback(), BiometricAuthentication.Authenticator {
  private val signal = CancellationSignal()
  private var startTime = 0L

  override fun authenticate() {
    startTime = SystemClock.uptimeMillis()
    try {
      manager.authenticate(null, signal, 0, this, null)
    } catch (t: Throwable) {
      Log.e("Unable to use fingerprint sensor", t)
      onAuthenticationError(-1, Lang.getString(R.string.BiometricsError))
    }
  }

  override fun cancel() {
    signal.cancel()
    release()
  }

  private fun release() {
    callback = null
    onComplete = null
  }

  private fun complete() {
    onComplete?.run()
    release()
  }

  override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
    // FingerprintManager gives events to the callback of its last session, also the end of a
    // session cancelled just before this one started
    if (errorCode == FingerprintManager.FINGERPRINT_ERROR_CANCELED &&
      SystemClock.uptimeMillis() - startTime < LATE_CANCEL_MS) {
      return
    }
    val callback = callback ?: return
    callback.onAuthenticationError(
      if (errString.isNullOrEmpty()) "Error $errorCode" else errString,
      true
    )
    complete()
  }

  override fun onAuthenticationHelp(helpCode: Int, helpString: CharSequence?) {
    if (!helpString.isNullOrEmpty()) {
      callback?.onAuthenticationError(helpString, false)
    }
  }

  override fun onAuthenticationSucceeded(result: FingerprintManager.AuthenticationResult?) {
    val callback = callback ?: return
    callback.onAuthenticated(strong)
    complete()
  }

  override fun onAuthenticationFailed() {
    callback?.onAuthenticationError(Lang.getString(R.string.fingerprint_fail), false)
  }
}
