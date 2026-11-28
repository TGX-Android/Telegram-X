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

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.util.text.Text;

import tgx.td.data.MessageWithProperties;

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
  public boolean canQuote () {
    MessagesController controller = message.messagesController();
    if (controller == null || message.isDestroyed() ||
      !controller.canWriteMessagesOrWaitingForReply()) {
      return false;
    }
    Tdlib tdlib = controller.tdlib();
    long chatId = controller.getChatId();
    boolean isAllowedToPost = !controller.isChannel() || tdlib.isAdminOrOwner(chatId);
    return isAllowedToPost && tdlib.canSendBasicMessage(chatId);
  }

  @Override
  public void onQuoteRequested (String quote, int position) {
    MessagesController controller = message.messagesController();
    if (controller == null || !canQuote()) {
      return;
    }
    TdApi.InputTextQuote inputQuote = new TdApi.InputTextQuote(
      new TdApi.FormattedText(quote, null),
      new TdApi.RichText[0],
      position
    );
    controller.showReply(
      new MessageWithProperties(message.getMessage(), message.lastMessageProperties()),
      inputQuote,
      0,
      "",
      true,
      true
    );
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
