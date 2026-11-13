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
import android.widget.Toast;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;

/** Select a profile channel from TDLib's eligible list, not from arbitrary chat IDs. */
public class ProfileChannelController extends ChatDiscoveryController {
  public ProfileChannelController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_profileChannel;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.ChooseProfileChannel);
  }

  @Override
  protected TdApi.Function<TdApi.Chats> discoveryRequest () {
    return new TdApi.GetSuitablePersonalChats();
  }

  private boolean changingChannel;
  private long selectedChannelId;

  @Override
  public void onClick (View view) {
    ListItem item = (ListItem) view.getTag();
    if (item != null && item.getViewType() == ListItem.TYPE_CHAT_SMALL) {
      if (changingChannel) {
        return;
      }
      final long chatId = selectedChannelId = item.getLongId();
      showConfirm(Lang.getStringBold(R.string.SetProfileChannelConfirm, tdlib.chatTitle(chatId)),
        Lang.getString(R.string.Save), R.drawable.baseline_bullhorn_24, OptionColor.NORMAL,
        () -> setProfileChannel(chatId));
    } else {
      super.onClick(view);
    }
  }

  private void setProfileChannel (long chatId) {
    if (changingChannel || chatId == 0 || chatId != selectedChannelId) {
      return;
    }
    changingChannel = true;
    tdlib.send(new TdApi.GetSuitablePersonalChats(), (suitable, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        changingChannel = false;
        UI.showError(error);
        return;
      }
      boolean eligible = false;
      for (long id : suitable.chatIds) {
        if (id == chatId) {
          eligible = true;
          break;
        }
      }
      if (!eligible) {
        changingChannel = false;
        UI.showToast(R.string.ProfileChannelUnavailable, Toast.LENGTH_SHORT);
        return;
      }
      tdlib.send(new TdApi.SetPersonalChat(chatId), (ok, setError) -> runOnUiThreadOptional(() -> {
        changingChannel = false;
        if (setError != null) {
          UI.showError(setError);
        } else {
          navigateBack();
        }
      }));
    }));
  }
}
