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
 * File created on 26/01/2017
 */
package org.thunderdog.challegram.v;

import android.webkit.JavascriptInterface;

import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.GameController;
import org.thunderdog.challegram.ui.ShareController;

@SuppressWarnings("unused")
public final class WebViewProxy {
  private final ClientCallback callback;

  public WebViewProxy (ClientCallback callback) {
    this.callback = callback;
  }

  @JavascriptInterface
  public final void postEvent (final String eventName, final String eventData) {
    UI.post(() -> {
      switch (eventName) {
        case "share_game":
        case "share_score": {
          callback.shareGame(eventName.equals("share_score"));
          break;
        }

        case "game_over": {
          callback.gameOver();
          break;
        }

        case "game_loaded": {
          callback.gameLoaded();
          break;
        }

        case "payment_form_submit": {
          callback.submitPaymentForm(eventData);
          break;
        }
      }
    });
  }

  public interface ClientCallback {
    default void shareGame (boolean withScore) {}
    default void gameOver () {}
    default void gameLoaded () {}

    default void submitPaymentForm (String jsonData) {}
  }
}
