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
 * File created on 03/09/2022, 18:42.
 */

package org.thunderdog.challegram.data;

import androidx.annotation.NonNull;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.util.text.FormattedText;
import org.thunderdog.challegram.util.text.TextColorSet;

import me.vkryl.core.lambda.Filter;

abstract class TGMessageServiceImpl extends TGMessage {
  protected TGMessageServiceImpl (MessagesManager manager, TdApi.Message msg) {
    super(manager, msg);
  }

  @NonNull
  protected abstract TextColorSet defaultTextColorSet ();

  protected interface ServiceMessageCreator {
    FormattedText createText ();
  }

  private ServiceMessageCreator textCreator;

  protected void setTextCreator (ServiceMessageCreator textCreator) {
    this.textCreator = textCreator;
  }

  protected interface OnClickListener {
    void onClick ();
  }

  private OnClickListener onClickListener;

  protected void setOnClickListener (OnClickListener onClickListener) {
    this.onClickListener = onClickListener;
  }

  private TdApi.ChatPhoto chatPhoto;

  protected void setDisplayChatPhoto (TdApi.ChatPhoto chatPhoto) {
    this.chatPhoto = chatPhoto;
  }

  public void setDisplayMessage (long chatId, long messageId, Filter<TdApi.Message> callback) {
    tdlib.client().send(new TdApi.GetMessage(chatId, messageId), result -> {
      if (result.getConstructor() == TdApi.Message.CONSTRUCTOR) {
        TdApi.Message message = (TdApi.Message) result;
        runOnUiThreadOptional(() -> {
          if (callback.accept(message)) {
            // subscribe to further updates
            rebuildAndUpdateContent();
          }
        });
      }
    });
  }
}
