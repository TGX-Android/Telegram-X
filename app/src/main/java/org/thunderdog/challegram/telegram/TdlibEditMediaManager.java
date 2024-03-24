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
 * File created on 09/02/2024 at 18:34
 */
package org.thunderdog.challegram.telegram;

import androidx.annotation.NonNull;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.component.attach.MediaToReplacePickerManager;
import org.thunderdog.challegram.tool.UI;

import java.util.HashMap;

public class TdlibEditMediaManager implements MessageEditMediaPending.Callback {
  private final Tdlib tdlib;
  private final HashMap<String, MessageEditMediaPending> pendingMessageMedia = new HashMap<>();

  public TdlibEditMediaManager (Tdlib tdlib) {
    this.tdlib = tdlib;
  }

  public void editMediaStart (long chatId, long messageId, TdApi.InputMessageContent inputMessageContent, @NonNull MediaToReplacePickerManager.LocalPickedFile localPickedFile) {
    final MessageEditMediaPending pendingEdit = new MessageEditMediaPending(tdlib, chatId, messageId, inputMessageContent, localPickedFile);
    pendingEdit.init(this);
    editMediaCancel(chatId, messageId);
    addPendingEditAndNotify(pendingEdit);
  }

  public boolean editMediaCancel (long chatId, long messageId) {
    synchronized (pendingMessageMedia) {
      MessageEditMediaPending pending = pendingMessageMedia.get(toKey(chatId, messageId));
      if (pending != null) {
        pending.cancel();
        return true;
      }
    }
    return false;
  }

  public boolean hasPendingMessageMedia (long chatId, long messageId) {
    synchronized (pendingMessageMedia) {
      return pendingMessageMedia.containsKey(toKey(chatId, messageId));
    }
  }

  public MessageEditMediaPending getPendingMessageMedia (long chatId, long messageId) {
    synchronized (pendingMessageMedia) {
      return pendingMessageMedia.get(toKey(chatId, messageId));
    }
  }

  @Override
  public void onMediaUploadStart (MessageEditMediaPending pendingEdit) {
    notifyPendingContentChanged(pendingEdit);
  }

  @Override
  public void onMediaUploadComplete (MessageEditMediaPending pendingEdit) {
    tdlib.send(new TdApi.EditMessageMedia(pendingEdit.chatId, pendingEdit.messageId, null, pendingEdit.content), (result, error) -> {
      if (error != null) {
        UI.showError(error);
      }
      UI.post(() -> removePendingEditAndNotify(pendingEdit));
    });

  }

  @Override
  public void onMediaUploadFailed (MessageEditMediaPending pendingEdit) {
    removePendingEditAndNotify(pendingEdit);
  }

  private void addPendingEditAndNotify (MessageEditMediaPending pendingEdit) {
    final String key = toKey(pendingEdit.chatId, pendingEdit.messageId);
    synchronized (pendingMessageMedia) {
      pendingMessageMedia.put(key, pendingEdit);
    }
    notifyPendingContentChanged(pendingEdit);
  }

  private void removePendingEditAndNotify (MessageEditMediaPending pendingEdit) {
    final String key = toKey(pendingEdit.chatId, pendingEdit.messageId);
    MessageEditMediaPending currentData = pendingMessageMedia.get(key);
    if (currentData == pendingEdit) {
      synchronized (pendingMessageMedia) {
        pendingMessageMedia.remove(key);
      }
      notifyPendingContentChanged(pendingEdit);
    }
  }

  private void notifyPendingContentChanged (MessageEditMediaPending pendingEdit) {
    tdlib.listeners().updateMessagePendingContentChanged(pendingEdit.chatId, pendingEdit.messageId);
  }

  private static String toKey (long chatId, long messageId) {
    return chatId + "_" + messageId;
  }
}
