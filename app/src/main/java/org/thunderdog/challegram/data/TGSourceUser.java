/**
 * File created on 20/02/16 at 18:26
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.data;

import android.view.View;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ImageFile;
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
  public ImageFile getAvatar () {
    return user == null || TD.isPhotoEmpty(user.profilePhoto) ? null : new ImageFile(msg.tdlib(), user.profilePhoto.small);
  }

  @Override
  public AvatarPlaceholder.Metadata getAvatarPlaceholderMetadata () {
    return msg.tdlib.cache().userPlaceholderMetadata(senderUserId, msg.tdlib.cache().user(senderUserId), false);
  }
}
