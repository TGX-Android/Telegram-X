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
package org.thunderdog.challegram.billing;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import com.android.billingclient.api.AccountIdentifiers;
import com.android.billingclient.api.Purchase;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibManager;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Handles serialization and deserialization of billing payloads.
 * Payloads contain account information and purchase purpose for
 * server-side verification.
 *
 * Design inspired by official Telegram clients (GPL-licensed).
 * Implementation is original code.
 *
 * SECURITY NOTE: This class handles sensitive purchase data.
 * Never log payload contents or purchase tokens.
 */
public final class BillingPayloadHandler {
  private static final String TAG = "BillingPayload";

  private final Context context;
  private final SecureRandom secureRandom;

  public BillingPayloadHandler(@NonNull Context context) {
    this.context = context.getApplicationContext();
    this.secureRandom = new SecureRandom();
  }

  /**
   * Creates obfuscated payload for billing flow.
   *
   * @param purpose   The store payment purpose (e.g., Premium subscription)
   * @param accountId The TDLib account ID
   * @return Pair of (obfuscatedAccountId, obfuscatedData)
   */
  @NonNull
  public Pair<String, String> createPayload(
    @NonNull TdApi.StorePaymentPurpose purpose,
    int accountId
  ) {
    // Create obfuscated account ID
    TdlibAccount account = TdlibManager.instance().account(accountId);
    String accountIdString;
    if (account != null && account.getKnownUserId() != 0) {
      accountIdString = String.valueOf(account.getKnownUserId());
    } else {
      accountIdString = "account-" + accountId;
    }
    String obfuscatedAccountId = Base64.encodeToString(
      accountIdString.getBytes(StandardCharsets.UTF_8),
      Base64.NO_WRAP
    );

    // Generate unique payload ID and save purpose
    long payloadId = generatePayloadId();
    String obfuscatedData = savePayload(payloadId, purpose, accountId);

    if (BillingConfig.DEBUG_BILLING) {
      Log.d(TAG, "Created payload for account %d, purpose type: %s",
        accountId, purpose.getClass().getSimpleName());
    }

    return Pair.create(obfuscatedAccountId, obfuscatedData);
  }

  /**
   * Extracts payload from a completed purchase.
   *
   * @param purchase The purchase to extract payload from
   * @return Pair of (accountId, purpose), or null if extraction fails
   */
  @Nullable
  public Pair<Integer, TdApi.StorePaymentPurpose> extractPayload(@NonNull Purchase purchase) {
    AccountIdentifiers identifiers = purchase.getAccountIdentifiers();
    if (identifiers == null) {
      if (BillingConfig.DEBUG_BILLING) {
        Log.w(TAG, "No account identifiers in purchase");
      }
      return null;
    }

    String obfuscatedAccountId = identifiers.getObfuscatedAccountId();
    String obfuscatedData = identifiers.getObfuscatedProfileId();

    if (obfuscatedAccountId == null || obfuscatedAccountId.isEmpty() ||
        obfuscatedData == null || obfuscatedData.isEmpty()) {
      if (BillingConfig.DEBUG_BILLING) {
        Log.w(TAG, "Empty account identifiers");
      }
      return null;
    }

    try {
      // Decode account ID
      byte[] accountIdBytes = Base64.decode(obfuscatedAccountId, Base64.NO_WRAP);
      String accountIdString = new String(accountIdBytes, StandardCharsets.UTF_8);

      int accountId;
      if (accountIdString.startsWith("account-")) {
        accountId = Integer.parseInt(accountIdString.substring(8));
      } else {
        // It's a user ID, find the corresponding account
        long userId = Long.parseLong(accountIdString);
        accountId = findAccountByUserId(userId);
        if (accountId < 0) {
          if (BillingConfig.DEBUG_BILLING) {
            Log.w(TAG, "Account not found for user");
          }
          return null;
        }
      }

      // Load purpose from stored data
      TdApi.StorePaymentPurpose purpose = loadPayload(obfuscatedData);
      if (purpose == null) {
        if (BillingConfig.DEBUG_BILLING) {
          Log.w(TAG, "Failed to load purpose from payload");
        }
        return null;
      }

      return Pair.create(accountId, purpose);
    } catch (Exception e) {
      Log.e(TAG, "Failed to extract payload", e);
      return null;
    }
  }

  /**
   * Clears stored payload data for a purchase.
   * Should be called after successful server verification.
   */
  public void clearPayload(@NonNull Purchase purchase) {
    AccountIdentifiers identifiers = purchase.getAccountIdentifiers();
    if (identifiers == null || identifiers.getObfuscatedProfileId() == null) {
      return;
    }

    try {
      String obfuscatedData = identifiers.getObfuscatedProfileId();
      long payloadId = decodePayloadId(obfuscatedData);

      SharedPreferences prefs = getPrefs();
      prefs.edit()
        .remove(getPayloadKey(payloadId))
        .remove(getAccountKey(payloadId))
        .apply();

      if (BillingConfig.DEBUG_BILLING) {
        Log.d(TAG, "Cleared payload");
      }
    } catch (Exception e) {
      Log.e(TAG, "Failed to clear payload", e);
    }
  }

  // Private helper methods

  private long generatePayloadId() {
    return secureRandom.nextLong();
  }

  private String savePayload(long payloadId, TdApi.StorePaymentPurpose purpose, int accountId) {
    // Serialize purpose to bytes
    byte[] purposeData = serializePurpose(purpose);
    if (purposeData == null) {
      // Fallback: just store ID, we'll need to recover purpose differently
      purposeData = new byte[0];
    }

    // Encode payload ID for transmission
    String payloadIdHex = Long.toHexString(payloadId);

    // Store full data locally (purpose might be too large for obfuscatedProfileId)
    SharedPreferences prefs = getPrefs();
    prefs.edit()
      .putString(getPayloadKey(payloadId), Base64.encodeToString(purposeData, Base64.NO_WRAP))
      .putInt(getAccountKey(payloadId), accountId)
      .apply();

    return payloadIdHex;
  }

  @Nullable
  private TdApi.StorePaymentPurpose loadPayload(String obfuscatedData) {
    try {
      long payloadId = decodePayloadId(obfuscatedData);

      SharedPreferences prefs = getPrefs();
      String purposeBase64 = prefs.getString(getPayloadKey(payloadId), null);

      if (purposeBase64 == null || purposeBase64.isEmpty()) {
        return null;
      }

      byte[] purposeData = Base64.decode(purposeBase64, Base64.NO_WRAP);
      return deserializePurpose(purposeData);
    } catch (Exception e) {
      Log.e(TAG, "Failed to load payload", e);
      return null;
    }
  }

  private long decodePayloadId(String obfuscatedData) {
    return Long.parseUnsignedLong(obfuscatedData, 16);
  }

  private String getPayloadKey(long payloadId) {
    return "purpose_" + payloadId;
  }

  private String getAccountKey(long payloadId) {
    return "account_" + payloadId;
  }

  private SharedPreferences getPrefs() {
    return context.getSharedPreferences(BillingConfig.BILLING_PREFS_NAME, Context.MODE_PRIVATE);
  }

  private int findAccountByUserId(long userId) {
    return TdlibManager.instance().accountIdForUserId(userId, 0);
  }

  @Nullable
  private byte[] serializePurpose(TdApi.StorePaymentPurpose purpose) {
    // Simple serialization: store constructor ID and relevant data
    // For StorePaymentPurposePremiumSubscription, we mainly need the flags
    if (purpose instanceof TdApi.StorePaymentPurposePremiumSubscription) {
      TdApi.StorePaymentPurposePremiumSubscription sub =
        (TdApi.StorePaymentPurposePremiumSubscription) purpose;
      // Store: constructor ID (4 bytes) + isRestore flag (1 byte) + isUpgrade flag (1 byte)
      byte[] data = new byte[6];
      int constructor = TdApi.StorePaymentPurposePremiumSubscription.CONSTRUCTOR;
      data[0] = (byte) (constructor & 0xFF);
      data[1] = (byte) ((constructor >> 8) & 0xFF);
      data[2] = (byte) ((constructor >> 16) & 0xFF);
      data[3] = (byte) ((constructor >> 24) & 0xFF);
      data[4] = (byte) (sub.isRestore ? 1 : 0);
      data[5] = (byte) (sub.isUpgrade ? 1 : 0);
      return data;
    }
    // Add other purpose types as needed
    return null;
  }

  @Nullable
  private TdApi.StorePaymentPurpose deserializePurpose(byte[] data) {
    if (data == null || data.length < 4) {
      return null;
    }

    int constructor = (data[0] & 0xFF) |
                     ((data[1] & 0xFF) << 8) |
                     ((data[2] & 0xFF) << 16) |
                     ((data[3] & 0xFF) << 24);

    if (constructor == TdApi.StorePaymentPurposePremiumSubscription.CONSTRUCTOR) {
      TdApi.StorePaymentPurposePremiumSubscription purpose =
        new TdApi.StorePaymentPurposePremiumSubscription();
      if (data.length >= 6) {
        purpose.isRestore = data[4] != 0;
        purpose.isUpgrade = data[5] != 0;
      }
      return purpose;
    }

    // Add other purpose types as needed
    return null;
  }
}
