/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.MessageListener;
import org.thunderdog.challegram.telegram.Tdlib;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.HashMap;
import java.util.Map;

/** Completes on a server send result, not on a locally queued pending message. */
public final class MiniAppMessageSender implements MessageListener {
  public interface Callback { void onResult (TdApi.Error error); }
  public interface Gateway { void send (TdApi.Function<TdApi.Message> function, org.drinkless.tdlib.Client.ResultHandler callback); }
  private final Tdlib tdlib;
  private final TdApi.Function<TdApi.Message> function;
  private final long chatId;
  private final Gateway gateway;
  private final Callback callback;
  private final AtomicBoolean finished = new AtomicBoolean();
  private boolean started;
  private long messageId;
  private boolean receivedMessage;
  private final Map<Long, TdApi.Error> earlyResults = new HashMap<>();

  public MiniAppMessageSender (Tdlib tdlib, TdApi.SendMessage function, Callback callback) {
    this(tdlib, function.chatId, function, (request, handler) -> tdlib.client().send(request, handler), callback);
  }

  public MiniAppMessageSender (Tdlib tdlib, long chatId, TdApi.Function<TdApi.Message> function, Gateway gateway, Callback callback) {
    this.tdlib = tdlib;
    this.chatId = chatId;
    this.function = function;
    this.gateway = gateway;
    this.callback = callback;
  }

  public synchronized void send () {
    if (started || finished.get()) return;
    started = true;
    tdlib.listeners().subscribeToMessageUpdates(chatId, this);
    gateway.send(function, result -> {
      synchronized (this) {
        if (finished.get() || receivedMessage) return;
        if (result instanceof TdApi.Error) { complete((TdApi.Error) result); return; }
        if (!(result instanceof TdApi.Message)) { complete(new TdApi.Error(500, "Unexpected send result")); return; }
        TdApi.Message message = (TdApi.Message) result;
        messageId = message.id;
        receivedMessage = true;
        if (message.sendingState == null) complete(null);
        else if (message.sendingState instanceof TdApi.MessageSendingStateFailed) complete(((TdApi.MessageSendingStateFailed) message.sendingState).error);
        else if (earlyResults.containsKey(messageId)) complete(earlyResults.get(messageId));
        earlyResults.clear();
      }
    });
  }

  @Override
  public void onMessageSendSucceeded (TdApi.Message message, long oldMessageId) {
    onSendResult(message.chatId, oldMessageId, null);
  }

  @Override
  public void onMessageSendFailed (TdApi.Message message, long oldMessageId, TdApi.Error error) {
    onSendResult(message.chatId, oldMessageId, error);
  }

  private synchronized void onSendResult (long chatId, long oldMessageId, TdApi.Error error) {
    if (finished.get() || this.chatId != chatId) return;
    if (!receivedMessage) earlyResults.put(oldMessageId, error);
    else if (oldMessageId == messageId) complete(error);
  }

  public synchronized void cancel () {
    earlyResults.clear();
    if (finished.compareAndSet(false, true)) tdlib.listeners().unsubscribeFromMessageUpdates(chatId, this);
  }

  private void complete (TdApi.Error error) {
    if (finished.compareAndSet(false, true)) {
      earlyResults.clear();
      tdlib.listeners().unsubscribeFromMessageUpdates(chatId, this);
      tdlib.ui().post(() -> callback.onResult(error));
    }
  }
}
