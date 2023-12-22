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
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGFoundChat;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.BetterChatView;

import java.util.ArrayList;

import me.vkryl.td.ChatId;

public class SettingsMessagesFilterChannelsController extends RecyclerViewController<Void> implements View.OnClickListener {

  private SettingsAdapter adapter;

  public SettingsMessagesFilterChannelsController(Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setChatData (ListItem item, int position, BetterChatView chatView) {
        final TGFoundChat tgFoundChat = (TGFoundChat) item.getData();
        final long chatId = item.getLongId();
        final String subtitle = makeSubtitle(chatId);

        if (tgFoundChat != null) {
          tgFoundChat.setForcedSubtitle(subtitle);
          chatView.setChat(tgFoundChat);
        } else {
          chatView.setTitle(Lang.getString(R.string.MessagesFilterUnknownChat));
          chatView.setChat(null);
        }
        chatView.setSubtitle(subtitle);
        chatView.invalidate();
      }
    };

    buildCells();
    recyclerView.setAdapter(adapter);
  }


  private void buildCells () {
    ArrayList<ListItem> items = new ArrayList<>();

    final long[] chatIds = Settings.instance().getFilteredChatIds();
    boolean isFirst = true;
    for (long chatId : chatIds) {
      if (!isFirst) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR));
      }
      isFirst = false;

      TdApi.Chat chat = tdlib.chat(chatId);
      if (chat == null) {
        tdlib.chat(chatId, this::onChatLoaded);
      }
      TGFoundChat tgFoundChat = chat != null ? new TGFoundChat(tdlib, null, chatId, false) : null;
      items.add(new ListItem(ListItem.TYPE_CHAT_BETTER, R.id.channel_filtered).setData(tgFoundChat).setLongId(chatId));
    }

    adapter.setItems(items, true);
  }

  private void onChatLoaded (TdApi.Chat chat) {
    UI.post(() -> {
      if (!isDestroyed() && chat != null) {
        final ListItem item = adapter.getItem(adapter.indexOfViewByLongId(chat.id));
        if (item != null) {
          item.setData( new TGFoundChat(tdlib, null, chat.id, false));
          adapter.updateValuedSettingByLongId(chat.id);
        }
      }
    });
  }



  @Override
  public void onClick (View v) {
    final ListItem item = (ListItem) v.getTag();
    if (item == null) {
      return;
    }

    final long chatId = item.getLongId();
    tdlib.ui().openMessageFilterSettings(this, chatId, () -> adapter.updateValuedSettingByLongId(chatId));
  }


  /* * */

  @Override
  public int getId () {
    return R.id.controller_messagesFilterChannelSettings;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.MessagesFilterFilteredChannels);
  }


  /* * */

  private static String makeSubtitle (long chatId) {
    StringBuilder sb = new StringBuilder();
    if (Settings.instance().isChatFilterEnabled(chatId, Settings.FILTER_TYPE_LINKS_INTERNAL)) {
      sb.append(Lang.getString(R.string.ChatMessagesFilterInternal));
    }
    if (Settings.instance().isChatFilterEnabled(chatId, Settings.FILTER_TYPE_LINKS_EXTERNAL)) {
      if (sb.length() > 0) {
        sb.append(Lang.getConcatSeparator());
      }
      sb.append(Lang.getString(R.string.ChatMessagesFilterExternal));
    }
    if (sb.length() == 0) {
      return Lang.getString(R.string.ChatMessagesFilterNone);
    }
    return sb.toString();
  }
}
