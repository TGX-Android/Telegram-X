package org.thunderdog.challegram.telegram;

import org.drinkless.tdlib.TdApi;

public interface MessageEditMediaUploadCallback {
  void onMediaPreliminaryUploadStart (MessageEditMediaPending pendingEdit, TdApi.File file);

  void onMediaPreliminaryUploadComplete (MessageEditMediaPending pendingEdit, TdApi.InputMessageContent content);

  void onMediaPreliminaryUploadFailed (MessageEditMediaPending pendingEdit, boolean isCanceled);
}
