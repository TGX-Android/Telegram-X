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
 * File created on 16/11/2023
 */
package org.thunderdog.challegram.component.chat.filter;

import android.util.Log;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import me.vkryl.core.reference.ReferenceLongMap;
import me.vkryl.core.reference.ReferenceMap;

public class MessagesFilterProvider implements ChatListener {

  private final Tdlib tdlib;

  public MessagesFilterProvider (Tdlib tdlib) {
    this.tdlib = tdlib;

    // memoryLeakDebug();
  }


  /**/

  public interface ChatCustomFilterSettingsUpdatesListener {
    void onChatFilterSettingsUpdate (long chatId);
  }

  private final ReferenceLongMap<ChatCustomFilterSettingsUpdatesListener> chatCustomFilterSettingsUpdatesListeners = new ReferenceLongMap<>();

  public void subscribeToChatCustomFilterSettingsUpdates (long chatId, ChatCustomFilterSettingsUpdatesListener listener) {
    chatCustomFilterSettingsUpdatesListeners.add(chatId, listener);
  }

  public void unsubscribeFromChatCustomFilterSettingsUpdates (long chatId, ChatCustomFilterSettingsUpdatesListener listener) {
    chatCustomFilterSettingsUpdatesListeners.remove(chatId, listener);
  }

  public void updateChatCustomFilterSettings (long chatId) {
    updateChatCustomFilterSettings(chatId, chatCustomFilterSettingsUpdatesListeners.iterator(chatId));
  }

  private static void updateChatCustomFilterSettings (long chatId, @Nullable Iterator<ChatCustomFilterSettingsUpdatesListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onChatFilterSettingsUpdate(chatId);
      }
    }
  }



  /* * */

  public interface MessageSenderUpdatesListener {
    void onMessageSenderBlockedUpdate (long chatId, boolean isBlocked);
  }

  private final ReferenceLongMap<MessageSenderUpdatesListener> senderUpdateListeners = new ReferenceLongMap<>();

  public void subscribeToMessageSenderUpdates (long chatId, MessageSenderUpdatesListener listener) {
    if (!senderUpdateListeners.has(chatId)) {
      tdlib.listeners().subscribeToChatUpdates(chatId, this);
    }

    senderUpdateListeners.add(chatId, listener);
  }

  public void unsubscribeFromMessageSenderUpdates (long chatId, MessageSenderUpdatesListener listener) {
    senderUpdateListeners.remove(chatId, listener);
    if (!senderUpdateListeners.has(chatId)) {
      tdlib.listeners().unsubscribeFromChatUpdates(chatId, this);
    }
  }

  @Override
  public void onChatBlockListChanged (long chatId, @Nullable TdApi.BlockList blockList) {
    UI.post(() -> updateChatUnreadMentionCount(chatId, tdlib.chatFullyBlocked(chatId), senderUpdateListeners.iterator(chatId)));
  }

  private static void updateChatUnreadMentionCount (long chatId, boolean isBlocked, @Nullable Iterator<MessageSenderUpdatesListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onMessageSenderBlockedUpdate(chatId, isBlocked);
      }
    }
  }



  /* * */

  public interface UsernameResolverUpdatesListener {
    void onUsernameResolverUpdate (String username, long chatId);
  }

  private final HashMap<String, Long> usernamesCache = new HashMap<>();   // Fixme: Username owner may change
  private final ReferenceMap<String, UsernameResolverUpdatesListener> usernameResolverCallbacks = new ReferenceMap<>();

  public long resolveUsername (String username) {
    Long cachedChatId = usernamesCache.get(username);
    if (cachedChatId != null) {
      return cachedChatId;
    }

    if (!usernamesCache.containsKey(username)) {
      usernamesCache.put(username, null);
      tdlib.send(new TdApi.SearchPublicChat(username), (TdApi.Chat chat, TdApi.Error error) -> {
        if (error != null) return;
        UI.post(() -> {
          usernamesCache.put(username, chat.id);
          updateUsernameResolverResult(username, chat.id, usernameResolverCallbacks.iterator(username));
        });
      });

    }
    return 0;
  }

  public void subscribeToUsernameResolverUpdates (String username, UsernameResolverUpdatesListener listener) {
    usernameResolverCallbacks.add(username, listener);
  }

  public void unsubscribeFromUsernameResolverUpdates (String username, UsernameResolverUpdatesListener listener) {
    usernameResolverCallbacks.remove(username, listener);
  }

  private static void updateUsernameResolverResult (String username, long chatId, @Nullable Iterator<UsernameResolverUpdatesListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onUsernameResolverUpdate(username, chatId);
      }
    }
  }



  private void memoryLeakDebug () {
    final Set<?> set1 = senderUpdateListeners.keySetUnchecked();
    final Set<?> set2 = usernameResolverCallbacks.keySetUnchecked();

    int size = (set1 != null ? set1.size() : 0) + (set2 != null ? set2.size() : 0);

    Log.i("FILTER_MEM_LEAK_DEBUG", "" + size);
    UI.post(this::memoryLeakDebug, 1000);
  }
}
