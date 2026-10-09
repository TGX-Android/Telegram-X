/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.MessageListener;
import org.thunderdog.challegram.telegram.Tdlib;

import java.util.concurrent.atomic.AtomicBoolean;

/** Completes on a server send result, not on a locally queued pending message. */
public final class MiniAppMessageSender implements MessageListener {
  public interface Callback { void onResult (TdApi.Error error); }
  private final Tdlib tdlib;
  private final TdApi.SendMessage function;
  private final Callback callback;
  private final AtomicBoolean finished = new AtomicBoolean();
  private long messageId;

  public MiniAppMessageSender (Tdlib tdlib, TdApi.SendMessage function, Callback callback) {
    this.tdlib = tdlib;
    this.function = function;
    this.callback = callback;
  }

  public void send () {
    tdlib.listeners().subscribeToMessageUpdates(function.chatId, this);
    tdlib.send(function, (message, error) -> {
      if (error != null) { complete(error); return; }
      messageId = message.id;
      if (message.sendingState == null) complete(null);
      else if (message.sendingState instanceof TdApi.MessageSendingStateFailed) complete(((TdApi.MessageSendingStateFailed) message.sendingState).error);
    });
  }

  @Override
  public void onMessageSendSucceeded (TdApi.Message message, long oldMessageId) {
    if (oldMessageId == messageId) complete(null);
  }

  @Override
  public void onMessageSendFailed (TdApi.Message message, long oldMessageId, TdApi.Error error) {
    if (oldMessageId == messageId) complete(error);
  }

  public void cancel () {
    if (finished.compareAndSet(false, true)) tdlib.listeners().unsubscribeFromMessageUpdates(function.chatId, this);
  }

  private void complete (TdApi.Error error) {
    if (finished.compareAndSet(false, true)) {
      tdlib.listeners().unsubscribeFromMessageUpdates(function.chatId, this);
      tdlib.ui().post(() -> callback.onResult(error));
    }
  }
}
