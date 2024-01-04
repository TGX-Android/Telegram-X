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
 * File created on 12/11/2023, 15:38.
 */

package org.thunderdog.challegram.util;

import android.graphics.Canvas;
import android.view.View;

import androidx.annotation.Px;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.ComplexReceiverProvider;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.td.Td;

public class AvatarDrawModifier implements DrawModifier {
  final int size;

  public AvatarDrawModifier (int sizeDp) {
    this.size = sizeDp;
  }

  @Override
  public void afterDraw (View view, Canvas c) {
    ComplexReceiver complexReceiver = view instanceof ComplexReceiverProvider ? ((ComplexReceiverProvider) view).getComplexReceiver() : null;
    if (complexReceiver == null) return;

    AvatarReceiver avatarReceiver = complexReceiver.getAvatarReceiver(0);
    if (avatarReceiver.isEmpty()) return;

    int s = Screen.dp(size);
    int x = Screen.dp(18);
    int y = (view.getMeasuredHeight() - s) / 2;

    avatarReceiver.setBounds(x, y, x + s, y + s);
    if (avatarReceiver.needPlaceholder()) {
      avatarReceiver.drawPlaceholder(c);
    }
    avatarReceiver.draw(c);
  }

  public AvatarDrawModifier requestFiles (ComplexReceiver complexReceiver, Tdlib tdlib, TdApi.MessageSender sender) {
    AvatarReceiver avatarReceiver = complexReceiver.getAvatarReceiver(0);
    avatarReceiver.requestMessageSender(tdlib, sender, AvatarReceiver.Options.NONE);
    return this;
  }

  @Override
  public int getWidth () {
    return 0;
  }
}
