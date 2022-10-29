package org.thunderdog.challegram.util;

import android.content.Context;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.Identity;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.MessagesController;

public class RevealIdentityConfirmationHelper {
  public static void showIfNecessary (Tdlib tdlib, MessagesController messagesController, Context context, Runnable onConfirm) {
    Identity selectedIdentity = messagesController.getSelectedIdentity();
    if (selectedIdentity == null || selectedIdentity.getType() == Identity.Type.USER) {
      onConfirm.run();
    } else {
      messagesController.hideAllKeyboards();
      show(tdlib, context, onConfirm);
    }
  }

  public static DoubleTapConfirmationView show (Tdlib tdlib, Context context, Runnable onConfirm) {
    DoubleTapConfirmationView confirmationView = new DoubleTapConfirmationView(context);
    confirmationView.init(tdlib, Lang.getString(R.string.TapConfirmationTip), Screen.dp(180f));
    confirmationView.setOnConfirm(onConfirm);
    confirmationView.show();
    return confirmationView;
  }
}
