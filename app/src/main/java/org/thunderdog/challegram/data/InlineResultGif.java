package org.thunderdog.challegram.data;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.telegram.Tdlib;

/**
 * Date: 03/12/2016
 * Author: default
 */

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
