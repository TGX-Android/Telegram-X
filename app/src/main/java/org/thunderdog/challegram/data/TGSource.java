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
 * File created on 20/02/2016 at 18:09
 */
package org.thunderdog.challegram.data;

import android.view.View;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextPart;

public abstract class TGSource {
  protected TGMessage msg;
  protected boolean isReady;

  public TGSource (TGMessage msg) {
    this.msg = msg;
  }

  public abstract boolean open (View view, Text text, TextPart part,  @Nullable TdlibUi.UrlOpenParameters openParameters, Receiver receiver);
  public abstract void load ();
  public abstract String getAuthorName ();
  public abstract ImageFile getAvatar ();
  public abstract AvatarPlaceholder.Metadata getAvatarPlaceholderMetadata ();
  public abstract void destroy ();

  public boolean isReady () {
    return isReady;
  }
}
