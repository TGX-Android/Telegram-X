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
 * File created on 29/09/2023
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;

import androidx.annotation.IdRes;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.NotificationSettingsListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

public class SettingsArchiveChatListController extends RecyclerViewController<Void> implements View.OnClickListener, NotificationSettingsListener {
  public SettingsArchiveChatListController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_archiveSettings;
  }

  @Override
  public boolean needAsynchronousAnimation () {
    return true;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.ArchiveSettings);
  }

  private TdApi.ArchiveChatListSettings archiveChatListSettings;
  private TdApi.Error error;

  private SettingsAdapter adapter;

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        @IdRes int id = item.getId();
        if (id == R.id.btn_keepUnmutedChatsArchived) {
          view.getToggler().setRadioEnabled(archiveChatListSettings != null && archiveChatListSettings.keepUnmutedChatsArchived, isUpdate);
        } else if (id == R.id.btn_keepFolderChatsArchived) {
          view.getToggler().setRadioEnabled(archiveChatListSettings != null && archiveChatListSettings.keepChatsFromFoldersArchived, isUpdate);
        } else if (id == R.id.btn_archiveMuteNonContacts) {
          view.getToggler().setRadioEnabled(archiveChatListSettings != null && archiveChatListSettings.archiveAndMuteNewChatsFromUnknownUsers, isUpdate);
        }
      }
    };
    tdlib.send(new TdApi.GetArchiveChatListSettings(), (settings, error) -> runOnUiThreadOptional(() -> {
      this.archiveChatListSettings = settings;
      this.error = error;
      buildCells();
      executeScheduledAnimation();
    }));
    recyclerView.setAdapter(adapter);
    tdlib.listeners().subscribeToSettingsUpdates(this);
  }

  private void buildCells () {
    if (error != null) {
      adapter.setItems(new ListItem[] {
        new ListItem(ListItem.TYPE_EMPTY, 0, 0, TD.toErrorString(error), false)
      }, false);
    } else {
      List<ListItem> items = new ArrayList<>();

      items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.ArchiveSettingUnmutedChatsTitle));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_keepUnmutedChatsArchived, 0, R.string.ArchiveSettingUnmutedChats));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.ArchiveSettingUnmutedChatsDesc));

      if (tdlib.hasFolders() || archiveChatListSettings.keepChatsFromFoldersArchived) {
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.ArchiveSettingFolderChatsTitle));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_keepFolderChatsArchived, 0, R.string.ArchiveSettingFolderChats));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.ArchiveSettingFolderChatsDesc));
      }

      if (tdlib.autoArchiveAvailable() || archiveChatListSettings.archiveAndMuteNewChatsFromUnknownUsers) {
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.UnknownChats));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_archiveMuteNonContacts, 0, R.string.ArchiveNonContacts));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.ArchiveNonContactsInfo));
      }

      adapter.setItems(items, false);
    }
  }

  @Override
  public void onArchiveChatListSettingsChanged (TdApi.ArchiveChatListSettings settings) {
    runOnUiThreadOptional(() -> {
      if (archiveChatListSettings != null) {
        archiveChatListSettings = settings;
        adapter.updateValuedSettingById(R.id.btn_keepFolderChatsArchived);
        adapter.updateValuedSettingById(R.id.btn_keepUnmutedChatsArchived);
        adapter.updateValuedSettingById(R.id.btn_archiveMuteNonContacts);
      }
    });
  }

  @Override
  public void onClick (View v) {
    @IdRes int id = v.getId();
    if (archiveChatListSettings == null) {
      return;
    }
    if (id == R.id.btn_keepUnmutedChatsArchived ||
      id == R.id.btn_keepFolderChatsArchived ||
      id == R.id.btn_archiveMuteNonContacts) {
      boolean value = adapter.toggleView(v);
      if (id == R.id.btn_keepUnmutedChatsArchived) {
        archiveChatListSettings.keepUnmutedChatsArchived = value;
      } else if (id == R.id.btn_keepFolderChatsArchived) {
        archiveChatListSettings.keepChatsFromFoldersArchived = value;
      } else if (id == R.id.btn_archiveMuteNonContacts) {
        archiveChatListSettings.archiveAndMuteNewChatsFromUnknownUsers = value;
      }
      tdlib.send(new TdApi.SetArchiveChatListSettings(archiveChatListSettings), (ok, error) -> {
        if (ok != null) {
          tdlib.listeners().notifyArchiveChatListSettingsChanged(archiveChatListSettings);
        }
      });
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    tdlib.listeners().unsubscribeFromSettingsUpdates(this);
  }
}
