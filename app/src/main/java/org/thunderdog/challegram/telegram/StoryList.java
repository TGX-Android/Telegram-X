package org.thunderdog.challegram.telegram;

import androidx.annotation.NonNull;

import org.drinkless.tdlib.TdApi;

import me.vkryl.core.lambda.RunnableBool;

public final class StoryList extends SortedList<TdApi.ChatActiveStories> {
  private final TdApi.StoryList list;

  public StoryList (Tdlib tdlib, TdApi.StoryList list) {
    super(tdlib);
    this.list = list;
  }

  @Override
  public int compare (TdApi.ChatActiveStories a, TdApi.ChatActiveStories b) {
    return tdlib.storiesComparator().compare(a, b);
  }

  @Override
  protected void loadMoreItems (int desiredCount, @NonNull RunnableBool after) {
    tdlib.client().send(new TdApi.LoadActiveStories(list), result -> {
      boolean endReached = result.getConstructor() == TdApi.Error.CONSTRUCTOR;
      after.runWithBool(endReached);
    });
  }

  @Override
  public int approximateTotalItemCount () {
    return tdlib.getStoryListChatCount(list);
  }
}
