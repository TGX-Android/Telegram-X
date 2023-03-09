/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 05/12/2016
 */
package org.thunderdog.challegram.data;

import androidx.annotation.NonNull;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;

public class InlineResultButton extends InlineResult<TdApi.InlineQueryResultsButton> {
  private final long userId;
  private long sourceChatId;

  public InlineResultButton (BaseActivity context, Tdlib tdlib, long inlineBotId, @NonNull TdApi.InlineQueryResultsButton button) {
    super(context, tdlib, TYPE_BUTTON, null, button);
    this.userId = inlineBotId;
  }

  public long getSourceChatId () {
    return sourceChatId;
  }

  public void setSourceChatId (long chatId) {
    this.sourceChatId = chatId;
  }

  public long getUserId () {
    return userId;
  }

  public @NonNull String getText () {
    return data.text;
  }

  public String botStartParameter () {
    if (data.type.getConstructor() == TdApi.InlineQueryResultsButtonTypeStartBot.CONSTRUCTOR) {
      return ((TdApi.InlineQueryResultsButtonTypeStartBot) data.type).parameter;
    } else {
      return "";
    }
  }

  @Override
  protected int getContentHeight () {
    return Screen.dp(36f) + Screen.dp(1f);
  }
}
