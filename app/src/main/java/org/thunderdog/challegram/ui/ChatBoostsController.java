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
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.StringUtils;

public class ChatBoostsController extends RecyclerViewController<Void> implements View.OnClickListener,
  TdlibCache.SupergroupDataChangeListener {
  private final long chatId, supergroupId;
  private final Map<String, TdApi.ChatBoost> boosts = new LinkedHashMap<>();
  private final HashSet<String> loadedOffsets = new HashSet<>();
  private SettingsAdapter adapter;
  private int total = -1;
  private String nextOffset = "";
  private boolean loading, error;

  public ChatBoostsController (Context context, Tdlib tdlib, long chatId) {
    super(context, tdlib);
    this.chatId = chatId;
    TdApi.Chat chat = tdlib.chat(chatId);
    this.supergroupId = chat != null && chat.type instanceof TdApi.ChatTypeSupergroup
      ? ((TdApi.ChatTypeSupergroup) chat.type).supergroupId : 0;
  }

  public static boolean canViewBoosts (Tdlib tdlib, long chatId) {
    TdApi.Chat chat = tdlib.chat(chatId);
    if (chat == null || !(chat.type instanceof TdApi.ChatTypeSupergroup)) {
      return false;
    }
    TdApi.Supergroup group = tdlib.cache().supergroup(((TdApi.ChatTypeSupergroup) chat.type).supergroupId);
    return group != null && !group.isDirectMessagesGroup && TD.isAdmin(group.status);
  }

  @Override
  public int getId () {
    return R.id.controller_chatBoosts;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.ViewChatBoosts);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (item.getData() instanceof TdApi.ChatBoost) {
          TdApi.ChatBoost boost = (TdApi.ChatBoost) item.getData();
          view.setData(Lang.getString(R.string.ChatBoostSourceDetails, Lang.getString(sourceLabel(boost.source)),
            boost.count, Lang.getUntilDate(boost.expirationDate, TimeUnit.SECONDS)));
        }
      }
    };
    recyclerView.setAdapter(adapter);
    if (supergroupId != 0) {
      tdlib.cache().subscribeToSupergroupUpdates(supergroupId, this);
    }
    loadMore();
  }

  @Override
  public void onFocus () {
    super.onFocus();
    if (adapter != null) {
      render();
    }
  }

  @Override
  public void onSupergroupUpdated (TdApi.Supergroup supergroup) {
    runOnUiThreadOptional(() -> {
      if (adapter != null) {
        render();
      }
    });
  }

  @Override
  public void destroy () {
    if (supergroupId != 0) {
      tdlib.cache().unsubscribeFromSupergroupUpdates(supergroupId, this);
    }
    super.destroy();
  }

  private void loadMore () {
    if (loading || nextOffset == null || !canViewBoosts(tdlib, chatId)) {
      render();
      return;
    }
    final String offset = nextOffset;
    loading = true;
    error = false;
    render();
    tdlib.send(new TdApi.GetChatBoosts(chatId, false, offset, 30), (page, requestError) ->
      runOnUiThreadOptional(() -> {
        loading = false;
        if (canViewBoosts(tdlib, chatId)) {
          if (requestError != null) {
            error = true;
            UI.showError(requestError);
          } else {
            total = page.totalCount;
            for (TdApi.ChatBoost boost : page.boosts) {
              boosts.put(boost.id, boost);
            }
            loadedOffsets.add(offset);
            nextOffset = StringUtils.isEmpty(page.nextOffset) || loadedOffsets.contains(page.nextOffset)
              ? null : page.nextOffset;
          }
        }
        render();
      }));
  }

  private void render () {
    ArrayList<ListItem> items = new ArrayList<>();
    if (!canViewBoosts(tdlib, chatId)) {
      boosts.clear();
      loadedOffsets.clear();
      total = -1;
      nextOffset = "";
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.ChatBoostsUnavailable));
    } else {
      if (total >= 0) {
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getString(R.string.ChatBoostsTotal, total)));
      }
      for (TdApi.ChatBoost boost : boosts.values()) {
        long userId = sourceUserId(boost.source);
        CharSequence name = userId != 0 ? tdlib.cache().userName(userId) : Lang.getString(sourceLabel(boost.source));
        items.add(new ListItem(ListItem.TYPE_INFO_SETTING, R.id.user, 0, name).setData(boost));
      }
      if (loading) {
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.LoadingInformation));
      } else if (nextOffset != null) {
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_loadChatBoosts, 0,
          error ? R.string.RetryChatList : R.string.LoadMoreChatBoosts));
      } else if (boosts.isEmpty()) {
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.NoChatBoosts));
      }
    }
    adapter.setItems(items, false);
  }

  private static int sourceLabel (TdApi.ChatBoostSource source) {
    if (source instanceof TdApi.ChatBoostSourcePremium) {
      return R.string.ChatBoostPremiumSource;
    } else if (source instanceof TdApi.ChatBoostSourceGiftCode) {
      return R.string.ChatBoostGiftCodeSource;
    } else if (source instanceof TdApi.ChatBoostSourceGiveaway) {
      return R.string.ChatBoostGiveawaySource;
    }
    return R.string.ChatBoostUnknownSource;
  }

  private static long sourceUserId (TdApi.ChatBoostSource source) {
    if (source instanceof TdApi.ChatBoostSourcePremium) {
      return ((TdApi.ChatBoostSourcePremium) source).userId;
    } else if (source instanceof TdApi.ChatBoostSourceGiftCode) {
      return ((TdApi.ChatBoostSourceGiftCode) source).userId;
    } else if (source instanceof TdApi.ChatBoostSourceGiveaway) {
      return ((TdApi.ChatBoostSourceGiveaway) source).userId;
    }
    return 0;
  }

  @Override
  public void onClick (View view) {
    if (view.getId() == R.id.btn_loadChatBoosts) {
      loadMore();
    } else if (view.getTag() instanceof ListItem && canViewBoosts(tdlib, chatId)) {
      Object data = ((ListItem) view.getTag()).getData();
      if (data instanceof TdApi.ChatBoost) {
        long userId = sourceUserId(((TdApi.ChatBoost) data).source);
        if (userId != 0) {
          tdlib.ui().openPrivateProfile(this, userId, new TdlibUi.UrlOpenParameters());
        }
      }
    }
  }
}
