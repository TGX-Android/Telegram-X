package org.thunderdog.challegram.data;

import androidx.annotation.NonNull;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.telegram.Tdlib;

/**
 * Date: 03/12/2016
 * Author: default
 */

public class InlineResultSticker extends InlineResult<TdApi.InlineQueryResultSticker> {
  private final TGStickerObj sticker;

  public InlineResultSticker (BaseActivity context, Tdlib tdlib, String emoji, TdApi.InlineQueryResultSticker data) {
    super(context, tdlib, TYPE_STICKER, data.id, data);
    this.sticker = new TGStickerObj(tdlib, data.sticker, emoji, data.sticker.type);
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
