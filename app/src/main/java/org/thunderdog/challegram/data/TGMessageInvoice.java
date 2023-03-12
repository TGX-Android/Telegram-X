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
 * File created on 11/02/2017
 */
package org.thunderdog.challegram.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessagesManager;

public class TGMessageInvoice extends TGMessage {
/*MessageInvoice {
  title = Working Time Machine
  description = Want to visit your great-great-great-grandparents? Make a fortune at the races? Shake hands with Hammurabi and take a stroll in the Hanging Gardens? Order our Working Time Machine today!
  photo = Photo {
    id = 0
    hasStickers = false
    sizes = [PhotoSize {
  type = u
  photo = File {
    id = 378
    persistentId = AgACAQQAA0podHRwczovL3RlbGVncmFtLm9yZy9maWxlLzgxMTE0MDgyNy8xL04wQWJMeTJCRVdBLjQ5MDMxLzk5ZmIwMzY3MWUzM2MzY2QzYgABzL_wHFTqGfwC
    size = 0
    isBeingDownloaded = false
    localSize = 0
    isBeingUploaded = false
    remoteSize = 0
    path =
  }
  width = 650
  height = 490
}]
  }
  currency = JPY
  totalAmount = 14322
  startParameter = buy_tshirt
  isTest = true
  needShippingAddress = true
  receiptMessageId = 0
}*/

  private MediaWrapper mediaWrapper;

  public TGMessageInvoice (MessagesManager context, TdApi.Message msg, TdApi.MessageInvoice invoice) {
    super(context, msg);
    setPhoto(invoice.photo);
  }

  @Override
  public void autoDownloadContent (TdApi.ChatType type) {
    if (mediaWrapper != null) {
      mediaWrapper.getFileProgress().downloadAutomatically(type);
    }
  }

  private void setPhoto (@Nullable TdApi.Photo photo) {
    if (mediaWrapper != null) {
      mediaWrapper.destroy();
    }
    if (photo != null) {
      mediaWrapper = new MediaWrapper(context(), tdlib, photo, msg.chatId, msg.id, this, false);
    } else {
      mediaWrapper = null;
    }
  }

  @Override
  protected void onMessageIdChanged (long oldMessageId, long newMessageId, boolean success) {
    if (mediaWrapper != null) {
      mediaWrapper.updateMessageId(oldMessageId, newMessageId, success);
    }
  }

  @Override
  protected void onMessageAttachedToView (@NonNull MessageView view, boolean attached) {
    if (mediaWrapper != null) {
      mediaWrapper.getFileProgress().notifyInvalidateTargetsChanged();
    }
  }

  @Override
  protected void onMessageContainerDestroyed () {
    setPhoto(null);
  }

  @Override
  protected void buildContent (int maxWidth) {

  }
}
