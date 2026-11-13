/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.DoubleTextWrapper;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.HashSet;

/** Account-level chat lists, using the same cells and navigation as shared chats. */
public class ChatDiscoveryController extends RecyclerViewController<Void> implements View.OnClickListener {
  public static final int RECOMMENDED = 0;
  public static final int RECENT = 1;
  public static final int INACTIVE = 2;
  private final int mode;

  public ChatDiscoveryController (Context context, Tdlib tdlib) {
    this(context, tdlib, RECOMMENDED);
  }

  public ChatDiscoveryController (Context context, Tdlib tdlib, int mode) {
    super(context, tdlib);
    this.mode = mode;
  }

  @Override
  public int getId () {
    return R.id.controller_chatDiscovery;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(mode == RECENT ? R.string.RecentlyOpenedChats :
      mode == INACTIVE ? R.string.InactiveGroupChats : R.string.RecommendedChannels);
  }

  protected TdApi.Function<TdApi.Chats> discoveryRequest () {
    if (mode == RECENT) {
      return new TdApi.GetRecentlyOpenedChats(100);
    } else if (mode == INACTIVE) {
      return new TdApi.GetInactiveSupergroupChats();
    }
    return new TdApi.GetRecommendedChats();
  }

  private SettingsAdapter adapter;
  private TdApi.Chat[] chats;
  private boolean loading;
  private TdApi.Error loadError;
  private int requestGeneration;

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this);
    recyclerView.setAdapter(adapter);
    loadChats();
  }

  @Override
  public void onFocus () {
    super.onFocus();
    if (adapter != null) {
      loadChats();
    }
  }

  private void loadChats () {
    if (loading) {
      return;
    }
    loading = true;
    loadError = null;
    final int generation = ++requestGeneration;
    buildCells();
    tdlib.send(discoveryRequest(), (result, error) -> {
      TdApi.Chat[] loaded = error == null ? tdlib.chats(result.chatIds).toArray(new TdApi.Chat[0]) : null;
      runOnUiThreadOptional(() -> {
        if (!acceptsResult(generation)) {
          return;
        }
        loading = false;
        loadError = error;
        if (error == null) {
          chats = loaded;
        } else {
          UI.showError(error);
        }
        buildCells();
      });
    });
    tdlib.ui().postDelayed(() -> runOnUiThreadOptional(() -> {
      if (acceptsResult(generation)) {
        loading = false;
        loadError = new TdApi.Error(408, "Chat list request timed out");
        buildCells();
      }
    }), 15000);
  }

  private boolean acceptsResult (int generation) {
    return !isDestroyed() && loading && generation == requestGeneration;
  }

  private void buildCells () {
    ArrayList<ListItem> items = new ArrayList<>();
    items.add(new ListItem(loading ? ListItem.TYPE_DESCRIPTION : ListItem.TYPE_SETTING,
      R.id.btn_reloadDiscovery, 0, loading ? R.string.LoadingInformation :
        loadError != null ? R.string.RetryChatList : R.string.RefreshChatList));
    if (mode == RECENT) {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.RecentlyOpenedChatsInfo));
    } else if (mode == INACTIVE) {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.InactiveGroupChatsInfo));
    }
    if (chats != null && chats.length > 0) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      HashSet<Long> seen = new HashSet<>();
      for (TdApi.Chat chat : chats) {
        if (chat != null && seen.add(chat.id)) {
          items.add(new ListItem(ListItem.TYPE_CHAT_SMALL).setLongId(chat.id)
            .setData(new DoubleTextWrapper(tdlib, chat)));
        }
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    } else if (!loading && loadError == null) {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.NoDiscoveryChats));
    }
    adapter.setItems(items, false);
  }

  @Override
  public void onClick (View view) {
    ListItem item = (ListItem) view.getTag();
    if (item == null) {
      return;
    }
    if (item.getId() == R.id.btn_reloadDiscovery) {
      loadChats();
    } else if (item.getViewType() == ListItem.TYPE_CHAT_SMALL) {
      tdlib.ui().openChat(this, item.getLongId(), new TdlibUi.ChatOpenParameters().keepStack());
    }
  }
}
