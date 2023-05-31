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
 * File created on 20/02/2016 at 18:26
 */
package org.thunderdog.challegram.data;

import android.view.View;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextPart;

public class TGSourceUser extends TGSource implements TdlibCache.UserDataChangeListener {
  private final long senderUserId;
  private TdApi.User user;

  public TGSourceUser (TGMessage msg, TdApi.MessageForwardOriginUser info) {
    super(msg);
    this.senderUserId = info.senderUserId;
  }

  public TdApi.User getUser () {
    return user;
  }

  @Override
  public void load () {
    TdApi.User user = msg.tdlib().cache().user(senderUserId);
    msg.tdlib().cache().addUserDataListener(senderUserId, this);
    if (user != null) {
      this.user = user;
      this.isReady = true;
      msg.rebuildForward();
    }
  }

  @Override
  public boolean open (View view, Text text, TextPart part, TdlibUi.UrlOpenParameters openParameters, Receiver receiver) {
    if (user != null) {
      msg.tdlib().ui().openPrivateProfile(msg.controller(), user.id, openParameters);
      return true;
    }
    return false;
  }

  @Override
  public void destroy () {
    msg.tdlib().cache().removeUserDataListener(senderUserId, this);
  }

  @Override
  public void onUserUpdated (TdApi.User user) {
    this.user = user;
    msg.tdlib().ui().post(() -> {
      if (!msg.isDestroyed()) {
        msg.rebuildForward();
        msg.postInvalidate();
      }
    });
  }

  public long getSenderUserId () {
    return senderUserId;
  }

  @Override
  public String getAuthorName () {
    return user == null ? Lang.getString(R.string.LoadingUser) : TD.getUserName(user);
  }

  @Override
  public int getAuthorNameColorId () {
    return TD.getNameColorId(msg.tdlib.cache().userAvatarColorId(senderUserId));
  }

  @Override
  public void requestAvatar (AvatarReceiver receiver) {
    receiver.requestUser(msg.tdlib, senderUserId, AvatarReceiver.Options.NONE);
  }
}
