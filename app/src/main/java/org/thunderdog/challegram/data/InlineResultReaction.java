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
 * File created on 03/12/2016
 */
package org.thunderdog.challegram.data;

import androidx.annotation.NonNull;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.telegram.Tdlib;

public class InlineResultReaction extends InlineResult<TdApi.Sticker> {
  private final TGStickerObj sticker;

  public InlineResultReaction (BaseActivity context, Tdlib tdlib, TdApi.Sticker data) {
    super(context, tdlib, TYPE_REACTION, data.emoji,  data);
    this.sticker = new TGStickerObj(tdlib, data, data.emoji, data.type);
  }

  public @NonNull TGStickerObj getSticker () {
    return sticker;
  }

  @Override
  public int getCellWidth () {
    return sticker.getWidth();
  }

  @Override
  public int getCellHeight () {
    return sticker.getHeight();
  }
}
