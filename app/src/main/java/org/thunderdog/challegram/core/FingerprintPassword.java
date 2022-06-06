/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 02/10/2015 at 22:12
 */
package org.thunderdog.challegram.core;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.CancellationSignal;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.tool.UI;

public class FingerprintPassword {
  public interface Callback {
    void onAuthenticated (int fingerId);
    void onAuthenticationError (String message, boolean isFatal);
  }

  private static @Nullable Object getManager () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      return UI.getAppContext().getSystemService(Context.FINGERPRINT_SERVICE);
    } else {
      return null;
    }
  }

  public static boolean isAvailable () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      android.hardware.fingerprint.FingerprintManager manager = (android.hardware.fingerprint.FingerprintManager) getManager();
      return manager != null && manager.isHardwareDetected();
    } else {
      return false;
    }
  }

  public static boolean hasFingerprints () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      android.hardware.fingerprint.FingerprintManager manager = (android.hardware.fingerprint.FingerprintManager) getManager();
      return manager != null && manager.hasEnrolledFingerprints();
    } else {
      return false;
    }
  }

  private static CancellationSignal signal;
  private static AuthCallback callback;

  @TargetApi(value = Build.VERSION_CODES.M)
  private static class AuthCallback {
    private android.hardware.fingerprint.FingerprintManager.AuthenticationCallback callback;
    private Callback resultCallback;
    private boolean isCancelled;

    public AuthCallback (Callback resultCallback) {
      this.resultCallback = resultCallback;
      this.callback = new android.hardware.fingerprint.FingerprintManager.AuthenticationCallback() {
        @Override
        public void onAuthenticationError (int errorCode, CharSequence errString) {
          if (!isCancelled && signal != null && errString != null) {
            AuthCallback.this.resultCallback.onAuthenticationError(errString.toString(),true);
          }
        }

        @Override
        public void onAuthenticationHelp (int helpCode, CharSequence helpString) {
          if (!isCancelled && signal != null && helpString != null) {
            AuthCallback.this.resultCallback.onAuthenticationError(helpString.toString(), false);
          }
        }

        @Override
        public void onAuthenticationSucceeded (android.hardware.fingerprint.FingerprintManager.AuthenticationResult result) {
          if (!isCancelled) {
            AuthCallback.this.resultCallback.onAuthenticated(0);
          }
        }

        @Override
        public void onAuthenticationFailed () {
          if (!isCancelled) {
            AuthCallback.this.resultCallback.onAuthenticationError(Lang.getString(R.string.fingerprint_fail), false);
          }
        }
      };
    }

    public android.hardware.fingerprint.FingerprintManager.AuthenticationCallback getCallback () {
      return callback;
    }

    public void cancel () {
      isCancelled = true;
    }
  }

  public static void authenticate (Callback resultCallback) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      cancelAuthentication();
      android.hardware.fingerprint.FingerprintManager manager = (android.hardware.fingerprint.FingerprintManager) getManager();
      if (manager != null) {
        signal = new CancellationSignal();
        callback = new AuthCallback(resultCallback);
        android.hardware.fingerprint.FingerprintManager.AuthenticationCallback cb = callback.getCallback();
        try {
          manager.authenticate(null, (android.os.CancellationSignal) signal, 0, cb, null);
        } catch (Throwable t) {
          Log.e("Unable to use fingerprint sensor", t);
          cb.onAuthenticationError(-1, Lang.getString(R.string.BiometricsError));
        }
      }
    }
  }

  public static void cancelAuthentication () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (callback != null) {
        callback.cancel();
        callback = null;
      }
      if (signal != null) {
        try {
          signal.cancel();
        } catch (Throwable t) {
          Log.e("Cannot cancel authentication", t);
        }
        signal = null;
      }
    }
  }
}
