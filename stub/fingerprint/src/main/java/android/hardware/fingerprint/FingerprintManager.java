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
package android.hardware.fingerprint;

import android.os.CancellationSignal;
import android.os.Handler;

// The API of Android 6–9 that compileSdk no longer has, for code that only compiles against it:
// Android has the class itself
public class FingerprintManager {
  public static final int FINGERPRINT_ERROR_CANCELED = 5;

  public boolean isHardwareDetected () {
    throw new RuntimeException("Stub!");
  }

  public boolean hasEnrolledFingerprints () {
    throw new RuntimeException("Stub!");
  }

  public void authenticate (
    CryptoObject crypto,
    CancellationSignal cancel,
    int flags,
    AuthenticationCallback callback,
    Handler handler
  ) {
    throw new RuntimeException("Stub!");
  }

  public static final class CryptoObject {
    private CryptoObject () {
      throw new RuntimeException("Stub!");
    }
  }

  public static class AuthenticationResult {
    private AuthenticationResult () {
      throw new RuntimeException("Stub!");
    }
  }

  public static abstract class AuthenticationCallback {
    public void onAuthenticationError (int errorCode, CharSequence errString) {
      throw new RuntimeException("Stub!");
    }

    public void onAuthenticationHelp (int helpCode, CharSequence helpString) {
      throw new RuntimeException("Stub!");
    }

    public void onAuthenticationSucceeded (AuthenticationResult result) {
      throw new RuntimeException("Stub!");
    }

    public void onAuthenticationFailed () {
      throw new RuntimeException("Stub!");
    }
  }
}
