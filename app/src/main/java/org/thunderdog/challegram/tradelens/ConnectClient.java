/*
 * This file is a part of TradeLens Connect, an unofficial Telegram client derived from Telegram X
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
package org.thunderdog.challegram.tradelens;

import android.net.Uri;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.json.JSONObject;
import org.thunderdog.challegram.telegram.Tdlib;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Talks to the TradeLens Connect API (/api/connect/v1).
 *
 * Telegram signs the launch data (initData) of the TradeLens bot's mini app through TDLib, exactly as official
 * Telegram does. The TradeLens server verifies that signature, so no Telegram password, code or session ever
 * leaves the device. The short-lived token the server returns is held in memory only.
 */
public final class ConnectClient {
  private static final String BOT_USERNAME = "TradeLensagent";
  private static final String SITE = "https://tradelens360ai.live";
  private static final String MINIAPP_URL = SITE + "/tg/";
  private static final String API = SITE + "/api/connect/v1";

  public static final class Status {
    public final boolean linked;
    public final String tier;
    public final String email;
    public final @Nullable String error;

    Status (boolean linked, String tier, String email, @Nullable String error) {
      this.linked = linked;
      this.tier = tier;
      this.email = email;
      this.error = error;
    }
  }

  public interface Callback {
    void onStatus (Status status);
  }

  /** error is one of: "unavailable", "network", "not_linked", "http NNN". Runs on a background thread. */
  public interface Result {
    void onResult (@Nullable JSONObject json, @Nullable String error);
  }

  private ConnectClient () { }

  // The short-lived Connect token is kept in memory only, for the Telegram account that asked for it.
  private static final Object lock = new Object();
  private static String token;
  private static long tokenUser;
  private static long tokenExpiresAt;
  private static JSONObject cachedMe;

  /** Reports whether this Telegram account is linked to a TradeLens account. */
  public static void fetchStatus (Tdlib tdlib, Callback callback) {
    ensureSession(tdlib, true, (t, me, error) -> {
      if (error == null) {
        callback.onStatus(new Status(true, me != null ? me.optString("tier", "") : "", me != null ? me.optString("email", "") : "", null));
      } else if ("not_linked".equals(error)) {
        callback.onStatus(new Status(false, "", "", null));
      } else {
        callback.onStatus(new Status(false, "", "", error));
      }
    });
  }

  /** GET /api/connect/v1/{path} with the Connect token (opening a session first when needed). */
  public static void get (Tdlib tdlib, String path, Result result) {
    getInternal(tdlib, path, result, true);
  }

  private static void getInternal (Tdlib tdlib, String path, Result result, boolean retry) {
    ensureSession(tdlib, false, (t, me, error) -> {
      if (error != null) {
        result.onResult(null, error);
        return;
      }
      new Thread(() -> {
        HttpURLConnection connection = null;
        try {
          connection = (HttpURLConnection) new URL(API + "/" + path).openConnection();
          connection.setRequestMethod("GET");
          connection.setConnectTimeout(15000);
          connection.setReadTimeout(20000);
          connection.setRequestProperty("Authorization", "Bearer " + t);
          int code = connection.getResponseCode();
          if (code == 401 && retry) {
            synchronized (lock) {
              token = null;
            }
            getInternal(tdlib, path, result, false);
            return;
          }
          if (code != 200) {
            result.onResult(null, "http " + code);
            return;
          }
          result.onResult(new JSONObject(readAll(connection.getInputStream())), null);
        } catch (Throwable e) {
          result.onResult(null, "network");
        } finally {
          if (connection != null) {
            connection.disconnect();
          }
        }
      }, "tradelens-connect").start();
    });
  }

  /**
   * POST /api/connect/v1/{path} with a JSON body. With freshSession the app signs in through Telegram again first, because
   * the server only accepts control actions from a Connect token less than 5 minutes old. For an HTTP error the result has the
   * server's JSON message (when there is one) and error "http NNN".
   */
  public static void post (Tdlib tdlib, String path, JSONObject body, boolean freshSession, Result result) {
    ensureSession(tdlib, freshSession, (t, me, error) -> {
      if (error != null) {
        result.onResult(null, error);
        return;
      }
      new Thread(() -> {
        HttpURLConnection connection = null;
        try {
          connection = (HttpURLConnection) new URL(API + "/" + path).openConnection();
          connection.setRequestMethod("POST");
          connection.setConnectTimeout(15000);
          connection.setReadTimeout(20000);
          connection.setDoOutput(true);
          connection.setRequestProperty("Authorization", "Bearer " + t);
          connection.setRequestProperty("Content-Type", "application/json");
          try (OutputStream out = connection.getOutputStream()) {
            out.write(body.toString().getBytes(StandardCharsets.UTF_8));
          }
          int code = connection.getResponseCode();
          if (code == 200) {
            result.onResult(new JSONObject(readAll(connection.getInputStream())), null);
            return;
          }
          JSONObject details = null;
          try {
            details = new JSONObject(readAll(connection.getErrorStream()));
          } catch (Throwable ignored) { }
          result.onResult(details, "http " + code);
        } catch (Throwable e) {
          result.onResult(null, "network");
        } finally {
          if (connection != null) {
            connection.disconnect();
          }
        }
      }, "tradelens-connect").start();
    });
  }

  private interface SessionCallback {
    void onSession (@Nullable String token, @Nullable JSONObject me, @Nullable String error);
  }

  private static void ensureSession (Tdlib tdlib, boolean forceRefresh, SessionCallback callback) {
    final long userId = tdlib.myUserId();
    synchronized (lock) {
      if (!forceRefresh && token != null && tokenUser == userId && System.currentTimeMillis() < tokenExpiresAt - 30000) {
        callback.onSession(token, cachedMe, null);
        return;
      }
    }
    tdlib.send(new TdApi.SearchPublicChat(BOT_USERNAME), (chat, error) -> {
      if (chat == null || chat.type.getConstructor() != TdApi.ChatTypePrivate.CONSTRUCTOR) {
        callback.onSession(null, null, "unavailable");
        return;
      }
      long botUserId = ((TdApi.ChatTypePrivate) chat.type).userId;
      TdApi.WebAppOpenParameters parameters = new TdApi.WebAppOpenParameters(null, "TradeLensConnect", new TdApi.WebAppOpenModeFullSize());
      tdlib.send(new TdApi.OpenWebApp(chat.id, botUserId, MINIAPP_URL, null, null, parameters), (info, openError) -> {
        if (info == null || info.url == null || info.url.url == null) {
          callback.onSession(null, null, "unavailable");
          return;
        }
        final String initData = extractInitData(info.url.url);
        tdlib.send(new TdApi.CloseWebApp(info.launchId), tdlib.typedOkHandler());
        if (initData == null) {
          callback.onSession(null, null, "unavailable");
          return;
        }
        new Thread(() -> openSession(userId, initData, callback), "tradelens-connect").start();
      });
    });
  }

  private static @Nullable String extractInitData (String url) {
    try {
      String fragment = Uri.parse(url).getEncodedFragment();
      if (fragment == null) {
        return null;
      }
      for (String part : fragment.split("&")) {
        if (part.startsWith("tgWebAppData=")) {
          return URLDecoder.decode(part.substring("tgWebAppData=".length()), "UTF-8");
        }
      }
    } catch (Throwable ignored) { }
    return null;
  }

  private static void openSession (long userId, String initData, SessionCallback callback) {
    HttpURLConnection connection = null;
    try {
      connection = (HttpURLConnection) new URL(API + "/session").openConnection();
      connection.setRequestMethod("POST");
      connection.setConnectTimeout(15000);
      connection.setReadTimeout(20000);
      connection.setDoOutput(true);
      connection.setRequestProperty("Content-Type", "application/json");
      byte[] body = new JSONObject().put("init_data", initData).toString().getBytes(StandardCharsets.UTF_8);
      try (OutputStream out = connection.getOutputStream()) {
        out.write(body);
      }
      int code = connection.getResponseCode();
      if (code != 200) {
        callback.onSession(null, null, "http " + code);
        return;
      }
      JSONObject json = new JSONObject(readAll(connection.getInputStream()));
      if (!json.optBoolean("linked", false)) {
        synchronized (lock) {
          token = null;
          cachedMe = null;
        }
        callback.onSession(null, null, "not_linked");
        return;
      }
      JSONObject me = json.optJSONObject("me");
      String newToken = json.optString("token", "");
      long ttl = json.optLong("expires_in", 900);
      synchronized (lock) {
        token = newToken;
        tokenUser = userId;
        tokenExpiresAt = System.currentTimeMillis() + ttl * 1000L;
        cachedMe = me;
      }
      callback.onSession(newToken, me, null);
    } catch (Throwable t) {
      callback.onSession(null, null, "network");
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  private static String readAll (InputStream in) throws java.io.IOException {
    try (InputStream stream = in) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buffer = new byte[4096];
      int n;
      while ((n = stream.read(buffer)) > 0) {
        out.write(buffer, 0, n);
      }
      return out.toString("UTF-8");
    }
  }
}
