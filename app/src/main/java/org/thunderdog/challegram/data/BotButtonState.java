/* This file is a part of Telegram X. Licensed under the GNU GPL, version 3 or later. */
package org.thunderdog.challegram.data;

import org.drinkless.tdlib.TdApi;

import tgx.td.Td;

/** Separates edits to presentation from edits that invalidate an in-flight action. */
public final class BotButtonState {
  public TdApi.InlineKeyboardButtonType type;
  public long iconCustomEmojiId;
  public int backgroundColorId;
  private String text;

  public boolean update (TdApi.InlineKeyboardButton button, String displayText) {
    boolean actionChanged = !displayText.equals(text) || !Td.equalsTo(type, button.type);
    text = displayText;
    type = button.type;
    iconCustomEmojiId = button.iconCustomEmojiId;
    backgroundColorId = BotButtonStyle.backgroundColorId(button.style);
    return actionChanged;
  }
}
