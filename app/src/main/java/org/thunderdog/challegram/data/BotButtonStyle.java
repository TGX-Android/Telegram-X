/* This file is a part of Telegram X. Licensed under the GNU GPL, version 3 or later. */
package org.thunderdog.challegram.data;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.theme.ColorId;

/** Semantic bot colors are independent of a theme's general accent color. */
public final class BotButtonStyle {
  private BotButtonStyle () { }

  public static @ColorId int backgroundColorId (@Nullable TdApi.ButtonStyle style) {
    if (style != null) {
      switch (style.getConstructor()) {
        case TdApi.ButtonStylePrimary.CONSTRUCTOR: return ColorId.botButtonPrimary;
        case TdApi.ButtonStyleDanger.CONSTRUCTOR: return ColorId.botButtonDanger;
        case TdApi.ButtonStyleSuccess.CONSTRUCTOR: return ColorId.botButtonSuccess;
      }
    }
    // Default, null and styles intended for other surfaces keep the existing UI.
    return ColorId.NONE;
  }
}
