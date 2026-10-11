/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.util.Base64;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import org.json.JSONObject;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import static org.thunderdog.challegram.miniapps.MiniAppTheme.object;

/** A dedicated biometric prompt/key per bot; never shares the application's passcode prompt. */
final class MiniAppBiometry {
  private final MiniAppController host;
  private final SharedPreferences preferences;
  private final String alias;
  private BiometricPrompt prompt;
  private Result pending;
  private interface Result { void done (boolean success, String token); }

  MiniAppBiometry (MiniAppController host) {
    this.host = host;
    alias = "miniapp-biometry-" + host.storageScope();
    preferences = host.context().getSharedPreferences(alias, Context.MODE_PRIVATE);
    if (!preferences.contains("device_id")) {
      byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
      StringBuilder id = new StringBuilder(64);
      for (byte b : bytes) id.append(String.format(java.util.Locale.US, "%02x", b & 255));
      preferences.edit().putString("device_id", id.toString()).apply();
    }
  }

  boolean available () {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && BiometricManager.from(host.context())
      .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS;
  }
  boolean granted () { return preferences.getBoolean("granted", false); }
  JSONObject info () {
    JSONObject result = object("available", available(), "access_requested", preferences.getBoolean("requested", false),
      "access_granted", granted(), "token_saved", preferences.contains("token"), "device_id", preferences.getString("device_id", ""));
    if (available()) try { result.put("type", "unknown"); } catch (Exception ignored) { }
    return result;
  }
  void notifyInfo (MiniAppDocument.Request origin) { host.reply(origin, "biometry_info_received", info()); }

  void requestAccess (MiniAppDocument.Request origin, String reason) {
    if (pending != null) return;
    if (preferences.getBoolean("requested", false) || !available()) { notifyInfo(origin); return; }
    String message = Lang.getString(R.string.MiniAppBiometryRequest, host.botName());
    host.nativePrompt(origin, reason.isEmpty() ? Lang.getString(R.string.MiniAppBiometryTitle) : message,
      reason.isEmpty() ? message : reason, R.string.Allow, null, () -> {
        preferences.edit().putBoolean("requested", true).apply();
        authenticate(reason, false, null, (ok, token) -> {
          if (host.current(origin) && ok) preferences.edit().putBoolean("granted", true).apply();
          notifyInfo(origin);
        });
      }, () -> { preferences.edit().putBoolean("requested", true).apply(); notifyInfo(origin); });
  }

  void requestAuth (MiniAppDocument.Request origin, String reason) {
    if (pending != null) return;
    if (!granted()) { host.reply(origin, "biometry_auth_requested", object("status", "failed")); return; }
    authenticate(reason, false, null, (ok, token) -> host.reply(origin, "biometry_auth_requested",
      ok ? object("status", "authorized", "token", token == null ? "" : token) : object("status", "failed")));
  }

  void updateToken (MiniAppDocument.Request origin, String token, String reason) {
    if (pending != null) return;
    if (!granted() || token.length() > 1024) { host.reply(origin, "biometry_token_updated", object("status", "failed")); return; }
    authenticate(reason, true, token, (ok, value) -> host.reply(origin, "biometry_token_updated",
      object("status", ok ? token.isEmpty() ? "removed" : "updated" : "failed")));
  }

  void setGranted (boolean granted) {
    if (!granted) cancel();
    preferences.edit().putBoolean("requested", true).putBoolean("granted", granted).apply();
  }

  private void complete (boolean success, String token) {
    Result result = pending; pending = null; prompt = null;
    if (result != null) result.done(success, token);
  }
  void cancel () {
    BiometricPrompt current = prompt;
    complete(false, null);
    if (current != null) current.cancelAuthentication();
  }

  private void authenticate (String reason, boolean write, String value, Result result) {
    if (!available() || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) { result.done(false, null); return; }
    pending = result;
    try {
      final boolean decrypt = !write && preferences.contains("token");
      Cipher cipher = cipher(decrypt);
      prompt = new BiometricPrompt(host.context(), androidx.core.content.ContextCompat.getMainExecutor(host.context()), new BiometricPrompt.AuthenticationCallback() {
        @Override public void onAuthenticationError (int error, @NonNull CharSequence message) { complete(false, null); }
        @Override public void onAuthenticationSucceeded (@NonNull BiometricPrompt.AuthenticationResult result) {
          if (pending == null) return;
          try {
            Cipher authenticated = result.getCryptoObject() != null ? result.getCryptoObject().getCipher() : null;
            if (authenticated == null) { complete(false, null); return; }
            if (write) {
              SharedPreferences.Editor edit = preferences.edit();
              if (value.isEmpty()) edit.remove("token").remove("iv");
              else edit.putString("token", Base64.encodeToString(authenticated.doFinal(value.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP))
                .putString("iv", Base64.encodeToString(authenticated.getIV(), Base64.NO_WRAP));
              if (!edit.commit()) { complete(false, null); return; }
              complete(true, null);
            } else {
              String token = decrypt ? new String(authenticated.doFinal(Base64.decode(preferences.getString("token", ""), Base64.NO_WRAP)), StandardCharsets.UTF_8) : "";
              complete(true, token);
            }
          } catch (Exception e) { complete(false, null); }
        }
      });
      BiometricPrompt.PromptInfo.Builder info = new BiometricPrompt.PromptInfo.Builder()
        .setTitle(host.botName()).setNegativeButtonText(Lang.getString(R.string.MiniAppBack))
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG);
      if (!reason.isEmpty()) info.setDescription(reason.substring(0, Math.min(128, reason.length())));
      prompt.authenticate(info.build(), new BiometricPrompt.CryptoObject(cipher));
    } catch (KeyPermanentlyInvalidatedException e) {
      // Enrolling/removing biometrics invalidates the old token; it must never be read with a new key.
      preferences.edit().remove("token").remove("iv").putBoolean("granted", false).putBoolean("requested", false).apply();
      try { KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null); store.deleteEntry(alias); } catch (Exception ignored) { }
      complete(false, null);
    } catch (Exception e) { complete(false, null); }
  }

  @RequiresApi(Build.VERSION_CODES.M)
  private Cipher cipher (boolean decrypt) throws Exception {
    KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
    if (!store.containsAlias(alias)) {
      KeyGenParameterSpec.Builder spec = new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setUserAuthenticationRequired(true);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) spec.setInvalidatedByBiometricEnrollment(true);
      KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
      generator.init(spec.build()); generator.generateKey();
    }
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    SecretKey key = (SecretKey) store.getKey(alias, null);
    if (decrypt) cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Base64.decode(preferences.getString("iv", ""), Base64.NO_WRAP)));
    else cipher.init(Cipher.ENCRYPT_MODE, key);
    return cipher;
  }
}
