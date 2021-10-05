package org.thunderdog.challegram.telegram;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.collection.LongSet;

public abstract class UserListManager extends ListManager<Long> implements TdlibCache.UserDataChangeListener, TdlibCache.UserStatusChangeListener {
  public interface ChangeListener extends ListManager.ListChangeListener<Long> { }

  private final LongSet userIdsCheck = new LongSet();

  public UserListManager (Tdlib tdlib, int initialLoadCount, int loadCount, @Nullable ChangeListener listener) {
    super(tdlib, initialLoadCount, loadCount, false, listener);
  }

  @Override
  protected void subscribeToUpdates() {
    tdlib.cache().addGlobalUsersListener(this);
  }

  @Override
  protected void unsubscribeFromUpdates() {
    tdlib.cache().removeGlobalUsersListener(this);
  }

  @Override
  protected Response<Long> processResponse (TdApi.Object response, Client.ResultHandler retryHandler, int retryLoadCount, boolean reverse) {
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

  private void runWithUser (long userId, Runnable act) {
    runOnUiThreadIfReady(() -> {
      if (userIdsCheck.has(userId)) {
        act.run();
      }
    });
  }

  public static final int REASON_USER_CHANGED = 0;
  public static final int REASON_USER_FULL_CHANGED = 1;
  public static final int REASON_STATUS_CHANGED = 2;

  @Override
  public void onUserUpdated (TdApi.User user) {
    runWithUser(user.id, () -> {
      int index = indexOfItem(user.id);
      if (index != -1) {
        notifyItemChanged(index, REASON_USER_CHANGED);
      }
    });
  }

  @Override
  public void onUserFullUpdated (long userId, TdApi.UserFullInfo userFull) {
    runWithUser(userId, () -> {
      int index = indexOfItem(userId);
      if (index != -1) {
        notifyItemChanged(index, REASON_USER_FULL_CHANGED);
      }
    });
  }

  @Override
  public void onUserStatusChanged(long userId, TdApi.UserStatus status, boolean uiOnly) {
    runWithUser(userId, () -> {
      int index = indexOfItem(userId);
      if (index != -1) {
        notifyItemChanged(index, REASON_STATUS_CHANGED);
      }
    });
  }

  @Override
  public boolean needUserStatusUiUpdates() {
    return false;
  }
}
