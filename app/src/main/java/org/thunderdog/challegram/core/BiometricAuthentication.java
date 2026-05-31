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
 *
 * File created on 02/10/2015 at 22:12
 */
package org.thunderdog.challegram.core;

import android.os.Build;
import android.os.SystemClock;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.os.CancellationSignal;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.tool.UI;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.CancellableRunnable;

public class BiometricAuthentication {
  public static final boolean ONLY_FINGERPRINT = Build.VERSION.SDK_INT < Build.VERSION_CODES.R;

  public interface Callback {
    void onAuthenticated (BiometricPrompt.AuthenticationResult result, boolean strong);
    void onAuthenticationError (CharSequence message, boolean isFatal);
  }

  private static @Nullable BiometricManager getManager () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      return BiometricManager.from(UI.getAppContext());
    } else {
      return null;
    }
  }

  @Retention(RetentionPolicy.SOURCE)
  @IntDef(value = {
    Availability.UNAVAILABLE,
    Availability.STRONG,
    Availability.WEAK
  }, flag = true)
  public @interface Availability {
    int
      UNAVAILABLE = 0,
      STRONG = 1,
      WEAK = 1 << 1;

    @Availability int MASK = STRONG | WEAK;
  }

  private static boolean isAvailable (int availability, boolean onlyEnrolled) {
    switch (availability) {
      case BiometricManager.BIOMETRIC_SUCCESS:
        return true;
      case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
        return !onlyEnrolled;
    }
    return false;
  }

  public static boolean isStrongAvailable (boolean onlyEnrolled) {
    BiometricManager biometricManager = getManager();
    return biometricManager != null &&
      isAvailable(biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG), onlyEnrolled);
  }

  @Availability
  public static int getAvailability (boolean onlyEnrolled) {
    BiometricManager biometricManager = getManager();
    if (biometricManager != null) {
      boolean strongAvailability = isAvailable(biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG), onlyEnrolled);
      boolean weakAvailability = isAvailable(biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK), onlyEnrolled);
      return
        BitwiseUtils.optional(Availability.STRONG, strongAvailability) |
        BitwiseUtils.optional(Availability.WEAK, weakAvailability);
    }
    return Availability.UNAVAILABLE;
  }

  public static boolean isAvailable () {
    return BitwiseUtils.hasFlag(getAvailability(false), Availability.MASK);
  }

  public static boolean isMissingEnrolledData (boolean strong) {
    @Availability int availability = getAvailability(true);
    return !BitwiseUtils.hasFlag(availability, strong ? Availability.STRONG : Availability.MASK);
  }

  private static class BiometricsCallback extends BiometricPrompt.AuthenticationCallback {
    private final Callback callback;
    private final boolean strong;

    public BiometricsCallback (Callback callback, boolean strong) {
      this.callback = callback;
      this.strong = strong;
    }

    @Override
    public final void onAuthenticationError (int errorCode, @NonNull CharSequence errString) {
      callback.onAuthenticationError(StringUtils.isEmpty(errString) ? "Error " + errorCode : errString, true);
      onComplete();
    }

    @Override
    public final void onAuthenticationSucceeded (@NonNull BiometricPrompt.AuthenticationResult result) {
      int authenticationType = result.getAuthenticationType();
      switch (authenticationType) {
        case BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL:
        case BiometricPrompt.AUTHENTICATION_RESULT_TYPE_UNKNOWN:
          callback.onAuthenticationError("Use biometrics instead of lock screen credentials.", true);
          break;
        case BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC:
          callback.onAuthenticated(result, strong);
          break;
        default:
          throw new UnsupportedOperationException(Integer.toString(authenticationType));
      }
      onComplete();
    }

    @Override
    public final void onAuthenticationFailed () {
      callback.onAuthenticationError("Try again.", false);
    }

    protected void onComplete () { }
  }

  private static CancellationSignal pendingSignal;
  private static long lastTimestamp;

  public static void authenticate (BaseActivity context, CharSequence title, CharSequence negativeButtonText, boolean strong, Callback callback) {
    CancellationSignal signal = new CancellationSignal();
    BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
      .setConfirmationRequired(false)
      .setAllowedAuthenticators(
        strong ? BiometricManager.Authenticators.BIOMETRIC_STRONG : BiometricManager.Authenticators.BIOMETRIC_WEAK
      )
      .setTitle(title)
      .setNegativeButtonText(negativeButtonText)
      .build();
    BiometricPrompt prompt = new BiometricPrompt(context, new BiometricsCallback(callback, strong) {
      @Override
      protected void onComplete () {
        lastTimestamp = SystemClock.uptimeMillis();
        if (pendingSignal == signal) {
          pendingSignal = null;
        }
      }
    });
    pendingSignal = signal;
    CancellableRunnable act = new CancellableRunnable() {
      @Override
      public void act () {
        lastTimestamp = SystemClock.uptimeMillis();
        prompt.authenticate(info);
      }
    };
    signal.setOnCancelListener(() -> {
      prompt.cancelAuthentication();
      act.cancel();
      if (pendingSignal == signal) {
        pendingSignal = null;
      }
    });

    long delay = lastTimestamp == 0 ? 0 : Math.max(0L, Math.min(600L, SystemClock.uptimeMillis() - lastTimestamp));
    if (delay > 0) {
      UI.post(act, delay);
    } else {
      act.run();
    }
  }

  public static void cancelAuthentication () {
    if (pendingSignal != null) {
      pendingSignal.cancel();
      pendingSignal = null;
    }
  }
}
