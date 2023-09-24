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
 */
package org.thunderdog.challegram.telegram;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.collection.LongSet;
import me.vkryl.core.lambda.RunnableInt;

public abstract class UserListManager extends ListManager<Long> implements TdlibCache.UserDataChangeListener, TdlibCache.UserStatusChangeListener {
  public interface ChangeListener extends ListManager.ListChangeListener<Long> { }

  private final LongSet userIdsCheck = new LongSet();

  public UserListManager (Tdlib tdlib, int initialLoadCount, int loadCount, @Nullable ChangeListener listener) {
    super(tdlib, initialLoadCount, loadCount, false, listener);
  }

  @Override
  protected abstract TdApi.Function<TdApi.Users> nextLoadFunction (boolean reverse, int itemCount, int loadCount);

  @Override
  protected void subscribeToUpdates() {
    tdlib.cache().addGlobalUsersListener(this);
  }

  @Override
  protected void unsubscribeFromUpdates() {
    tdlib.cache().removeGlobalUsersListener(this);
  }

  @Override
  protected final Response<Long> processResponse (TdApi.Object response, Client.ResultHandler retryHandler, int retryLoadCount, boolean reverse) {
    TdApi.Users users = (TdApi.Users) response;
    long[] rawUserIds = users.userIds;
    List<Long> userIds = new ArrayList<>(rawUserIds.length);
    for (long userId : rawUserIds) {
      if (userIdsCheck.add(userId)) {
        userIds.add(userId);
      }
    }
    return new Response<>(userIds, users.totalCount);
  }

  // Updates

  private void runWithUser (long userId, RunnableInt act) {
    if (userIdsCheck.has(userId)) {
      runOnUiThreadIfReady(() -> {
        int index = indexOfItem(userId);
        if (index != -1) {
          // This check is needed, because
          // userIdsCheck is modified on TDLib thread,
          // but items list is modified on main thread
          act.runWithInt(index);
        }
      });
    }
  }

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    UpdateReason.USER_CHANGED,
    UpdateReason.USER_FULL_CHANGED,
    UpdateReason.USER_STATUS_CHANGED,
  })
  public @interface UpdateReason {
    int
      USER_CHANGED = 1,
      USER_FULL_CHANGED = 2,
      USER_STATUS_CHANGED = 3;
  }

  private void notifyUserChanged (long userId, @UpdateReason int reason) {
    runWithUser(userId, index ->
      notifyItemChanged(index, reason)
    );
  }

  @Override
  public void onUserUpdated (TdApi.User user) {
    notifyUserChanged(user.id, UpdateReason.USER_CHANGED);
  }

  @Override
  public void onUserFullUpdated (long userId, TdApi.UserFullInfo userFull) {
    notifyUserChanged(userId, UpdateReason.USER_FULL_CHANGED);
  }

  @Override
  public void onUserStatusChanged(long userId, TdApi.UserStatus status, boolean uiOnly) {
    notifyUserChanged(userId, UpdateReason.USER_STATUS_CHANGED);
  }
}
