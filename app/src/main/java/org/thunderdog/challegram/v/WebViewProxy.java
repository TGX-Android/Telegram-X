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
 * File created on 26/01/2017
 */
package org.thunderdog.challegram.v;

import android.webkit.JavascriptInterface;

import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.GameController;
import org.thunderdog.challegram.ui.ShareController;

@SuppressWarnings("unused")
public final class WebViewProxy {
  private final GameController context;

  public WebViewProxy (GameController context) {
    this.context = context;
  }

  @JavascriptInterface
  public final void postEvent (final String eventName, final String eventData) {
    UI.post(() -> {
      if (context.getArguments() != null && context.getArgumentsStrict().message != null) {
        GameController.Args args = context.getArgumentsStrict();

        if ("share_game".equals(eventName)) {
          ShareController c = new ShareController(context.context(), context.tdlib());
          c.setArguments(new ShareController.Args(args.game, args.userId, args.message, false));
          c.show();
        } else if ("share_score".equals(eventName)) {
          ShareController c = new ShareController(context.context(), context.tdlib());
          c.setArguments(new ShareController.Args(args.game, args.userId, args.message, true));
          c.show();
        }
      }
    });
  }
}
