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
 * File created on 25/09/2026
 */
package org.thunderdog.challegram.component.chat;

import android.view.View;

import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.util.text.Text;

public class MessageTextSelection implements Text.SelectionDelegate {
  private final View anchor;
  private final TGMessage message;
  private final Text text;

  public MessageTextSelection (View anchor, TGMessage message, Text text) {
    this.anchor = anchor;
    this.message = message;
    this.text = text;
  }

  public boolean show (float x, float y) {
    return text.startSelection(anchor, x, y, this);
  }

  public boolean dismiss (boolean animated) {
    return text.dismissSelection(animated);
  }

  @Override
  public boolean canSelect () {
    return !message.isDestroyed() && message.canSelectText(text) &&
      anchor instanceof MessagesManager.MessageProvider &&
      ((MessagesManager.MessageProvider) anchor).getMessage() == message;
  }

  @Override
  public float getOffsetX () {
    return message.getTextSelectionOffsetX();
  }
}
