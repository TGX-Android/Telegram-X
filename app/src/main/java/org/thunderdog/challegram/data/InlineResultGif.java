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
 * File created on 03/12/2016
 */
package org.thunderdog.challegram.data;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.telegram.Tdlib;

public class InlineResultGif extends InlineResult<TdApi.InlineQueryResultAnimation> {
  private final TGGif gif;

  public InlineResultGif (BaseActivity context, Tdlib tdlib, TdApi.InlineQueryResultAnimation data) {
    super(context, tdlib, TYPE_GIF, data.id, data);
    this.gif = new TGGif(tdlib, data.animation);
  }

  public TGGif getGif () {
    return gif;
  }

  @Override
  public int getCellWidth () {
    return gif.width();
  }

  @Override
  public int getCellHeight () {
    return gif.height();
  }
}
