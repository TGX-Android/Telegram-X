package org.thunderdog.challegram.telegram;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;

public interface StoryListener {
  void onStoryUpdated (@NonNull TdApi.Story story);
  void onStoryDeleted (long storySenderChatId, int storyId);
  default void onStorySendSucceeded (@NonNull TdApi.Story story, int oldStoryId) { }
  default void onStorySendFailed (@NonNull TdApi.Story story, TdApi.Error error, @Nullable TdApi.CanSendStoryResult errorType) { }
  default void onStoryStealthModeUpdated (int activeUntilDate, int cooldownUntilDate) { }
}
