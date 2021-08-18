/**
 * File created on 20/02/16 at 18:09
 * Copyright Vyacheslav Krylov, 2014
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
