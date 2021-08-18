/**
 * File created on 27/04/15 at 15:47
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.ui;

import android.os.Handler;
import android.os.Message;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.data.TGChat;
import org.thunderdog.challegram.telegram.ChatFilter;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.td.ChatPosition;

public class ChatsProcessor extends Handler {
  private static final int DISPLAY = 1;
  private static final int LOAD_MORE = 3;

  private final ChatsController context;
  private @Nullable ChatFilter filter;

  public ChatsProcessor (ChatsController context) {
    this.context = context;
  }

  public void setFilter (@Nullable ChatFilter filter) {
    this.filter = filter;
  }

  public void display (List<TdApi.Chat> rawChats) {
    TGChat[] chats;
    if (filter != null) {
      chats = filter(rawChats, this.filter);
    } else {
      chats = new TGChat[rawChats.size()];
      int i = 0;
      for (TdApi.Chat chat : rawChats) {
        chats[i] = new TGChat(context.getParentOrSelf(), context.chatList(), chat, true);
        i++;
      }
    }
    if (!rawChats.isEmpty() && chats.length == 0) {
      sendMessage(Message.obtain(this, LOAD_MORE, rawChats));
    } else {
      sendMessage(Message.obtain(this, DISPLAY, rawChats.size(), 0, new Object[] {chats, rawChats}));
    }
  }

  private TGChat[] filter (List<TdApi.Chat> rawChats, @NonNull ChatFilter filter) {
    List<TGChat> chats = new ArrayList<>(rawChats.size());
    for (TdApi.Chat chat : rawChats) {
      if (filter.accept(chat)) {
        chats.add(new TGChat(context.getParentOrSelf(), context.chatList(), chat, true));
      }
    }
    return chats.toArray(new TGChat[0]);
  }

  private void displayImpl (TGChat[] list, List<TdApi.Chat> rawChats) {
    if (filter != null && !rawChats.isEmpty() && rawChats.size() > list.length) {
      TdApi.Chat chat = rawChats.get(rawChats.size() - 1);
      context.resetLoading(ChatPosition.getOrder(chat, context.chatList()), chat.id, false);
    }
    int displayedCount = list.length;
    boolean hasChats = list.length != 0;
    context.hideProgressView();
    context.getChatsAdapter().addMore(list);
    context.setCanLoadMore(hasChats);
    if (!hasChats) {
      context.setFinishReached();
    }
    context.setIsLoading(false);
    if (displayedCount < context.getChatsView().getInitialLoadCount()) {
      context.loadInitialMore();
    }
    context.executeScheduledAnimation();
  }

  @Override
  public void handleMessage (Message msg) {
    switch (msg.what) {
      case DISPLAY: {
        Object[] data = (Object[]) msg.obj;
        TGChat[] list = (TGChat[]) data[0];
        List<TdApi.Chat> rawChats = (List<TdApi.Chat>) data[1];
        displayImpl(list, rawChats);
        break;
      }
      case LOAD_MORE: {
        List<TdApi.Chat> chats = (List<TdApi.Chat>) msg.obj;
        boolean found = false;
        long minOrder = 0, minChatId = 0;
        for (TdApi.Chat chat : chats) {
          long order = ChatPosition.getOrder(chat, context.chatList());
          if (!found || order < minOrder) {
            found = true;
            minOrder = order;
            minChatId = chat.id;
          }
        }
        for (TGChat chat : context.getChatsAdapter().getChats()) {
          if (!found || chat.getChatOrder() < minOrder) {
            minOrder = chat.getChatOrder();
            minChatId = chat.getChatId();
          }
        }
        if (found) {
          context.resetLoading(minOrder, minChatId, true);
          context.requestLoadMore();
        } else {
          displayImpl(new TGChat[0], chats);
        }
        break;
      }
    }
  }

}
