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

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;

public class SettingsMessagesFilterController extends RecyclerViewController<Void> implements View.OnClickListener, ChatListener {

  private SettingsAdapter adapter;

  public SettingsMessagesFilterController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      public void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        final int itemId = item.getId();
        if (itemId == R.id.btn_messageFilterFilteredChannelsManage) {
          view.setData(Lang.pluralBold(R.string.xChannels, 42));
        } else if (itemId == R.id.btn_blockedSenders) {
          view.setData(getBlockedSendersCount());
        } else if (itemId == R.id.btn_messageFilterEnabled) {
          view.getToggler().setRadioEnabled(Settings.instance().getMessagesFilterSetting(Settings.MESSAGES_FILTER_ENABLED), isUpdate);
        } else if (itemId == R.id.btn_messageFilterHideBlockedSenders) {
          view.getToggler().setRadioEnabled(Settings.instance().getMessagesFilterSetting(Settings.MESSAGES_FILTER_HIDE_BLOCKED_SENDERS), isUpdate);
        } else if (itemId == R.id.btn_messageFilterHideBlockedSendersMentions) {
          view.getToggler().setRadioEnabled(Settings.instance().getMessagesFilterSetting(Settings.MESSAGES_FILTER_HIDE_BLOCKED_SENDERS_MENTIONS), isUpdate);
        }
      }
    };

    buildCells();
    recyclerView.setAdapter(adapter);

    tdlib.send(new TdApi.GetBlockedMessageSenders(new TdApi.BlockListMain(), 0, 1), this::onBlockedSendersResult);
    tdlib.listeners().subscribeForAnyUpdates(this);
  }

  private int defaultCellsCount = 0;
  private boolean settingCellsVisible;
  private int settingCellsCount = 0;

  private void buildCells () {
    ArrayList<ListItem> items = new ArrayList<>();

    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_messageFilterEnabled, 0, R.string.MessagesFilter));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.MessagesFilterDesc));

    defaultCellsCount = items.size();

    settingCellsVisible = Settings.instance().getMessagesFilterSetting(Settings.MESSAGES_FILTER_ENABLED);
    if (settingCellsVisible) {
      buildSettingsCells(items);
    }

    adapter.setItems(items, true);
  }

  private void buildSettingsCells (ArrayList<ListItem> items) {
    final int size = items.size();

    /*
    items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.MessagesFilterChannels));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_messageFilterFilteredChannelsManage, 0, R.string.MessagesFilterChannelsManage));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.MessagesFilterGroupsAndChats));
    */

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_messageFilterHideBlockedSenders, 0, R.string.MessagesFilterHideBlockedSenders));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.MessagesFilterHideBlockedSendersDesc));

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_messageFilterHideBlockedSendersMentions, 0, R.string.MessagesFilterHideBlockedSendersMentions));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.MessagesFilterHideBlockedSendersMentionsDesc));

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_blockedSenders, 0, R.string.MessagesFilterBlockedSendersManage));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    settingCellsCount = items.size() - size;
  }

  private void setSettingsCellsVisible (boolean visible) {
    if (visible == settingCellsVisible) {
      return;
    }
    settingCellsVisible = visible;
    if (visible) {
      ArrayList<ListItem> items = new ArrayList<>();
      buildSettingsCells(items);
      adapter.addItems(defaultCellsCount, items.toArray(new ListItem[0]));
    } else {
      adapter.removeRange(defaultCellsCount, settingCellsCount);
    }
  }

  @Override
  public void onClick (View v) {
    final int id = v.getId();
    if (id == R.id.btn_blockedSenders) {
      SettingsBlockedController c = new SettingsBlockedController(context, tdlib);
      c.setArguments(new TdApi.BlockListMain());
      navigateTo(c);
    } else if (id == R.id.btn_messageFilterEnabled) {
      boolean newValue = adapter.toggleView(v);
      Settings.instance().setMessagesFilterSetting(Settings.MESSAGES_FILTER_ENABLED, newValue);
      setSettingsCellsVisible(newValue);
    } else if (id == R.id.btn_messageFilterHideBlockedSenders) {
      Settings.instance().setMessagesFilterSetting(Settings.MESSAGES_FILTER_HIDE_BLOCKED_SENDERS, adapter.toggleView(v));
    } else if (id == R.id.btn_messageFilterHideBlockedSendersMentions) {
      Settings.instance().setMessagesFilterSetting(Settings.MESSAGES_FILTER_HIDE_BLOCKED_SENDERS_MENTIONS, adapter.toggleView(v));
    }
  }


  /* Blocked users */

  private int blockedSendersCount = -1;

  private CharSequence getBlockedSendersCount () {
    return blockedSendersCount == -1 ? Lang.getString(R.string.LoadingInformation) : blockedSendersCount > 0 ? Lang.pluralBold(R.string.xSenders, blockedSendersCount) : Lang.getString(R.string.BlockedNone);
  }

  private void onBlockedSendersResult (TdApi.MessageSenders senders, TdApi.Error error) {
    UI.post(() -> {
      if (isDestroyed()) {
        return;
      }

      final int totalCount = senders.totalCount;
      if (this.blockedSendersCount != totalCount) {
        this.blockedSendersCount = totalCount;
        adapter.updateValuedSettingById(R.id.btn_blockedSenders);
      }
    });
  }


  /* * */

  @Override
  public int getId () {
    return R.id.controller_messagesFilterSettings;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.MessagesFilter);
  }

  @Override
  public void destroy () {
    super.destroy();
    tdlib.listeners().unsubscribeFromAnyUpdates(this);
  }


  /* Tdlib listeners */

  @Override
  public void onChatBlockListChanged (long chatId, @Nullable TdApi.BlockList blockList) {
    runOnUiThread(() -> {
      if (!isDestroyed()) {
        tdlib.send(new TdApi.GetBlockedMessageSenders(new TdApi.BlockListMain(), 0, 1), this::onBlockedSendersResult);
      }
    }, 350L);
  }
}
