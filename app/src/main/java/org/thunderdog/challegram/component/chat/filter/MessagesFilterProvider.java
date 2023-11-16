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

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;

import java.util.Iterator;

import me.vkryl.core.reference.ReferenceLongMap;
import me.vkryl.core.reference.ReferenceMap;

public class MessagesFilterProvider implements ChatListener {

  private final Tdlib tdlib;

  public MessagesFilterProvider (Tdlib tdlib) {
    this.tdlib = tdlib;
  }




  /* * */

  public interface SenderUpdateListener {
    void onMessageSenderBlockedUpdate (long chatId, boolean isBlocked);
  }

  private final ReferenceLongMap<SenderUpdateListener> senderUpdateListeners = new ReferenceLongMap<>();

  public void subscribeToMessageSenderUpdates (long chatId, SenderUpdateListener listener) {
    if (!senderUpdateListeners.has(chatId)) {
      tdlib.listeners().subscribeToChatUpdates(chatId, this);
    }

    senderUpdateListeners.add(chatId, listener);
  }

  public void unsubscribeFromChatUpdates (long chatId, SenderUpdateListener listener) {
    senderUpdateListeners.remove(chatId, listener);
    if (!senderUpdateListeners.has(chatId)) {
      tdlib.listeners().unsubscribeFromChatUpdates(chatId, this);
    }
  }

  @Override
  public void onChatBlockListChanged (long chatId, @Nullable TdApi.BlockList blockList) {
    UI.post(() -> updateChatUnreadMentionCount(chatId, tdlib.chatFullyBlocked(chatId), senderUpdateListeners.iterator(chatId)));
  }

  private static void updateChatUnreadMentionCount (long chatId, boolean isBlocked, @Nullable Iterator<SenderUpdateListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onMessageSenderBlockedUpdate(chatId, isBlocked);
      }
    }
  }



  /* * */

  public interface MentionsResolverCallback {
    void onMentionResolved (String username);
  }

  private final ReferenceMap<String, MentionsResolverCallback> mentionsResolverCallbacks = new ReferenceMap<>();

  public void subscribeToMentionsResolver (String username, MentionsResolverCallback listener) {
    mentionsResolverCallbacks.add(username, listener);
  }

  public void unsubscribeFromMentionsResolver (String username, MentionsResolverCallback listener) {
    mentionsResolverCallbacks.remove(username, listener);
  }



  /* * */

  private final ReferenceMap<String, LinksResolverCallback> linksResolverCallbacks = new ReferenceMap<>();

  public interface LinksResolverCallback {
    void onLinkResolved (String link);
  }

  public void subscribeToLinksResolver (String username, LinksResolverCallback listener) {
    linksResolverCallbacks.add(username, listener);
  }

  public void unsubscribeFromLinksResolver (String username, LinksResolverCallback listener) {
    linksResolverCallbacks.remove(username, listener);
  }
}
