/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.data.article;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.MessageListener;
import org.thunderdog.challegram.telegram.Tdlib;
import java.util.HashMap;
import java.util.Map;

/** Waits for the final server result, not the initial temporary outgoing message. */
public final class ArticleSendTracker implements MessageListener {
  public interface Callback {
    void onPending (long messageId);
    void onComplete (boolean success, TdApi.Error error);
  }
  private final Runnable unsubscribe;
  private final long chatId;
  private final Callback callback;
  private final Map<Long, TdApi.Error> earlyResults = new HashMap<>();
  private long messageId;
  private boolean accepted, complete;

  public ArticleSendTracker (Tdlib tdlib, long chatId, Callback callback) {
    this.chatId = chatId; this.callback = callback;
    this.unsubscribe = () -> tdlib.listeners().unsubscribeFromMessageUpdates(chatId, this);
    tdlib.listeners().subscribeToMessageUpdates(chatId, this);
  }
  ArticleSendTracker (long chatId, Callback callback, Runnable unsubscribe) {
    this.chatId = chatId; this.callback = callback; this.unsubscribe = unsubscribe;
  }
  public synchronized void cancel () {
    complete = true;
    unsubscribe.run();
    earlyResults.clear();
  }
  public synchronized boolean isComplete () { return complete; }
  public synchronized void accept (TdApi.Message message) {
    if (complete) return;
    accepted = true;
    if (message == null || message.chatId != chatId) { finish(false, null); return; }
    messageId = message.id;
    if (message.sendingState instanceof TdApi.MessageSendingStateFailed) { finish(false, ((TdApi.MessageSendingStateFailed) message.sendingState).error); return; }
    if (message.sendingState == null) { finish(true, null); return; }
    callback.onPending(messageId);
    if (earlyResults.containsKey(messageId)) finish(earlyResults.get(messageId) == null, earlyResults.get(messageId));
    earlyResults.clear();
  }
  @Override public synchronized void onMessageSendSucceeded (TdApi.Message message, long oldMessageId) { result(oldMessageId, null); }
  @Override public synchronized void onMessageSendFailed (TdApi.Message message, long oldMessageId, TdApi.Error error) { result(oldMessageId, error); }
  private void result (long id, TdApi.Error error) {
    if (complete) return;
    if (!accepted) { if (earlyResults.size() < 64) earlyResults.put(id, error); }
    else if (messageId == id) finish(error == null, error);
  }
  private void finish (boolean success, TdApi.Error error) {
    complete = true;
    unsubscribe.run();
    earlyResults.clear();
    callback.onComplete(success, error);
  }
}
