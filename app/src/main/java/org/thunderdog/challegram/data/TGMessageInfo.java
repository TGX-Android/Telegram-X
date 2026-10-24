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
 * File created on 08/10/2026
 */
package org.thunderdog.challegram.data;

import android.view.View;
import android.view.ViewParent;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.component.chat.MessagesAdapter;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.tool.Screen;

public abstract class TGMessageInfo extends TGMessage {
  private MessagesAdapter boundAdapter;
  private int cachedHeight;

  protected TGMessageInfo (MessagesManager context, TdApi.Message message) {
    super(context, message);
  }

  public void setBoundAdapter (MessagesAdapter adapter) {
    this.boundAdapter = adapter;
  }

  public int getCachedHeight () {
    return cachedHeight;
  }

  @Override
  public int getHeight () {
    int totalHeight = getContentHeight() + Screen.dp(6f) + xAvatarRadius * 2 +
      xPaddingBottom - xContentOffset - xPaddingTop + xHeaderPadding;

    final View view = findCurrentView();
    if (view == null) {
      return cachedHeight = totalHeight;
    }

    ViewParent parent = view.getParent();
    if (!(parent instanceof View)) {
      return cachedHeight = totalHeight;
    }

    int height = ((View) parent).getMeasuredHeight();
    if (height == 0 || totalHeight >= height) {
      return cachedHeight = totalHeight;
    }

    int padding = Math.max(0, (int) ((float)
      (height - totalHeight - xPaddingBottom - xHeaderPadding - Screen.dp(6f)) * .5f));

    if (boundAdapter != null) {
      int messageCount = boundAdapter.getMessageCount();
      for (int i = 0; i < messageCount; i++) {
        TGMessage msg = boundAdapter.getMessage(i);
        if (msg != null && msg.getMessage().id != 0) {
          padding -= msg.getHeight();
          if (padding <= 0) {
            return cachedHeight = totalHeight;
          }
        }
      }
    }

    return cachedHeight = totalHeight + padding;
  }
}
