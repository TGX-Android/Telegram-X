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
 * File created on 25/09/2023
 */
package org.thunderdog.challegram.component.chat;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.telegram.MessageListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibThread;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.CancellableResultHandler;

import java.util.HashMap;
import java.util.Iterator;

import me.vkryl.core.lambda.Destroyable;
import me.vkryl.core.reference.ReferenceLongMap;
import me.vkryl.td.Td;

public class TdlibSingleUnreadReactionsManager implements ChatListener, MessageListener, Destroyable {
  private final Tdlib tdlib;
  private final HashMap<Long, ChatState> chatStates = new HashMap<>();

  public TdlibSingleUnreadReactionsManager (Tdlib tdlib) {
    this.tdlib = tdlib;

    tdlib.listeners().subscribeForGlobalUpdates(this);
  }

  @Override
  public void performDestroy () {
    tdlib.listeners().unsubscribeFromGlobalUpdates(this);
  }

  @UiThread
  private void onUpdateChatUnreadReactionCount (long chatId, @Nullable TdApi.UnreadReaction[] unreadReactions, int unreadReactionCount) {
    ChatState chatState = chatStates.get(chatId);
    if (chatState == null) {
      chatState = new ChatState(tdlib, this, chatId);
      chatStates.put(chatId, chatState);
    }

    chatState.onUpdateChatUnreadReactionCount(chatId, unreadReactions, unreadReactionCount);
  }

  @UiThread
  public void checkChat (TdApi.Chat chat) {
    final long chatId = chat.id;
    ChatState chatState = chatStates.get(chatId);
    if (chatState == null && chat.unreadReactionCount == 1) {
      chatState = new ChatState(tdlib, this, chatId);
      chatStates.put(chatId, chatState);
      chatState.onUpdateChatUnreadReactionCount(chatId, null, chat.unreadReactionCount);
    }
  }

  @Nullable
  public TdApi.UnreadReaction getSingleUnreadReaction (long chatId) {
    ChatState chatState = chatStates.get(chatId);
    return chatState != null ? chatState.getLastSingleUnreadReaction() : null;
  }

  private static class ChatState {
    private static final int STATE_NO_SINGLE_REACTION = 1;
    private static final int STATE_LOADING = 2;
    private static final int STATE_SINGLE_REACTION_FOUND = 3;

    private final TdlibSingleUnreadReactionsManager manager;
    private final Tdlib tdlib;
    private final long chatId;

    private int state;
    private TdApi.UnreadReaction lastSingleUnreadReaction;

    private CancellableResultHandler handler;

    public ChatState (Tdlib tdlib, TdlibSingleUnreadReactionsManager manager, long chatId) {
      this.tdlib = tdlib;
      this.chatId = chatId;
      this.state = STATE_NO_SINGLE_REACTION;
      this.manager = manager;
    }

    @Nullable
    public TdApi.UnreadReaction getLastSingleUnreadReaction () {
      return state == STATE_SINGLE_REACTION_FOUND ? lastSingleUnreadReaction : null;
    }

    @UiThread
    public void onUpdateChatUnreadReactionCount (long chatId, @Nullable TdApi.UnreadReaction[] unreadReactions, int unreadReactionCount) {
      if (this.chatId != chatId) {
        return;
      }
      if (handler != null) {
        handler.cancel();
        handler = null;
      }

      if (unreadReactionCount != 1 || unreadReactions != null && unreadReactions.length > 1) {
        setState(STATE_NO_SINGLE_REACTION, null);
        return;
      }
      if (unreadReactions != null && unreadReactions.length == 1) {
        setState(STATE_SINGLE_REACTION_FOUND, unreadReactions[0]);
        return;
      }

      setState(STATE_LOADING, null);
      handler = new CancellableResultHandler() {
        @Override
        public void processResult (TdApi.Object object) {
          if (object.getConstructor() != TdApi.FoundChatMessages.CONSTRUCTOR) {
            UI.post(() -> setState(STATE_NO_SINGLE_REACTION, null));
            return;
          }
          UI.post(() -> onUnreadReactionsLoaded((TdApi.FoundChatMessages) object));
        }
      };
      tdlib.client().send(new TdApi.SearchChatMessages(
        chatId, null, null, 0, 0, 100,
        new TdApi.SearchMessagesFilterUnreadReaction(), 0), handler
      );
    }

    @UiThread
    private void setState (int state, TdApi.UnreadReaction foundUnreadReaction) {
      this.state = state;
      this.lastSingleUnreadReaction = foundUnreadReaction;
      if (foundUnreadReaction != null) {
        tdlib.getReaction(foundUnreadReaction.type);    // preload reaction
      }
      manager.updateUnreadSingleReaction(chatId, foundUnreadReaction);
    }

    private static TdApi.UnreadReaction findSingleReactionType (TdApi.Message[] messages) {
      TdApi.UnreadReaction singleReaction = null;
      for (TdApi.Message message : messages) {
        if (message.unreadReactions == null) {
          continue;
        }
        for (TdApi.UnreadReaction unreadReaction : message.unreadReactions) {
          if (singleReaction == null) {
            singleReaction = unreadReaction;
          } else if (!Td.equalsTo(singleReaction.type, unreadReaction.type)) {
            return null;
          }
        }
      }
      return singleReaction;
    }

    @UiThread
    private void onUnreadReactionsLoaded (TdApi.FoundChatMessages foundChatMessages) {
      TdApi.UnreadReaction singleReaction = foundChatMessages.totalCount <= foundChatMessages.messages.length ? findSingleReactionType(foundChatMessages.messages) : null;
      if (singleReaction != null) {
        setState(STATE_SINGLE_REACTION_FOUND, singleReaction);
      } else {
        setState(STATE_NO_SINGLE_REACTION, null);
      }
    }
  }



  /*
   * Listeners
   *
   * TdApi.UpdateMessageUnreadReactions first calls updateMessageUnreadReactions,
   * then immediately updatesChatUnreadReactionCount. The second call should be ignored.
   *
   */

  @Override
  public void onMessageUnreadReactionsChanged (long chatId, long messageId, @Nullable TdApi.UnreadReaction[] unreadReactions, int unreadReactionCount) {
    scheduleOrIgnoreUpdate(chatId, new ScheduledUpdate(() -> UI.post(() ->
      onUpdateChatUnreadReactionCount(chatId, unreadReactions, unreadReactionCount)), true));
  }

  @Override
  public void onChatUnreadReactionCount (long chatId, int unreadReactionCount, boolean availabilityChanged) {
    scheduleOrIgnoreUpdate(chatId, new ScheduledUpdate(() -> UI.post(() ->
      onUpdateChatUnreadReactionCount(chatId, null, unreadReactionCount)), false));
  }

  private final HashMap<Long, ScheduledUpdate> scheduledUpdates = new HashMap<>();

  private static class ScheduledUpdate {
    public final Runnable update;
    public final boolean isPriority;

    public ScheduledUpdate (Runnable update, boolean isPriority) {
      this.update = update;
      this.isPriority = isPriority;
    }
  }

  @TdlibThread
  private void scheduleOrIgnoreUpdate (final long chatId, ScheduledUpdate update) {
    final boolean hasScheduledUpdate = scheduledUpdates.containsKey(chatId);
    final ScheduledUpdate oldUpdate = hasScheduledUpdate ? scheduledUpdates.get(chatId) : null;
    final boolean hasPriorityScheduledUpdate = oldUpdate == null && hasScheduledUpdate;

    if (update.isPriority) {
      update.update.run();
      scheduledUpdates.put(chatId, null);
    } else if (!hasPriorityScheduledUpdate) {
      scheduledUpdates.put(chatId, update);
    }

    if (!hasScheduledUpdate) {
      tdlib.runOnTdlibThread(() -> executeScheduledUpdate(chatId));
    }
  }

  @TdlibThread
  private void executeScheduledUpdate (long chatId) {
    ScheduledUpdate update = scheduledUpdates.remove(chatId);
    if (update != null) {
      update.update.run();
    }
  }

  /* * */

  public interface UnreadSingleReactionListener {
    void onUnreadSingleReactionUpdate (long chatId, @Nullable TdApi.UnreadReaction unreadReaction);
  }

  private final ReferenceLongMap<UnreadSingleReactionListener> listeners = new ReferenceLongMap<>();

  public void subscribeToUnreadSingleReactionUpdates (long chatId, UnreadSingleReactionListener listener) {
    listeners.add(chatId, listener);
  }

  public void unsubscribeFromUnreadSingleReactionUpdates (long chatId, UnreadSingleReactionListener listener) {
    listeners.remove(chatId, listener);
  }

  private void updateUnreadSingleReaction (long chatId, TdApi.UnreadReaction unreadReaction) {
    Iterator<UnreadSingleReactionListener> list = listeners.iterator(chatId);
    if (list != null) {
      while (list.hasNext()) {
        list.next().onUnreadSingleReactionUpdate(chatId, unreadReaction);
      }
    }
  }
}
