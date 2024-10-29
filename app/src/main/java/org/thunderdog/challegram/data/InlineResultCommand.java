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
 * File created on 03/12/2016
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.inline.CustomResultView;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import tgx.td.Td;

public class InlineResultCommand extends InlineResult<TdApi.BotCommand> {
  private final UserContext userContext;

  public InlineResultCommand (BaseActivity context, Tdlib tdlib, long userId, TdApi.BotCommand botCommand) {
    super(context, tdlib, TYPE_COMMAND, null, botCommand);
    this.userContext = new UserContext(tdlib, userId);
  }

  public InlineResultCommand (BaseActivity context, Tdlib tdlib, TdApi.User user, TdApi.BotCommand botCommand) {
    super(context, tdlib, TYPE_COMMAND, null, botCommand);
    this.userContext = new UserContext(tdlib, user);
  }

  @Nullable
  public String getUsername () {
    return Td.primaryUsername(getUsernames());
  }

  @Nullable
  public TdApi.Usernames getUsernames () {
    return userContext.getUser() != null ? userContext.getUser().usernames : null;
  }

  public String getCommand () {
    return "/" + data.command;
  }

  public boolean matchesPrefix (String prefix) {
    return data.command.startsWith(prefix);
  }

  private int commandWidth;
  private String trimmedCommand;
  private @Nullable String trimmedDesc;

  @Override
  protected void layoutInternal (int contentWidth) {
    userContext.measureTexts(12f, null);

    String cmd = "/" + data.command;

    if (commandWidth == 0) {
      commandWidth = (int) U.measureText(cmd, Paints.getCommandPaint());
    }

    int availWidth = contentWidth - Screen.dp(14f) * 2 /* avatar */ - Screen.dp(14f) * 2 /* horizontal view padding */ - Screen.dp(12f) /* image right padding */ - commandWidth - Screen.dp(12f) /* command padding right */;
    if (availWidth <= 0) {
      trimmedCommand = TextUtils.ellipsize(cmd, Paints.getCommandPaint(), availWidth + commandWidth + Screen.dp(12f), TextUtils.TruncateAt.END).toString();
      trimmedDesc = null;
    } else {
      trimmedCommand = cmd;
      trimmedDesc = !data.description.isEmpty() ? TextUtils.ellipsize(data.description,  Paints.getCommandPaint(), availWidth, TextUtils.TruncateAt.END).toString() : null;
    }
  }

  @Override
  protected int getContentHeight () {
    return Screen.dp(4f) * 2 + Screen.dp(14f) * 2;
  }

  @Override
  public void requestContent (ComplexReceiver receiver, boolean isInvalidate) {
    receiver.clearReceivers((receiverType, receiver1, key) -> receiverType == ComplexReceiver.ReceiverType.IMAGE && key == 0);
    receiver.getImageReceiver(0).requestFile(userContext.getImageFile());
  }

  @Override
  protected void drawInternal (CustomResultView view, Canvas c, ComplexReceiver receiver, int viewWidth, int viewHeight, int startY) {
    ImageReceiver imageReceiver = receiver.getImageReceiver(0);
    imageReceiver.setRadius(Screen.dp(14f));
    imageReceiver.setBounds(Screen.dp(14f), startY + Screen.dp(4f), Screen.dp(14f) + Screen.dp(14f) * 2, startY + Screen.dp(4f) + Screen.dp(14f) * 2);
    if (userContext.hasPhoto()) {
      if (imageReceiver.needPlaceholder()) {
        imageReceiver.drawPlaceholder(c);
      }
      imageReceiver.draw(c);
    } else {
      userContext.drawPlaceholder(c, Screen.dp(14f), Screen.dp(14f), startY + Screen.dp(4f), 12f);
    }
    int startX = Screen.dp(14f) * 3 + Screen.dp(12f);
    startY += Screen.dp(4f) + Screen.dp(14f) + Screen.dp(5f);
    if (trimmedCommand != null) {
      c.drawText(trimmedCommand, startX, startY, Paints.getCommandPaint(Theme.textAccentColor()));
      startX += commandWidth + Screen.dp(12f);
    }
    if (trimmedDesc != null) {
      c.drawText(trimmedDesc, startX, startY, Paints.getCommandPaint(Theme.textDecentColor()));
    }
  }
}
