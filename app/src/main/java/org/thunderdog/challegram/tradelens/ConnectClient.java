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

  private ConnectClient () { }

  /** Opens a session with the TradeLens server and reports whether this Telegram account is linked. Callback runs on a background thread. */
  public static void fetchStatus (Tdlib tdlib, Callback callback) {
    tdlib.send(new TdApi.SearchPublicChat(BOT_USERNAME), (chat, error) -> {
      if (chat == null || chat.type.getConstructor() != TdApi.ChatTypePrivate.CONSTRUCTOR) {
        callback.onStatus(failure());
        return;
      }
      long botUserId = ((TdApi.ChatTypePrivate) chat.type).userId;
      TdApi.WebAppOpenParameters parameters = new TdApi.WebAppOpenParameters(null, "TradeLensConnect", new TdApi.WebAppOpenModeFullSize());
      tdlib.send(new TdApi.OpenWebApp(chat.id, botUserId, MINIAPP_URL, null, null, parameters), (info, openError) -> {
        if (info == null || info.url == null || info.url.url == null) {
          callback.onStatus(failure());
          return;
        }
        final long launchId = info.launchId;
        final String initData = extractInitData(info.url.url);
        tdlib.send(new TdApi.CloseWebApp(launchId), tdlib.typedOkHandler());
        if (initData == null) {
          callback.onStatus(failure());
          return;
        }
        new Thread(() -> callback.onStatus(openSession(initData)), "tradelens-connect").start();
      });
    });
  }

  private static Status failure () {
    return new Status(false, "", "", "unavailable");
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

  private static Status openSession (String initData) {
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
        return new Status(false, "", "", "http " + code);
      }
      JSONObject json = new JSONObject(readAll(connection.getInputStream()));
      if (!json.optBoolean("linked", false)) {
        return new Status(false, "", "", null);
      }
      JSONObject me = json.optJSONObject("me");
      String tier = me != null ? me.optString("tier", "") : "";
      String email = me != null ? me.optString("email", "") : "";
      return new Status(true, tier, email, null);
    } catch (Throwable t) {
      return new Status(false, "", "", "network");
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
