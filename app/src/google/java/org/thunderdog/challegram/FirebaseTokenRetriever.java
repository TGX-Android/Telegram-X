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
 * File created on 22/10/2022
 */
package org.thunderdog.challegram;

import android.content.Context;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.util.TokenRetriever;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.vkryl.core.StringUtils;

public final class FirebaseTokenRetriever extends TokenRetriever {
  @Override
  protected boolean performInitialization (Context context) {
    try {
      TDLib.Tag.notifications("FirebaseApp is initializing...");
      if (FirebaseApp.initializeApp(context) != null) {
        TDLib.Tag.notifications("FirebaseApp initialization finished successfully");
        return true;
      } else {
        TDLib.Tag.notifications("FirebaseApp initialization failed");
      }
    } catch (Throwable e) {
      TDLib.Tag.notifications("FirebaseApp initialization failed with error: %s", Log.toString(e));
    }
    return false;
  }

  private static String extractFirebaseErrorName (Throwable e) {
    String message = e.getMessage();
    if (!StringUtils.isEmpty(message)) {
      Matcher matcher = Pattern.compile("(?<=: )[A-Z_]+$").matcher(message);
      if (matcher.find()) {
        return matcher.group();
      }
    }
    return e.getClass().getSimpleName();
  }

  @Override
  protected void fetchDeviceToken (int retryCount, RegisterCallback callback) {
    try {
      TDLib.Tag.notifications("FirebaseMessaging: requesting token... retryCount: %d", retryCount);
      FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
        TDLib.Tag.notifications("FirebaseMessaging: successfully fetched token: \"%s\"", token);
        callback.onSuccess(new TdApi.DeviceTokenFirebaseCloudMessaging(token, true));
      }).addOnFailureListener(e -> {
        String errorName = extractFirebaseErrorName(e);
        TDLib.Tag.notifications(
          "FirebaseMessaging: token fetch failed with remote error: %s, retryCount: %d",
          !StringUtils.isEmpty(errorName) ? errorName : Log.toString(e),
          retryCount
        );
        callback.onError(errorName, e);
      });
    } catch (Throwable e) {
      TDLib.Tag.notifications("FirebaseMessaging: token fetch failed with error: %s, retryCount: %d", Log.toString(e), retryCount);
      callback.onError("FIREBASE_REQUEST_ERROR", e);
    }
  }
}