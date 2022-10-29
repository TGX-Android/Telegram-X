package org.thunderdog.challegram.component.chat;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.Identity;
import org.thunderdog.challegram.telegram.Tdlib;

public class IdentityHeaderView extends IdentityItemView {
  public IdentityHeaderView (@NonNull Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  public void setIdentity (Identity identity) {
    super.setIdentity(identity);
    this.title = Lang.getString(R.string.SendAs);
    this.subtitle = identity.getName();
  }
}
