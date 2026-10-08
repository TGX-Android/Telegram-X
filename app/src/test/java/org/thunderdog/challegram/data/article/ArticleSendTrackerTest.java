/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.data.article;

import org.drinkless.tdlib.TdApi;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArticleSendTrackerTest {
  @Test public void deletingPendingMessageRestoresDraftWithoutReportingSuccess () {
    Observer observer = new Observer(); ArticleSendTracker tracker = observer.tracker();
    tracker.accept(message(1, -1, new TdApi.MessageSendingStatePending()));
    tracker.onMessagesDeleted(2, new long[] {-1});
    tracker.onMessagesDeleted(1, new long[] {-2});
    assertEquals(0, observer.completed);
    tracker.onMessagesDeleted(1, new long[] {-1});
    assertEquals(1, observer.completed); assertFalse(observer.success); assertNull(observer.error);
    tracker.cancel(); tracker.onMessageSendSucceeded(message(1, 10, null), -1);
    assertEquals(1, observer.unsubscribed); assertEquals(1, observer.completed);
  }
  @Test public void deletionBeforeSendCallbackAndUnrelatedChatResultsAreHandled () {
    Observer observer = new Observer(); ArticleSendTracker tracker = observer.tracker();
    tracker.onMessageSendSucceeded(message(2, 11, null), -1);
    tracker.onMessagesDeleted(1, new long[] {-1});
    tracker.accept(message(1, -1, new TdApi.MessageSendingStatePending()));
    assertEquals(1, observer.completed); assertFalse(observer.success); assertEquals(1, observer.unsubscribed);
  }
  private static TdApi.Message message (long chatId, long id, TdApi.MessageSendingState state) {
    TdApi.Message message = new TdApi.Message(); message.chatId = chatId; message.id = id; message.sendingState = state; return message;
  }
  private static final class Observer implements ArticleSendTracker.Callback {
    int pending, completed, unsubscribed; boolean success; TdApi.Error error;
    ArticleSendTracker tracker () { return new ArticleSendTracker(1, this, () -> unsubscribed++); }
    @Override public void onPending (long id) { pending++; }
    @Override public void onComplete (boolean success, TdApi.Error error) { completed++; this.success = success; this.error = error; }
  }
  @Test public void pendingIsNotSuccessAndUnrelatedUpdatesAreIgnored () {
    Observer observer = new Observer(); ArticleSendTracker tracker = observer.tracker();
    tracker.accept(message(1, -1, new TdApi.MessageSendingStatePending()));
    assertEquals(1, observer.pending); assertEquals(0, observer.completed);
    tracker.onMessageSendSucceeded(message(1, 10, null), -2); assertEquals(0, observer.completed);
    tracker.onMessageSendSucceeded(message(1, 11, null), -1); assertTrue(observer.success); assertEquals(1, observer.completed); assertEquals(1, observer.unsubscribed);
    tracker.onMessageSendFailed(message(1, 11, null), -1, new TdApi.Error(400, "late")); assertEquals(1, observer.completed);
  }
  @Test public void resultBeforeRequestCallbackIsNotLost () {
    Observer observer = new Observer(); ArticleSendTracker tracker = observer.tracker();
    tracker.onMessageSendSucceeded(message(1, 11, null), -1);
    tracker.accept(message(1, -1, new TdApi.MessageSendingStatePending()));
    assertTrue(observer.success); assertEquals(1, observer.completed); assertEquals(1, observer.unsubscribed);
  }
  @Test public void failureBeforeRequestCallbackKeepsErrorAndDoesNotReportSuccess () {
    Observer observer = new Observer(); ArticleSendTracker tracker = observer.tracker(); TdApi.Error error = new TdApi.Error(400, "CHAT_WRITE_FORBIDDEN");
    tracker.onMessageSendFailed(message(1, -1, null), -1, error);
    tracker.accept(message(1, -1, new TdApi.MessageSendingStatePending()));
    assertFalse(observer.success); assertSame(error, observer.error); assertEquals(1, observer.completed);
  }
  @Test public void restoredFinalMessageCompletesAndCancellationDoesNotDeleteDraft () {
    Observer observer = new Observer(); ArticleSendTracker tracker = observer.tracker(); tracker.accept(message(1, 10, null));
    assertTrue(observer.success); assertEquals(1, observer.completed);
    observer = new Observer(); tracker = observer.tracker(); tracker.cancel();
    tracker.accept(message(1, 10, null)); assertEquals(0, observer.completed); assertEquals(1, observer.unsubscribed);
  }
}
