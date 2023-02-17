/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 19/10/2016
 */
package org.thunderdog.challegram.component.attach;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.user.SimpleUsersAdapter;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGUser;

import java.util.ArrayList;
import java.util.List;

public class MediaBottomInlineBotsController extends MediaBottomBaseController<Void> implements Client.ResultHandler, SimpleUsersAdapter.Callback {
  public MediaBottomInlineBotsController (MediaLayout context) {
    super(context, R.string.InlineBot);
  }

  @Override
  public int getId () {
    return R.id.controller_media_inlineBots;
  }

  @Override
  protected View onCreateView (Context context) {
    buildContentView(true);
    setLayoutManager(new LinearLayoutManager(context(), RecyclerView.VERTICAL, false));
    setAdapter(adapter = new SimpleUsersAdapter(this, this, SimpleUsersAdapter.OPTION_CLICKABLE, this));
    tdlib.client().send(new TdApi.GetTopChats(new TdApi.TopChatCategoryInlineBots(), 50), this);
    return contentView;
  }

  private SimpleUsersAdapter adapter;

  protected void displayBots (final List<TGUser> users) {
    if (users.isEmpty()) {
      showError(R.string.NothingFound, 0, null, true);
    } else {
      hideProgress(() -> {
        adapter.setUsers(users);
        expandStartHeight(adapter);
      });
    }
  }

  /*@Override
  protected int getInitialContentHeight () {
    return asdfScreen.dp(64);
  }*/

  @Override
  public void onUserPicked (TGUser user) {
    mediaLayout.chooseInlineBot(user);
  }

  @Override
  public void onUserSelected (int selectedCount, TGUser user, boolean isSelected) { }

  private static final String[] DEFAULT_BOTS = { "bing", "wiki", "gif", "nephobot", "vid" };

  private ArrayList<TGUser> defaultBots = new ArrayList<>(DEFAULT_BOTS.length);

  private void loadNextBot () {
    if (defaultBots.size() > DEFAULT_BOTS.length) {
      return;
    }
    if (defaultBots.size() == DEFAULT_BOTS.length) {
      runOnUiThread(() -> displayBots(defaultBots));
    } else {
      tdlib.client().send(new TdApi.SearchPublicChat(DEFAULT_BOTS[defaultBots.size()]), this);
    }
  }

  private void addDefaultBot (TdApi.User user) {
    defaultBots.add(TGUser.createWithUsername(tdlib, user));
    loadNextBot();
  }

  @Override
  public void onResult (TdApi.Object object) {
    switch (object.getConstructor()) {
      case TdApi.Error.CONSTRUCTOR: {
        dispatchError(TD.toErrorString(object), null, null, true);
        break;
      }
      case TdApi.Chat.CONSTRUCTOR: {
        addDefaultBot(tdlib.chatUser((TdApi.Chat) object));
        break;
      }
      case TdApi.Chats.CONSTRUCTOR: {
        long[] chatIds = ((TdApi.Chats) object).chatIds;
        if (chatIds.length == 0) {
          loadNextBot();
          break;
        }

        final List<TGUser> users = new ArrayList<>(chatIds.length);
        final List<TdApi.Chat> chats = tdlib.chats(chatIds);
        for (TdApi.Chat chat : chats) {
          TdApi.User user = tdlib.chatUser(chat);
          if (user != null) {
            users.add(TGUser.createWithUsername(tdlib, user));
          }
        }

        if (users.isEmpty()) {
          tdlib.client().send(new TdApi.GetRecentInlineBots(), this);
        } else {
          runOnUiThread(() -> displayBots(users));
        }

        break;
      }
      case TdApi.Users.CONSTRUCTOR: {
        long[] userIds = ((TdApi.Users) object).userIds;
        if (userIds.length == 0) {
          loadNextBot();
          break;
        }
        final ArrayList<TdApi.User> users = tdlib.cache().users(userIds);
        final ArrayList<TGUser> parsedUsers = new ArrayList<>(userIds.length);
        for (TdApi.User user : users) {
          parsedUsers.add(TGUser.createWithUsername(tdlib, user));
        }
        runOnUiThread(() -> displayBots(parsedUsers));
        break;
      }
    }
  }
}
