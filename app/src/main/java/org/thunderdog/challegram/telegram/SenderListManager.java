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
 * File created on 24/09/2023
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
import me.vkryl.td.ChatId;
import me.vkryl.td.Td;

public abstract class SenderListManager extends ListManager<TdApi.MessageSender> implements TdlibCache.UserDataChangeListener, TdlibCache.UserStatusChangeListener, TdlibCache.SupergroupDataChangeListener, ChatListener {
  public interface ChangeListener extends ListManager.ListChangeListener<TdApi.MessageSender> { }

  private final LongSet senderIdsCheck = new LongSet();

  public SenderListManager (Tdlib tdlib, int initialLoadCount, int loadCount, @Nullable ChangeListener listener) {
    super(tdlib, initialLoadCount, loadCount, false, listener);
  }

  @Override
  protected abstract TdApi.Function<TdApi.MessageSenders> nextLoadFunction (boolean reverse, int itemCount, int loadCount);

  @Override
  protected void subscribeToUpdates () {
    tdlib.cache().subscribeToAnyUpdates(this);
    tdlib.listeners().subscribeForAnyUpdates(this);
  }

  @Override
  protected void unsubscribeFromUpdates () {
    tdlib.cache().unsubscribeFromAnyUpdates(this);
    tdlib.listeners().unsubscribeFromAnyUpdates(this);
  }

  @Override
  protected Response<TdApi.MessageSender> processResponse (TdApi.Object response, Client.ResultHandler retryHandler, int retryLoadCount, boolean reverse) {
    final TdApi.MessageSenders senders = (TdApi.MessageSenders) response;
    List<TdApi.MessageSender> sendersList = new ArrayList<>(senders.senders.length);
    for (TdApi.MessageSender sender : senders.senders) {
      long senderId = Td.getSenderId(sender);
      if (senderIdsCheck.add(senderId)) {
        sendersList.add(sender);
      }
    }
    return new Response<>(sendersList, senders.totalCount);
  }

  // Updates

  private int indexOfItem (long senderId) {
    int index = 0;
    for (TdApi.MessageSender sender : items) {
      if (Td.getSenderId(sender) == senderId) {
        return index;
      }
      index++;
    }
    return -1;
  }

  private void runWithChat (long chatId, RunnableInt act) {
    if (senderIdsCheck.has(chatId)) {
      runOnUiThreadIfReady(() -> {
        int index = indexOfItem(chatId);
        if (index != -1) {
          // This check is needed, because
          // senderIdsCheck is modified on TDLib thread,
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
    UpdateReason.CHAT_TITLE_CHANGED,
    UpdateReason.CHAT_PHOTO_CHANGED,
    UpdateReason.SUPERGROUP_UPDATED,
    UpdateReason.SUPERGROUP_FULL_UPDATED
  })
  public @interface UpdateReason {
    int
      USER_CHANGED = 1,
      USER_FULL_CHANGED = 2,
      USER_STATUS_CHANGED = 3,
      CHAT_TITLE_CHANGED = 4,
      CHAT_PHOTO_CHANGED = 5,
      SUPERGROUP_UPDATED = 6,
      SUPERGROUP_FULL_UPDATED = 7;
  }

  private void notifyChatChanged (long chatId, @UpdateReason int reason) {
    runWithChat(chatId, index ->
      notifyItemChanged(index, reason)
    );
  }

  private void notifyUserChanged (long userId, @UpdateReason int reason) {
    notifyChatChanged(ChatId.fromUserId(userId), reason);
  }

  private void notifySupergroupChanged (long supergroupId, @UpdateReason int reason) {
    notifyChatChanged(ChatId.fromSupergroupId(supergroupId), reason);
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
  public void onUserStatusChanged (long userId, TdApi.UserStatus status, boolean uiOnly) {
    notifyUserChanged(userId, UpdateReason.USER_STATUS_CHANGED);
  }

  @Override
  public void onChatTitleChanged (long chatId, String title) {
    notifyChatChanged(chatId, UpdateReason.CHAT_TITLE_CHANGED);
  }

  @Override
  public void onChatPhotoChanged (long chatId, @Nullable TdApi.ChatPhotoInfo photo) {
    notifyChatChanged(chatId, UpdateReason.CHAT_PHOTO_CHANGED);
  }

  @Override
  public void onSupergroupUpdated (TdApi.Supergroup supergroup) {
    notifySupergroupChanged(supergroup.id, UpdateReason.SUPERGROUP_UPDATED);
  }

  @Override
  public void onSupergroupFullUpdated (long supergroupId, TdApi.SupergroupFullInfo newSupergroupFull) {
    notifySupergroupChanged(supergroupId, UpdateReason.SUPERGROUP_FULL_UPDATED);
  }
}
