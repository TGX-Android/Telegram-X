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

import android.widget.Toast;

import androidx.annotation.Nullable;

import org.json.JSONObject;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;

/**
 * The two safe-direction controls (switch AutoTrading off, make equity protection stricter). The TradeLens server decides
 * everything: it checks the request (prepare), returns the text to show, and only changes something after the member confirms
 * that exact text (confirm). The app never changes a setting by itself and never trusts its own idea of the result: it shows
 * what the server answers, and reloads the screen from the server afterwards.
 */
public final class TLControls {
  private TLControls () { }

  public static void run (ViewController<?> controller, Tdlib tdlib, String action, int account, @Nullable JSONObject params, Runnable onDone) {
    JSONObject body = new JSONObject();
    try {
      body.put("action", action).put("account", String.valueOf(account));
      if (params != null) {
        body.put("params", params);
      }
    } catch (Throwable ignored) { }
    ConnectClient.post(tdlib, "controls/prepare", body, true, (json, error) -> UI.post(() -> {
      if (controller.isDestroyed()) {
        return;
      }
      String code = json != null ? json.optString("code", "") : "";
      if (error != null || code.isEmpty()) {
        fail(error, json);
        return;
      }
      controller.showConfirm(json.optString("summary", "Confirm this change?"), "Confirm", () -> confirm(controller, tdlib, code, onDone));
    }));
  }

  private static void confirm (ViewController<?> controller, Tdlib tdlib, String code, Runnable onDone) {
    JSONObject body = new JSONObject();
    try {
      body.put("code", code);
    } catch (Throwable ignored) { }
    ConnectClient.post(tdlib, "controls/confirm", body, false, (json, error) -> UI.post(() -> {
      if (error != null || json == null || !json.optBoolean("ok", false)) {
        fail(error, json);
      } else {
        UI.showToast("Done. TradeLens saved it (reference " + json.optLong("audit_id", 0) + ").", Toast.LENGTH_LONG);
      }
      if (!controller.isDestroyed()) {
        onDone.run();
      }
    }));
  }

  private static void fail (@Nullable String error, @Nullable JSONObject json) {
    String detail = json != null ? json.optString("detail", "") : "";
    String text;
    if ("not_linked".equals(error)) {
      text = "Link your TradeLens account first.";
    } else if ("network".equals(error) || "unavailable".equals(error)) {
      text = "Couldn't reach TradeLens. Nothing was changed.";
    } else if ("http 404".equals(error) && detail.isEmpty() || "Not found".equals(detail)) {
      text = "Changes from the app aren't switched on yet. Use the TradeLens website.";
    } else if ("http 401".equals(error)) {
      text = "Please try again.";
    } else if (!detail.isEmpty()) {
      text = detail;
    } else {
      text = "TradeLens couldn't make that change. Nothing was changed.";
    }
    UI.showToast(text, Toast.LENGTH_LONG);
  }
}
